package com.ammu.social.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log // Added import

object TokenManager {

    private const val PREFS_NAME = "ammu_prefs"
    private const val KEY_PAGE_ACCESS_TOKEN = "page_access_token"
    private const val KEY_PAGE_ID = "page_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePageData(context: Context, pageId: String, token: String) {
        Log.d("TokenManager", "Attempting to save page data: pageId=$pageId, token=${token.take(10)}...")
        val editor = getPrefs(context).edit()
        editor.putString(KEY_PAGE_ID, pageId)
        editor.putString(KEY_PAGE_ACCESS_TOKEN, token)
        val committed = editor.commit() // Use commit() for synchronous write and check return value
        Log.d("TokenManager", "Page data save committed: $committed")
    }

    fun getPageToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_PAGE_ACCESS_TOKEN, null)
        Log.d("TokenManager", "Retrieving page token: ${token?.take(10)}...")
        return token
    }

    fun getPageId(context: Context): String? {
        val pageId = getPrefs(context).getString(KEY_PAGE_ID, null)
        Log.d("TokenManager", "Retrieving page ID: $pageId")
        return pageId
    }

    fun clearToken(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PAGE_ID)
            .remove(KEY_PAGE_ACCESS_TOKEN)
            .apply()
    }
}
