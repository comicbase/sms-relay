package com.example.smsrelay

import android.app.Application
import android.content.Context
import android.os.Build
import com.example.smsrelay.data.DeviceIdentity
import com.example.smsrelay.data.ConfigStore
import com.example.smsrelay.data.local.SmsDatabase
import com.example.smsrelay.data.remote.SupabaseClient
import com.example.smsrelay.data.session.SessionStore

/** Application 在进程创建时初始化整个应用共享的依赖。 */
class SmsRelayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        fun from(context: Context): SmsRelayApp = context.applicationContext as SmsRelayApp
    }
}

/**
 * 一个适合教学项目的轻量依赖容器。
 *
 * 生产级大型项目通常会使用 Hilt 等依赖注入框架；这里显式列出依赖，便于初学者
 * 看清 Database、配置、会话和网络客户端之间的关系。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database = SmsDatabase.get(appContext)
    val sessionStore = SessionStore(appContext)
    val configStore = ConfigStore(appContext)
    val deviceIdentity = DeviceIdentity(appContext)
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    // 每次读取都根据最新配置创建客户端，因此在设置页保存 URL 后无需重启应用。
    val supabase: SupabaseClient
        get() = configStore.get().let { config ->
            SupabaseClient(
                baseUrl = config.url,
                publishableKey = config.publishableKey,
                sessionStore = sessionStore,
            )
        }
}
