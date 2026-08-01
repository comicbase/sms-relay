package com.example.smsrelay.data

import java.security.MessageDigest

/** 生成跨本地数据库和云端数据库都可复用的幂等键。 */
object MessageFingerprint {
    fun create(sender: String, body: String, receivedAt: Long, subscriptionId: Int?): String {
        // 使用空字符分隔字段，避免 ["ab", "c"] 与 ["a", "bc"] 产生相同拼接结果。
        val source = "$sender\u0000$body\u0000$receivedAt\u0000$subscriptionId"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
