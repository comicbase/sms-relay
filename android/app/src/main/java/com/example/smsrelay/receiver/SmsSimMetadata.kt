package com.example.smsrelay.receiver

import android.content.Intent

/** 优先标准 telephony extra，再兼容原项目使用的旧版字段。卡槽是从 0 开始的。 */
object SmsSimMetadata {
    fun slot(intent: Intent): Int? = nonNegativeExtra(intent,
        "android.telephony.extra.SLOT_INDEX", "slot")

    fun subscriptionId(intent: Intent): Int? = nonNegativeExtra(intent,
        "android.telephony.extra.SUBSCRIPTION_INDEX", "subscription")

    private fun nonNegativeExtra(intent: Intent, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            intent.getIntExtra(key, -1).takeIf { it >= 0 }
        }
}
