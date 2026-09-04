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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged

/** 页面渲染所需的不可变快照。Compose 只消费状态，不直接访问数据库。 */
data class MainUiState(
    val isConfigured: Boolean = false,
    val isSignedIn: Boolean = false,
    val isBusy: Boolean = false,
    val messages: List<SmsEntity> = emptyList(),
    val status: String? = null,
    val simNumbers: SimNumbers = SimNumbers(),
    val localCount: Int = 0,
    val pendingCount: Int = 0,
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val pageLoading: Boolean = true,
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
    private data class Cursor(val time: Long, val id: String)
    private val cursors = mutableListOf<Cursor?>(null)
    private var pageJob: Job? = null
    private var pageGeneration = 0

    init {
        observePage()
        // 总数在 SQL 中统计，不从当前页推算，也不读取其他页的正文。
        viewModelScope.launch {
            container.database.smsDao().observeTotalCount().distinctUntilChanged().collect { count ->
                mutableState.update { it.copy(localCount = count) }
            }
        }
        viewModelScope.launch {
            container.database.smsDao().observePendingCount().distinctUntilChanged().collect { count ->
                mutableState.update { it.copy(pendingCount = count) }
            }
        }
    }

    private fun observePage() {
        pageJob?.cancel()
        val generation = ++pageGeneration
        val cursor = cursors.last()
        mutableState.update { it.copy(pageLoading = true, page = cursors.size) }
        pageJob = viewModelScope.launch {
            val dao = container.database.smsDao()
            val rows = cursor?.let { dao.observePageBefore(it.time, it.id, PAGE_SIZE + 1) }
                ?: dao.observeFirstPage(PAGE_SIZE + 1)
            rows.collect { messages ->
                if (generation == pageGeneration) mutableState.update {
                    it.copy(messages = messages.take(PAGE_SIZE), hasNextPage = messages.size > PAGE_SIZE, pageLoading = false)
                }
            }
        }
    }

    fun nextPage() {
        val current = state.value
        if (current.pageLoading || !current.hasNextPage) return
        val last = current.messages.lastOrNull() ?: return
        cursors.add(Cursor(last.receivedAt, last.clientMessageId))
        observePage()
    }

    fun previousPage() {
        if (state.value.pageLoading || cursors.size <= 1) return
        cursors.removeAt(cursors.lastIndex)
        observePage()
    }

    fun latestPage() {
        if (state.value.pageLoading) return
        cursors.clear()
        cursors.add(null)
        observePage()
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
        const val PAGE_SIZE = 20
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SmsRelayViewModel(container) as T
            }
    }
}
