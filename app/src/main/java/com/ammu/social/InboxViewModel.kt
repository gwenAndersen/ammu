package com.ammu.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammu.social.models.ClassifiedComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException // Added import
import org.json.JSONObject

class InboxViewModel : ViewModel() {

    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY

    // --- API URLs ---
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    

    private val _allAnalyzedComments = MutableStateFlow<List<ClassifiedComment>>(emptyList())
    private val _allRawComments = MutableStateFlow<List<ClassifiedComment>>(emptyList())
    private val _detailedErrorLogs = MutableStateFlow<Map<String, String>>(emptyMap())
    val detailedErrorLogs: StateFlow<Map<String, String>> = _detailedErrorLogs.asStateFlow()
    private val currentPage = MutableStateFlow(0)
    private val pageSize = 20

    val paginatedComments: StateFlow<List<ClassifiedComment>> = combine(
        _allRawComments,
        _allAnalyzedComments,
        currentPage.asStateFlow()
    ) { allRawComments, allAnalyzedComments, page ->
        val startIndex = page * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(allRawComments.size)
        if (startIndex < allRawComments.size) {
            allRawComments.subList(startIndex, endIndex).map { rawComment ->
                allAnalyzedComments.find { it.id == rawComment.id } ?: rawComment.copy(priority = "Pending", reason = "Analyzing...")
            }
        } else {
            emptyList()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun fetchAndAnalyzeComments(pageId: String, accessToken: String) {
        Log.d("InboxViewModel", "fetchAndAnalyzeComments called for pageId: $pageId")
        viewModelScope.launch {
            try {
                val fbCommentsUrl = "https://graph.facebook.com/v18.0/$pageId/posts?fields=comments{message,from},message&access_token=$accessToken"
                Log.d("InboxViewModel", "Facebook comments URL: $fbCommentsUrl")

                val fetchApiStartTime = System.currentTimeMillis()
                val facebookResponse = fetchFromApi(fbCommentsUrl)
                val fetchApiEndTime = System.currentTimeMillis()
                val fetchApiDuration = fetchApiEndTime - fetchApiStartTime
                Log.d("InboxViewModel", "fetchFromApi for Facebook comments took ${fetchApiDuration}ms")

                Log.d("InboxViewModel", "Facebook API raw response: ${facebookResponse?.take(500)}") // Log first 500 chars
                if (facebookResponse != null) {
                    val rawComments = parseFacebookComments(facebookResponse)
                    Log.d("InboxViewModel", "Parsed raw comments count: ${rawComments.size}")
                    rawComments.forEachIndexed { index, comment ->
                        Log.d("InboxViewModel", "Raw Comment ${index + 1}: ID=${comment.id}, Text='${comment.text.take(50)}...'")
                    }
                    if (rawComments.isNotEmpty()) {
                        _allRawComments.value = rawComments
                        Log.d("InboxViewModel", "Populated _allRawComments with ${rawComments.size} comments.")
                        analyzeCurrentPageComments() // Analyze the first page
                    } else {
                        _allRawComments.value = emptyList()
                        Log.d("InboxViewModel", "No raw comments found from Facebook.")
                    }
                } else {
                    _allAnalyzedComments.value = emptyList()
                    Log.e("InboxViewModel", "Facebook API call returned null response.")
                }
            } catch (e: Exception) {
                Log.e("InboxViewModel", "Error in fetchAndAnalyzeComments: ${e.message}", e)
                _allAnalyzedComments.value = emptyList()
            }
        }
    }

    private fun analyzeCurrentPageComments() {
        viewModelScope.launch {
            val currentRawComments = _allRawComments.value.chunked(pageSize)[currentPage.value]
            if (currentRawComments.isNotEmpty()) {
                Log.d("InboxViewModel", "Analyzing comments for page ${currentPage.value + 1}...")
                val analyzedComments = analyzeCommentsWithGeminiAPI(currentRawComments)

                // Merge newly analyzed comments into _allAnalyzedComments
                _allAnalyzedComments.value = _allAnalyzedComments.value.toMutableList().apply {
                    analyzedComments.forEach { newComment ->
                        val index = indexOfFirst { it.id == newComment.id }
                        if (index != -1) {
                            set(index, newComment)
                        } else {
                            add(newComment)
                        }
                    }
                }
                Log.d("InboxViewModel", "Finished analyzing comments for page ${currentPage.value + 1}.")
            } else {
                Log.d("InboxViewModel", "No raw comments to analyze for page ${currentPage.value + 1}.")
            }
        }
    }

    private suspend fun fetchFromApi(url: String): String? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                Log.d("InboxViewModel", "fetchFromApi call to $url took ${duration}ms")
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("InboxViewModel", "API call to $url failed: ${response.code} - ${response.message} - $errorBody")
                    return@withContext null
                }
                val responseBody = response.body?.string()
                Log.d("InboxViewModel", "fetchFromApi successful for $url, response length: ${responseBody?.length ?: 0}")
                responseBody
            }
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.e("InboxViewModel", "Exception during API call to $url: ${e.message}, took ${duration}ms", e)
            null
        }
    }

    private fun parseFacebookComments(jsonString: String): List<ClassifiedComment> {
        val parsedComments = mutableListOf<ClassifiedComment>()
        try {
            val jsonObject = JSONObject(jsonString)
            val posts = jsonObject.optJSONArray("data") ?: JSONArray()
            Log.d("InboxViewModel", "parseFacebookComments: Found ${posts.length()} posts.")
            for (i in 0 until posts.length()) {
                val post = posts.getJSONObject(i)
                val postCommentsObject = post.optJSONObject("comments")
                val postCommentsArray = postCommentsObject?.optJSONArray("data") ?: JSONArray()
                Log.d("InboxViewModel", "parseFacebookComments: Post ${i} has ${postCommentsArray.length()} comments.")

                for (j in 0 until postCommentsArray.length()) {
                    val comment = postCommentsArray.getJSONObject(j)
                    val fromName = comment.optJSONObject("from")?.optString("name") ?: "Unknown User"
                    val commentId = comment.optString("id")
                    val commentMessage = comment.optString("message")
                    Log.d("InboxViewModel", "Parsed comment: id=$commentId, from=$fromName, message=${commentMessage.take(100)}")
                    parsedComments.add(
                        ClassifiedComment(
                            id = commentId,
                            text = commentMessage,
                            from = fromName
                        )
                    )
                }
            }
        } catch (e: JSONException) {
            Log.e("InboxViewModel", "JSONException in parseFacebookComments: ${e.message}", e)
        }
        return parsedComments
    }

    private suspend fun analyzeCommentsWithGeminiAPI(rawComments: List<ClassifiedComment>): List<ClassifiedComment> = withContext(Dispatchers.IO) {
        Log.d("InboxViewModel", "analyzeCommentsWithGeminiAPI called with ${rawComments.size} comments.")
        rawComments.forEachIndexed { index, comment ->
            Log.d("InboxViewModel", "AI Input Comment ${index + 1}: ID=${comment.id}, Text='${comment.text.take(50)}...'")
        }
        val analyzedComments = mutableListOf<ClassifiedComment>()
        val BATCH_SIZE = 10 // Define batch size

        if (rawComments.isEmpty()) {
            return@withContext emptyList()
        }

        rawComments.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
            Log.d("InboxViewModel", "Processing batch ${index + 1}/${rawComments.chunked(BATCH_SIZE).size} with ${batch.size} comments.")
            // Construct a single prompt for the current batch of comments
            val commentsForPrompt = batch.joinToString(separator = "---") { 
                "Comment ID: ${it.id}\nComment Text: ${it.text}"
            }
            // Store the AI input prompt for each comment in the batch for debugging
            batch.forEach { comment ->
                _detailedErrorLogs.value = _detailedErrorLogs.value + (comment.id to "AI Input Prompt:\n${commentsForPrompt}")
            }

            val prompt = """
                You are a professional social media manager for a busy brand. Your task is to analyze the following Facebook comments and classify each one based on urgency and content.

                For each comment provided below, return a JSON object with four fields:
                1.  "id": The original Comment ID.
                2.  "priority": "High", "Medium", or "Low" based on the urgency of the comment.
                3.  "reason": A brief, one-sentence explanation for your classification.
                4.  "reply": A short, friendly, and appropriate reply to the comment. For generic comments, you can use emojis.

                Return a JSON array containing one such object for each comment.

                --- Comments to Analyze ---
                $commentsForPrompt
                --- End of Comments ---
                """.trimIndent()

            val geminiRequestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url(GEMINI_API_URL) // Use the direct Gemini API URL
                .post(geminiRequestBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    Log.d("InboxViewModel", "Gemini API response for batch ${index + 1}: ${response.code}, successful: ${response.isSuccessful}")
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        val detailedLog = "Gemini API call failed for batch ${index + 1}: ${response.code} - ${response.message} - $errorBody"
                        Log.e("InboxViewModel", detailedLog)
                        batch.forEach { _detailedErrorLogs.value = _detailedErrorLogs.value + (it.id to detailedLog) }
                        analyzedComments.addAll(batch.map { it.copy(priority = "Error", reason = "Gemini API error: ${response.code}. See detailed logs.") })
                        return@use // Continue to next batch
                    }

                    val responseBody = response.body?.string()
                    Log.d("InboxViewModel", "Gemini API response body length for batch ${index + 1}: ${responseBody?.length ?: 0}")
                    val geminiData = JSONObject(responseBody)
                    val textContent = geminiData.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text") as String? ?: ""

                    val cleanedJson = textContent.trim().removePrefix("```json\n").removeSuffix("\n```")
                    Log.d("InboxViewModel", "Cleaned JSON content length for batch ${index + 1}: ${cleanedJson.length}")

                    try {
                        val classifiedCommentsArray = JSONArray(cleanedJson)
                        Log.d("InboxViewModel", "Received ${classifiedCommentsArray.length()} classified comments from Gemini for batch ${index + 1}.")
                        val classifiedMap = mutableMapOf<String, ClassifiedComment>()

                        for (i in 0 until classifiedCommentsArray.length()) {
                            val classifiedCommentJson = classifiedCommentsArray.getJSONObject(i)
                            val id: String = classifiedCommentJson.optString("id", "")
                            val priority: String = classifiedCommentJson.optString("priority", "Low")
                            val reason: String = classifiedCommentJson.optString("reason", "N/A")
                            val reply: String = classifiedCommentJson.optString("reply", "")

                            val originalComment = batch.find { it.id == id }
                            if (originalComment != null) {
                                classifiedMap[id] = originalComment.copy(priority = priority, reason = reason, reply = reply)
                            } else {
                                Log.w("InboxViewModel", "Gemini returned classification for unknown comment ID: $id in batch ${index + 1}")
                            }
                        }
                        analyzedComments.addAll(batch.map {
                            classifiedMap[it.id] ?: it.copy(priority = "Error", reason = "Classification missing from AI in batch ${index + 1}")
                        })
                        Log.d("InboxViewModel", "Batch ${index + 1} processed. Total analyzed comments so far: ${analyzedComments.size}")

                    } catch (jsonParseError: JSONException) {
                        val detailedLog = "Error parsing Gemini response JSON for batch ${index + 1}: ${jsonParseError.message}\n${Log.getStackTraceString(jsonParseError)}"
                        Log.e("InboxViewModel", detailedLog, jsonParseError)
                        batch.forEach { _detailedErrorLogs.value = _detailedErrorLogs.value + (it.id to detailedLog) }
                        analyzedComments.addAll(batch.map { it.copy(priority = "Error", reason = "Failed to parse AI response. See detailed logs.") })
                    }
                }
            } catch (e: Exception) {
                val detailedLog = "Exception during Gemini API call for batch ${index + 1}: ${e.message}\n${Log.getStackTraceString(e)}"
                Log.e("InboxViewModel", detailedLog, e)
                batch.forEach { _detailedErrorLogs.value = _detailedErrorLogs.value + (it.id to detailedLog) }
                analyzedComments.addAll(batch.map { it.copy(priority = "Error", reason = "Network or API error. See detailed logs.") })
            }
        }
        return@withContext analyzedComments
    }

    private val _testLogOutput = MutableStateFlow("")
    val testLogOutput: StateFlow<String> = _testLogOutput.asStateFlow()

    fun runTestAiCall() {
        viewModelScope.launch {
            _testLogOutput.value = "" // Clear previous log
            _testLogOutput.value += "Starting AI test call...\n"
            try {
                val dummyComments = listOf(
                    ClassifiedComment(id = "test_1", text = "This is a test comment about a product issue.", from = "User A"),
                    ClassifiedComment(id = "test_2", text = "Great service, thank you!", from = "User B"),
                    ClassifiedComment(id = "test_3", text = "Where is my order? It's late!", from = "User C")
                )
                _testLogOutput.value += "Dummy comments prepared: ${dummyComments.size}\n"

                val analyzedComments = analyzeCommentsWithGeminiAPI(dummyComments)
                _testLogOutput.value += "AI analysis completed. Results:\n"
                analyzedComments.forEach {
                    _testLogOutput.value += "  ID: ${it.id}, Priority: ${it.priority}, Reason: ${it.reason}, Reply: ${it.reply}\n"
                }
                _testLogOutput.value += "AI test call finished successfully.\n"
            } catch (e: Exception) {
                val errorMessage = "AI test call failed: ${e.message}\n"
                _testLogOutput.value += errorMessage
                Log.e("InboxViewModel", errorMessage, e)
            }
        }
    }

    suspend fun generateReply(comment: ClassifiedComment): String {
        // This is a placeholder. In a real app, you'd call the AI again
        // or have the initial prompt also generate a reply.
        return "Thank you for your comment!"
    }

    fun goToNextPage() {
        val totalPages = (_allRawComments.value.size + pageSize - 1) / pageSize // Use _allRawComments for total pages
        if (currentPage.value < totalPages - 1) {
            currentPage.value++
            analyzeCurrentPageComments() // Trigger analysis for the new page
            analyzeCurrentPageComments()
        }
    }

    fun goToPreviousPage() {
        if (currentPage.value > 0) {
            currentPage.value--
            analyzeCurrentPageComments() // Trigger analysis for the new page
        }
    }

    fun goToPage(page: Int) {
        val totalPages = (_allRawComments.value.size + pageSize - 1) / pageSize // Use _allRawComments for total pages
        val newPage = when {
            page < 0 && totalPages > 0 -> 0 // Go to first page if requested page is too low
            page >= totalPages && totalPages > 0 -> totalPages - 1 // Go to last page if requested page is too high
            page >= 0 && page < totalPages -> page
            else -> currentPage.value // Stay on current page if no valid change
        }
        if (newPage != currentPage.value) {
            currentPage.value = newPage
            analyzeCurrentPageComments() // Trigger analysis for the new page
        }
    }

    fun getCurrentPageNumber(): Int = currentPage.value + 1 // 1-based page number
    fun getTotalPages(): Int = (_allRawComments.value.size + pageSize - 1) / pageSize
    fun getTotalComments(): Int = _allRawComments.value.size

    fun sendReply(commentId: String, replyText: String, pageToken: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val url = "https://graph.facebook.com/v18.0/$commentId/comments"
                    val requestBody = JSONObject().apply {
                        put("message", replyText)
                        put("access_token", pageToken)
                    }.toString()

                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("InboxViewModel", "Successfully posted reply to comment $commentId. Response: ${response.body?.string()}")
                        } else {
                            Log.e("InboxViewModel", "Failed to post reply to comment $commentId. Response: ${response.body?.string()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InboxViewModel", "Exception while posting reply to comment $commentId: ${e.message}", e)
                }
            }
        }
    }
}