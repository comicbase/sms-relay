package com.example.smsrelay.data

import android.content.Context
import com.example.smsrelay.BuildConfig

/** Supabase URL 和 Publishable Key 都是客户端公开配置；真正的授权由 Auth + RLS 完成。 */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    val isValid: Boolean
        get() = url.startsWith("https://") && publishableKey.isNotBlank()
}

/** 在应用内配置优先、构建时配置兜底，方便同一个 APK 连接不同教学项目。 */
class ConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("supabase_config", Context.MODE_PRIVATE)

    fun get(): SupabaseConfig = SupabaseConfig(
        url = preferences.getString(KEY_URL, null) ?: BuildConfig.SUPABASE_URL,
        publishableKey = preferences.getString(KEY_PUBLISHABLE_KEY, null)
            ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    )

    fun save(url: String, publishableKey: String) {
        preferences.edit()
            .putString(KEY_URL, url.trim().trimEnd('/'))
            .putString(KEY_PUBLISHABLE_KEY, publishableKey.trim())
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_PUBLISHABLE_KEY = "publishable_key"
    }
}
