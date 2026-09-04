package com.example.smsrelay.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsrelay.data.local.SmsEntity
import com.example.smsrelay.data.SimNumbers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 顶层 Composable 根据配置、登录和权限状态切换不同教学步骤。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SmsRelayViewModel) {
    // lifecycle-aware 收集会在页面不可见时自动暂停，避免无意义地持续观察 UI 状态。
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.page, state.pageLoading) {
        if (!state.pageLoading) listState.scrollToItem(0)
    }
    val context = LocalContext.current
    var showSimSettings by rememberSaveable { mutableStateOf(false) }
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Activity Result API 取代旧式 onRequestPermissionsResult 回调。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasSmsPermission = granted
        viewModel.permissionResult(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("短信中继 MVP") },
                actions = { TextButton(onClick = { showSimSettings = true }) { Text("SIM 号码") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            PermissionCard(
                hasSmsPermission = hasSmsPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                },
            )
            Spacer(Modifier.height(12.dp))
            // 设置 → 登录 → 正常状态，构成页面的三个主要教学阶段。
            if (!state.isConfigured) {
                ConfigCard(viewModel::saveConfig)
            } else if (!state.isSignedIn) {
                LoginCard(
                    state.isBusy,
                    state.localCount,
                    viewModel::login,
                    viewModel::clearConfig,
                )
            } else {
                StatusPanel(
                    localCount = state.localCount,
                    pendingCount = state.pendingCount,
                    onSync = { viewModel.sync(context) },
                    onLogout = viewModel::logout,
                )
            }

            state.status?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Text(
                text = "本机短信",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::previousPage, enabled = state.page > 1 && !state.pageLoading) { Text("上一页") }
                Text("第 ${state.page} 页 · 每页 ${SmsRelayViewModel.PAGE_SIZE} 条", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::nextPage, enabled = state.hasNextPage && !state.pageLoading) { Text("下一页") }
            }
            if (state.page > 1) TextButton(onClick = viewModel::latestPage, enabled = !state.pageLoading) { Text("返回最新短信") }
            if (state.pageLoading) {
                CircularProgressIndicator()
            } else if (state.messages.isEmpty()) {
                Text(if (state.page == 1) "尚未收到短信。授权后，新短信会先保存在本机。" else "本页暂无短信，请返回最新短信。")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.clientMessageId }) { message ->
                        MessageCard(message)
                    }
                }
            }
        }
    }
    if (showSimSettings) {
        SimNumbersDialog(
            initial = state.simNumbers,
            onSave = { viewModel.saveSimNumbers(it); showSimSettings = false },
            onDismiss = { showSimSettings = false },
        )
    }
}

@Composable
private fun SimNumbersDialog(initial: SimNumbers, onSave: (SimNumbers) -> Unit, onDismiss: () -> Unit) {
    var sim1 by rememberSaveable { mutableStateOf(initial.sim1) }
    var sim2 by rememberSaveable { mutableStateOf(initial.sim2) }
    val numbers = SimNumbers(sim1, sim2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("接收 SIM 手机号") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("按系统设置中的卡槽填写，单卡只填实际使用的卡槽。换卡或更换卡槽后请更新。")
                OutlinedTextField(
                    value = sim1, onValueChange = { sim1 = it }, label = { Text("SIM 1 手机号") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sim2, onValueChange = { sim2 = it }, label = { Text("SIM 2 手机号") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("可带 + 区号；留空表示未配置。仅影响之后收到的短信；无法识别卡槽时不会猜测号码。")
                if (!numbers.isValid()) Text("请输入 3–15 位数字，可带 +，不要带空格或横线。", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(numbers) }, enabled = numbers.isValid()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PermissionCard(
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("短信接收", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasSmsPermission) {
                    "权限已开启。新短信将先保存在本机，再尝试上传。"
                } else {
                    "需要短信接收权限才能捕获新短信。"
                },
            )
            if (!hasSmsPermission) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("授予短信权限") }
            }
        }
    }
}

@Composable
private fun ConfigCard(onSave: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("尚未配置 Supabase", style = MaterialTheme.typography.titleMedium)
            Text("填写客户端可用的 Project URL 和 Publishable key。不要填写 Secret 或 service_role key。")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Project URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Publishable key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = { onSave(url, key) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("保存配置") }
        }
    }
}

@Composable
private fun LoginCard(
    isBusy: Boolean,
    localCount: Int,
    onLogin: (String, String) -> Unit,
    onChangeConfig: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("登录 Supabase", style = MaterialTheme.typography.titleMedium)
            Text("本机已有 $localCount 条短信。密码只用于登录，不会写入 APK。")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Auth 用户邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = { onLogin(email, password) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                else Text("登录")
            }
            TextButton(
                onClick = onChangeConfig,
                modifier = Modifier.align(Alignment.End),
            ) { Text("修改 Supabase 配置") }
        }
    }
}

@Composable
private fun StatusPanel(
    localCount: Int,
    pendingCount: Int,
    onSync: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("服务状态", style = MaterialTheme.typography.titleMedium)
            Text("本机：$localCount 条 · 待上传：$pendingCount 条")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onSync) { Text("立即同步") }
                TextButton(onClick = onLogout) { Text("退出") }
            }
        }
    }
}

@Composable
private fun MessageCard(message: SmsEntity) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.sender, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (message.uploadedAt != null) "已上传" else "待上传",
                    color = if (message.uploadedAt != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(formatter.format(Instant.ofEpochMilli(message.receivedAt)))
            Text(
                "接收号码：${message.recipient ?: "未记录"}" +
                    (message.simSlot?.let { " · SIM ${it + 1}" } ?: " · 卡槽未知"),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(message.body)
            message.lastUploadError?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "上传错误：$it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
