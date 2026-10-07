package com.daozhang.yuyin.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daozhang.yuyin.audio.Announcer
import com.daozhang.yuyin.audio.OfflineVoices
import com.daozhang.yuyin.data.Settings

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Announcer.init(this)
        val settings = Settings(this)
        if (!settings.voiceInitialized) {
            OfflineVoices.engine(this)?.voices?.firstOrNull { it.recommended }?.let { settings.voiceName = it.key }
            settings.voiceInitialized = true
        }
        setContent {
            AppTheme {
                var accepted by remember { mutableStateOf(settings.disclaimerAccepted) }
                if (!accepted) {
                    DisclaimerDialog(
                        onAccept = { settings.disclaimerAccepted = true; accepted = true },
                        onExit = { finish() },
                    )
                } else {
                    AppScaffold(requestNotifications = { requestNotifications() })
                }
            }
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Permissions.notificationsGranted(this)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppScaffold(requestNotifications: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Campaign, null) }, label = { Text("播报") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("任务") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
            }
        },
    ) { inner ->
        val m = Modifier.padding(inner)
        when (tab) {
            0 -> BroadcastScreen(m, requestNotifications, onTaskCreated = { tab = 1 })
            1 -> TasksScreen(m, openSettings = { tab = 2 })
            else -> SettingsScreen(m)
        }
    }
}

@Composable
private fun DisclaimerDialog(onAccept: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("使用前请阅读") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "本应用用于收款音箱/设备测试、软件演示、视频配音等场景，生成的是“收款风格”的模拟播报，" +
                        "每条播报前都带有本应用专属提示音，不是任何支付平台的官方到账提示。\n\n" +
                        "听到播报不代表真实到账。请勿用本应用冒充他人收款提示或误导他人，" +
                        "此类行为可能涉嫌诈骗，后果由使用者自行承担。\n\n" +
                        "本应用不联网，不收集任何个人信息，所有数据只保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("我已了解并同意") } },
        dismissButton = { TextButton(onClick = onExit) { Text("退出") } },
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val brand = Color(0xFF2F6BFF)
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF8FB0FF), secondary = Color(0xFFFFB86B))
    } else {
        lightColorScheme(primary = brand, secondary = Color(0xFFE07A1F))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 通用的页面内边距 */
val PagePadding = 16.dp
