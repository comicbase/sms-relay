package com.example.smsrelay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 中的一条短信，同时也是上传队列的一项。
 * uploadedAt 为 null 表示仍需上传，因此 MVP 不需要额外再建一张队列表。
 */
@Entity(
    tableName = "received_sms",
    indices = [
        Index(value = ["receivedAt"]),
        Index(value = ["uploadedAt"]),
    ],
)
data class SmsEntity(
    // 指纹作为主键，使重复的系统广播不会产生两条本地记录。
    @PrimaryKey val clientMessageId: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int?,
    val simSlot: Int?,
    val uploadedAt: Long? = null,
    val lastUploadError: String? = null,
)
