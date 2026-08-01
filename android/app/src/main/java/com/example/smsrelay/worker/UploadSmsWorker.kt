package com.example.smsrelay.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smsrelay.SmsRelayApp
import com.example.smsrelay.data.remote.SignedOutException

/**
 * 读取 Room 中未上传的短信并同步到 Supabase。
 * Room 是队列的事实来源，WorkManager 只负责可靠地触发消费过程。
 */
class UploadSmsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = SmsRelayApp.from(applicationContext).container
        if (!container.supabase.isConfigured || container.sessionStore.get() == null) {
            // 未配置或未登录不是网络故障；保留本地记录，用户登录后会再次调度。
            return Result.success()
        }

        return try {
            container.supabase.ensureDevice(container.deviceIdentity.id, container.deviceName)
            val pending = container.database.smsDao().pending()
            pending.forEach { message ->
                try {
                    container.supabase.upload(container.deviceIdentity.id, message)
                    container.database.smsDao().markUploaded(
                        message.clientMessageId,
                        System.currentTimeMillis(),
                    )
                } catch (error: Exception) {
                    container.database.smsDao().recordUploadError(
                        message.clientMessageId,
                        error.message.orEmpty().take(300),
                    )
                    throw error
                }
            }
            // DAO 每批最多取 50 条；满批说明可能还有数据，用新任务继续排空队列。
            if (pending.size == 50) UploadScheduler.enqueue(applicationContext)
            Result.success()
        } catch (_: SignedOutException) {
            // 刷新令牌失效需要用户交互，自动重试不会解决，因此不制造重试风暴。
            Result.success()
        } catch (_: Exception) {
            // 网络和服务端临时错误交给 WorkManager 的退避策略处理。
            Result.retry()
        }
    }
}
