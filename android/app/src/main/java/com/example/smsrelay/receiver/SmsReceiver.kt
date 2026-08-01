package com.example.smsrelay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.smsrelay.SmsRelayApp
import com.example.smsrelay.data.MessageFingerprint
import com.example.smsrelay.data.local.SmsEntity
import com.example.smsrelay.worker.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manifest 注册的短信广播接收器。
 *
 * Receiver 生命周期很短，所以使用 [goAsync] 获取 PendingResult，并把数据库 IO 放到
 * 后台协程。无论成功或失败，finally 都必须调用 finish() 归还系统资源。
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // 一条长短信可能由多个 PDU 组成，Android 会把它们放在同一个 Intent 中。
                val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (parts.isEmpty()) return@launch

                val sender = parts.first().displayOriginatingAddress.orEmpty()
                val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
                val receivedAt = parts.minOf { it.timestampMillis }
                val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it >= 0 }
                val simSlot = intent.getIntExtra("slot", -1).takeIf { it >= 0 }
                val clientMessageId = MessageFingerprint.create(
                    sender,
                    body,
                    receivedAt,
                    subscriptionId,
                )

                val app = SmsRelayApp.from(appContext)
                // 先持久化再调度上传：即使此刻断网，消息仍可在稍后补传。
                app.container.database.smsDao().insert(
                    SmsEntity(
                        clientMessageId = clientMessageId,
                        sender = sender.ifBlank { "未知号码" },
                        body = body,
                        receivedAt = receivedAt,
                        subscriptionId = subscriptionId,
                        simSlot = simSlot,
                    ),
                )
                UploadScheduler.enqueue(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
