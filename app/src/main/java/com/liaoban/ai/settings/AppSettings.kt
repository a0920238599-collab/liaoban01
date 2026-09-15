package com.liaoban.ai.settings

import android.content.Context
import com.liaoban.ai.security.SecureStore

class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("liaoban_settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(appContext)

    var endpoint: String
        get() = prefs.getString("runapi_endpoint", "") ?: ""
        set(value) = prefs.edit().putString("runapi_endpoint", value.trim()).apply()

    var memoryModel: String
        get() = prefs.getString("memory_model", "deepseek-v4-pro") ?: "deepseek-v4-pro"
        set(value) = prefs.edit().putString("memory_model", value.trim()).apply()

    var proactiveModel: String
        get() = prefs.getString("proactive_model", "deepseek-v4-pro") ?: "deepseek-v4-pro"
        set(value) = prefs.edit().putString("proactive_model", value.trim()).apply()

    var apiKey: String
        get() = secure.getApiKey()
        set(value) = secure.putApiKey(value)

    fun configured(): Boolean =
        endpoint.isNotBlank() && apiKey.isNotBlank()
}
