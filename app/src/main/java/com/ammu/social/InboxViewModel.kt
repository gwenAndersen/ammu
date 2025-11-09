package com.ammu.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
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

    // --- START: Hardcoded Credentials ---
    private val GEMINI_API_KEY = "AIzaSyAjWWFl_pwlHD8sQQjav4maNuTQ2BmgdbQ"
    // --- END: Hardcoded Credentials ---

    // --- API URLs ---
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val comments = mutableStateOf<List<ClassifiedComment>>(emptyList()) // This will now observe paginatedComments

    private val _allAnalyzedComments = MutableStateFlow<List<ClassifiedComment>>(emptyList())
    private val currentPage = mutableStateOf(0)
    private val pageSize = 50

    val paginatedComments: StateFlow<List<ClassifiedComment>> = combine(
        _allAnalyzedComments,
        currentPage.asStateFlow() // Convert mutableStateOf to StateFlow
    ) { allComments, page ->
        val startIndex = page * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(allComments.size)
        if (startIndex < allComments.size) {
            allComments.subList(startIndex, endIndex)
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
                val facebookResponse = fetchFromApi(fbCommentsUrl)
                Log.d("InboxViewModel", "Facebook API raw response: ${facebookResponse?.take(500)}") // Log first 500 chars
                if (facebookResponse != null) {
                    val rawComments = parseFacebookComments(facebookResponse)
                    Log.d("InboxViewModel", "Parsed raw comments count: ${rawComments.size}")
                    if (rawComments.isNotEmpty()) {
                        val analyzedComments = analyzeCommentsWithGeminiAPI(rawComments)
                        _allAnalyzedComments.value = analyzedComments
                        Log.d("InboxViewModel", "Analyzed comments count: ${analyzedComments.size}")
                    } else {
                        _allAnalyzedComments.value = emptyList()
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

    private suspend fun fetchFromApi(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
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
            Log.e("InboxViewModel", "Exception during API call to $url: ${e.message}", e)
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
        val analyzedComments = mutableListOf<ClassifiedComment>()

        if (rawComments.isEmpty()) {
            return@withContext emptyList()
        }

        // Construct a single prompt for all comments
        val commentsForPrompt = rawComments.joinToString(separator = "\n---\n") {
            "Comment ID: ${it.id}\nComment Text: ${it.text}"
        }

        val prompt = """
            You are a professional social media manager for a busy brand. Your task is to analyze the following Facebook comments and classify each one based on urgency and content.

            For each comment provided below, return a JSON object with two fields:
            1.  "id": The original Comment ID.
            2.  "priority": "High", "Medium", or "Low" based on the urgency of the comment.
            3.  "reason": A brief, one-sentence explanation for your classification.

            Return a JSON array containing one such object for each comment.

            --- Comments to Analyze ---
            $commentsForPrompt
            --- End of Comments ---
            """

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
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("InboxViewModel", "Gemini API call failed: ${response.code} - $errorBody")
                    // Return original comments marked as error
                    return@withContext rawComments.map { it.copy(priority = "Error", reason = "Gemini API error: ${response.code}") }
                }

                val geminiData = JSONObject(response.body?.string())
                val textContent = geminiData.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                val cleanedJson = textContent.trim().removePrefix("```json\n").removeSuffix("\n```")

                try {
                    val classifiedCommentsArray = JSONArray(cleanedJson)
                    val classifiedMap = mutableMapOf<String, ClassifiedComment>()

                    for (i in 0 until classifiedCommentsArray.length()) {
                        val classifiedCommentJson = classifiedCommentsArray.getJSONObject(i)
                        val id = classifiedCommentJson.optString("id", "")
                        val priority = classifiedCommentJson.optString("priority", "Low")
                        val reason = classifiedCommentJson.optString("reason", "N/A")

                        // Find the original comment to copy its text and from fields
                        val originalComment = rawComments.find { it.id == id }
                        if (originalComment != null) {
                            classifiedMap[id] = originalComment.copy(priority = priority, reason = reason)
                        } else {
                            Log.w("InboxViewModel", "Gemini returned classification for unknown comment ID: $id")
                        }
                    }
                    // Ensure all original comments are represented, even if Gemini missed some
                    return@withContext rawComments.map {
                        classifiedMap[it.id] ?: it.copy(priority = "Error", reason = "Classification missing from AI")
                    }

                } catch (jsonParseError: JSONException) {
                    Log.e("InboxViewModel", "Error parsing Gemini response JSON: ${jsonParseError.message}", jsonParseError)
                    return@withContext rawComments.map { it.copy(priority = "Error", reason = "Failed to parse AI response.") }
                }
            }
        } catch (e: Exception) {
            Log.e("InboxViewModel", "Exception during Gemini API call: ${e.message}", e)
            return@withContext rawComments.map { it.copy(priority = "Error", reason = "Network or API error: ${e.message}") }
        }
    }

    fun goToNextPage() {
        val totalPages = (_allAnalyzedComments.value.size + pageSize - 1) / pageSize
        if (currentPage.value < totalPages - 1) {
            currentPage.value++
        }
    }

    fun goToPreviousPage() {
        if (currentPage.value > 0) {
            currentPage.value--
        }
    }

    fun goToPage(page: Int) {
        val totalPages = (_allAnalyzedComments.value.size + pageSize - 1) / pageSize
        if (page >= 0 && page < totalPages) {
            currentPage.value = page
        } else if (page >= totalPages && totalPages > 0) { // Go to last page if requested page is too high
            currentPage.value = totalPages - 1
        } else if (page < 0 && totalPages > 0) { // Go to first page if requested page is too low
            currentPage.value = 0
        }
    }

    fun getCurrentPageNumber(): Int = currentPage.value + 1 // 1-based page number
    fun getTotalPages(): Int = (_allAnalyAnalyzedComments.value.size + pageSize - 1) / pageSize
    fun getTotalComments(): Int = _allAnalyzedComments.value.size
}
