package com.example.smsrelay.data

import android.content.Context
import java.util.UUID

/**
 * 为本次安装生成稳定 UUID，而不是读取 IMEI 等硬件标识。
 * 这样既减少敏感权限，也让 Supabase 能区分同一账号下的多台手机。
 */
class DeviceIdentity(context: Context) {
    private val preferences = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    val id: String
        get() = preferences.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_ID, it).apply()
        }

    companion object {
        private const val KEY_ID = "device_id"
    }
}
