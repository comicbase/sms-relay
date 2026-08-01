package com.example.smsrelay.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 把“何时运行”的策略与实际上传逻辑分开，便于独立测试和调整。 */
object UploadScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadSmsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            // 连续失败时逐步延长等待时间，避免无网络时频繁唤醒设备。
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            // 有配额时尽快上传；配额耗尽时降级为普通任务，而不是丢弃任务。
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("sms-upload")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
