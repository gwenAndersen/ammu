package com.ammu.social.facebook

import com.facebook.AccessToken
import com.facebook.GraphRequest
import com.facebook.GraphResponse
import com.facebook.HttpMethod
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class FacebookManager {

    interface CommentFetchListener {
        fun onCommentsFetched(comments: JSONArray)
        fun onError(error: String)
    }

    fun getPageComments(postId: String, pageAccessToken: AccessToken, listener: CommentFetchListener) {
        if (pageAccessToken.isExpired) {
            listener.onError("Page Access Token is null or expired.")
            return
        }

        val graphPath = "/$postId/comments"

        val request = GraphRequest(
            pageAccessToken,
            graphPath,
            null,
            HttpMethod.GET,
            GraphRequest.Callback { response ->
                if (response.error == null) {
                    try {
                        val responseObject = response.jsonObject
                        if (responseObject != null && responseObject.has("data")) {
                            val comments = responseObject.getJSONArray("data")
                            listener.onCommentsFetched(comments)
                        } else {
                            listener.onError("No 'data' field in response or response is null.")
                        }
                    } catch (e: JSONException) {
                        listener.onError("JSON parsing error: " + e.message)
                    }
                } else {
                    listener.onError("Graph API Error: " + response.error?.errorMessage)
                }
            }
        )
        request.executeAsync()
    }
}
