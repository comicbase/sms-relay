package com.example.smsrelay.data

import android.content.Context

/** 用户手动维护的卡槽号码，不依赖运营商是否向 Android 提供本机号码。 */
data class SimNumbers(val sim1: String = "", val sim2: String = "") {
    fun recipientFor(slot: Int?): String? = when (slot) {
        0 -> sim1
        1 -> sim2
        else -> null // 未知卡槽不能猜测为 SIM 1，单卡手机也一样。
    }?.trim()?.takeIf { it.isNotEmpty() }

    fun isValid(): Boolean = listOf(sim1, sim2).all {
        it.isBlank() || PHONE_PATTERN.matches(it.trim())
    }

    companion object {
        // 支持国际区号；保存为文本，保留 + 和前导零。留空表示未配置。
        private val PHONE_PATTERN = Regex("\\+?[0-9]{3,15}")
    }
}

class SimNumberStore(context: Context) {
    private val preferences = context.getSharedPreferences("sim_numbers", Context.MODE_PRIVATE)

    fun get() = SimNumbers(
        sim1 = preferences.getString("slot_0", "").orEmpty(),
        sim2 = preferences.getString("slot_1", "").orEmpty(),
    )

    fun save(numbers: SimNumbers) {
        require(numbers.isValid())
        preferences.edit()
            .putString("slot_0", numbers.sim1.trim())
            .putString("slot_1", numbers.sim2.trim())
            .apply()
    }
}
