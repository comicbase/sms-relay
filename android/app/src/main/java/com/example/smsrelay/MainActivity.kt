package com.example.smsrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.smsrelay.theme.SmsRelayTheme
import com.example.smsrelay.ui.main.MainScreen
import com.example.smsrelay.ui.main.SmsRelayViewModel

/**
 * 单 Activity 的 Compose 入口。
 *
 * Activity 只负责装配界面；业务状态和操作放在 [SmsRelayViewModel]，避免配置变化时
 * 把登录、同步等逻辑写进 UI 生命周期。
 */
class MainActivity : ComponentActivity() {
  private val viewModel: SmsRelayViewModel by viewModels {
    SmsRelayViewModel.factory((application as SmsRelayApp).container)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      SmsRelayTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainScreen(viewModel = viewModel)
        }
      }
    }
  }
}
