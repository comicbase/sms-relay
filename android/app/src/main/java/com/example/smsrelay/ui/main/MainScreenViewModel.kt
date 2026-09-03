package com.example.smsrelay.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smsrelay.AppContainer
import com.example.smsrelay.data.SimNumbers
import com.example.smsrelay.data.local.SmsEntity
import com.example.smsrelay.worker.UploadScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 页面渲染所需的不可变快照。Compose 只消费状态，不直接访问数据库。 */
data class MainUiState(
    val isConfigured: Boolean = false,
    val isSignedIn: Boolean = false,
    val isBusy: Boolean = false,
    val messages: List<SmsEntity> = emptyList(),
    val status: String? = null,
    val simNumbers: SimNumbers = SimNumbers(),
)

/** 把登录、配置和同步操作连接到 UI 状态。 */
class SmsRelayViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MainUiState(
            isConfigured = container.supabase.isConfigured,
            isSignedIn = container.sessionStore.get() != null,
            simNumbers = container.simNumberStore.get(),
        ),
    )
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        // Room Flow 是短信列表的单一数据源；插入后无需手动“刷新界面”。
        viewModelScope.launch {
            container.database.smsDao().observeAll().collect { messages ->
                mutableState.update { it.copy(messages = messages) }
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            mutableState.update { it.copy(status = "请输入邮箱和密码") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, status = null) }
            runCatching {
                container.supabase.login(email.trim(), password)
                container.supabase.ensureDevice(container.deviceIdentity.id, container.deviceName)
            }.onSuccess {
                mutableState.update {
                    it.copy(isBusy = false, isSignedIn = true, status = "登录成功，正在同步")
                }
                UploadScheduler.enqueue(container.appContext)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(isBusy = false, status = error.message ?: "登录失败")
                }
            }
        }
    }

    fun saveSimNumbers(numbers: SimNumbers) {
        if (!numbers.isValid()) {
            mutableState.update { it.copy(status = "手机号须为 3–15 位数字，可带 + 区号，或留空") }
            return
        }
        container.simNumberStore.save(numbers)
        mutableState.update {
            it.copy(simNumbers = container.simNumberStore.get(), status = "SIM 号码已保存，仅对之后收到的短信生效")
        }
    }

    fun saveConfig(url: String, publishableKey: String) {
        if (!url.trim().startsWith("https://") || publishableKey.isBlank()) {
            mutableState.update { it.copy(status = "请输入有效的 HTTPS Project URL 和 Publishable key") }
            return
        }
        container.configStore.save(url, publishableKey)
        mutableState.update {
            it.copy(isConfigured = true, status = "Supabase 配置已保存，请登录")
        }
    }

    fun clearConfig() {
        container.sessionStore.clear()
        container.configStore.clear()
        mutableState.update {
            it.copy(isConfigured = false, isSignedIn = false, status = "请重新填写 Supabase 配置")
        }
    }

    fun logout() {
        container.sessionStore.clear()
        mutableState.update { it.copy(isSignedIn = false, status = "已退出登录") }
    }

    fun sync(context: android.content.Context) {
        UploadScheduler.enqueue(context)
        mutableState.update { it.copy(status = "已提交同步任务") }
    }

    fun permissionResult(granted: Boolean) {
        mutableState.update {
            it.copy(status = if (granted) "短信接收权限已开启" else "未获得短信接收权限")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SmsRelayViewModel(container) as T
            }
    }
}
