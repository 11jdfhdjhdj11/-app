package com.daozhang.yuyin.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.daozhang.yuyin.audio.Announcer
import com.daozhang.yuyin.audio.OfflineVoice
import com.daozhang.yuyin.core.AmountSpeller
import com.daozhang.yuyin.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val s = remember { Settings(ctx) }

    var globalOn by remember { mutableStateOf(s.globalEnabled) }
    var colloquial by remember { mutableStateOf(s.colloquial) }
    var liang by remember { mutableStateOf(s.useLiang) }
    var zheng by remember { mutableStateOf(s.appendZheng) }
    var alarmStream by remember { mutableStateOf(s.useAlarmStream) }
    var gain by remember { mutableFloatStateOf(s.gainDb) }
    var rate by remember { mutableFloatStateOf(s.speechRate) }
    var pitch by remember { mutableFloatStateOf(s.pitch) }
    var chime by remember { mutableIntStateOf(s.chimeStyle) }
    var voice by remember { mutableStateOf(s.voiceName) }
    var voices by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        voices = withContext(Dispatchers.IO) { Announcer.chineseVoices().map { it.name } }
    }

    val sample = AmountSpeller.compose(s.lastPrefix, 1990, s.lastSuffix, s.spellOptions())
    fun preview() = Announcer.enqueue(AmountSpeller.compose(s.lastPrefix, 1990, s.lastSuffix, s.spellOptions())) { err ->
        if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        Section("总开关") {
            SwitchRow("启用所有定时/循环任务", "关闭后到点直接跳过，“立即播报”不受影响", globalOn) {
                globalOn = it; s.globalEnabled = it
            }
        }

        Section("读法") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(colloquial, { colloquial = true; s.colloquial = true }, label = { Text("口语：十九块九") })
                FilterChip(!colloquial, { colloquial = false; s.colloquial = false }, label = { Text("标准：十九点九元") })
            }
            if (colloquial) SwitchRow("2 读作“两”", "两块、两百、两千", liang) { liang = it; s.useLiang = it }
            SwitchRow("整数金额加“整”", "如“二十块整”", zheng) { zheng = it; s.appendZheng = it }
            Text("示例：$sample", style = MaterialTheme.typography.bodySmall)
        }

        Section("声音") {
            Text("专属提示音（每条播报前固定播放，可换样式）", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Settings.Chimes.NAMES.forEachIndexed { i, name ->
                    FilterChip(chime == i, { chime = i; s.chimeStyle = i; Announcer.clearCache() }, label = { Text(name) })
                }
            }
            VoicePickerRow(voice, voices, sample) { v -> voice = v; s.voiceName = v; Announcer.clearCache() }
            SliderRow("语速", rate, 0.8f..1.2f, "%.2f×".format(rate)) { rate = it; s.speechRate = it; Announcer.clearCache() }
            if (OfflineVoice.sidOf(voice) == null) {
                SliderRow("音调", pitch, 0.8f..1.2f, "%.2f×".format(pitch)) { pitch = it; s.pitch = it; Announcer.clearCache() }
            } else {
                Text("内置音色不支持调音调，可换其他音色获得不同声线。", style = MaterialTheme.typography.bodySmall)
            }
            SliderRow("增益", gain, 0f..6f, "+%.1f dB".format(gain)) { gain = it; s.gainDb = it }
            SwitchRow("使用闹钟音量", "静音模式下也能响；关闭则跟随媒体音量", alarmStream) {
                alarmStream = it; s.useAlarmStream = it
            }
            OutlinedButton(onClick = { preview() }) { Text("试听效果") }
        }

        PermissionCenter()

        Text(
            "本应用不联网、不收集个人信息。生成的是“收款风格”的模拟播报，不是任何支付平台的官方提示，请勿用于冒充收款。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCenter() {
    val ctx = LocalContext.current
    var notif by remember { mutableStateOf(true) }
    var exact by remember { mutableStateOf(true) }
    var battery by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notif = Permissions.notificationsGranted(ctx)
        exact = Permissions.exactAlarmGranted(ctx)
        battery = Permissions.batteryUnrestricted(ctx)
        onPauseOrDispose { }
    }
    Section("权限中心") {
        PermRow("通知", "显示任务运行状态和“暂停全部”按钮", notif) { Permissions.openNotificationSettings(ctx) }
        PermRow("闹钟和提醒", "让定时播报准时，误差在几秒内", exact) { Permissions.openExactAlarm(ctx) }
        PermRow("忽略电池优化", "防止息屏后被系统冻结", battery) { Permissions.openBattery(ctx) }
        PermRow("自启动 / 后台运行", Permissions.vendorHint(), null) { Permissions.openAutostart(ctx) }
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean?, onGo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (granted) {
            true -> Text("已开启", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            false -> TextButton(onClick = onGo) { Text("去开启") }
            null -> TextButton(onClick = onGo) { Text("去设置") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text(shown, style = MaterialTheme.typography.bodySmall)
        }
        Slider(value, onChange, valueRange = range)
    }
}
