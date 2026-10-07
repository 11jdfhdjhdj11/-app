package com.daozhang.yuyin.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daozhang.yuyin.audio.Announcer
import com.daozhang.yuyin.audio.OfflineVoice
import com.daozhang.yuyin.audio.OfflineVoices

/** 一行：当前音色 + “更换”。点开为分组音色列表，每个音色可单独试听。 */
@Composable
fun VoicePickerRow(current: String, systemVoices: List<String>, sampleText: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val offline = remember { OfflineVoices.engine(ctx)?.voices.orEmpty() }
    var open by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("音色", style = MaterialTheme.typography.bodyLarge)
            Text(
                voiceLabel(current, offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { open = true }) { Text("更换") }
    }
    if (open) {
        VoicePickerDialog(current, offline, systemVoices, sampleText, onDismiss = { open = false }) {
            onPick(it); open = false
        }
    }
}

fun voiceLabel(key: String, offline: List<OfflineVoice>): String {
    val sid = OfflineVoice.sidOf(key)
    return when {
        sid != null -> offline.firstOrNull { it.sid == sid }?.let { "${it.label}（内置离线）" } ?: "内置音色不可用，使用系统语音"
        key.isEmpty() -> "系统默认语音"
        else -> "系统：$key"
    }
}

private enum class VoiceTab(val title: String) { RECOMMENDED("推荐"), FEMALE("女声"), MALE("男声"), SYSTEM("系统语音") }

@Composable
private fun VoicePickerDialog(
    current: String,
    offline: List<OfflineVoice>,
    systemVoices: List<String>,
    sampleText: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val tabs = if (offline.isEmpty()) listOf(VoiceTab.SYSTEM) else VoiceTab.entries
    var tab by remember { mutableStateOf(tabs.first()) }
    var selected by remember { mutableStateOf(current) }
    var previewing by remember { mutableStateOf<String?>(null) }

    fun preview(key: String) {
        previewing = key
        Announcer.enqueue(sampleText, 1, voice = key) { err ->
            previewing = null
            if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择音色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tabs.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabs.forEach { t -> FilterChip(tab == t, { tab = t }, label = { Text(t.title) }) }
                    }
                }
                if (tab != VoiceTab.SYSTEM) {
                    Text(
                        "内置音色完全离线。首次使用会加载模型，需要几秒。“✓”表示金额读法已自动校验通过。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
                    when (tab) {
                        VoiceTab.SYSTEM -> {
                            item {
                                VoiceRow("系统默认语音", "跟随系统语音引擎设置", selected == "", previewing == "",
                                    onSelect = { selected = "" }, onPreview = { preview("") })
                            }
                            items(systemVoices) { name ->
                                VoiceRow(name, "系统语音引擎", selected == name, previewing == name,
                                    onSelect = { selected = name }, onPreview = { preview(name) })
                            }
                            if (systemVoices.isEmpty()) item {
                                Text("没有找到其他系统中文音色。可在系统设置 → 文字转语音 中安装语音引擎。",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        else -> {
                            val list = when (tab) {
                                VoiceTab.RECOMMENDED -> offline.filter { it.recommended }
                                VoiceTab.FEMALE -> offline.filter { it.female }
                                else -> offline.filter { !it.female }
                            }
                            items(list, key = { it.sid }) { v ->
                                val sub = buildString {
                                    append(v.code)
                                    append(if (v.verified) " · ✓ 金额读法校验通过" else " · 校验未通过，个别字可能读错")
                                }
                                VoiceRow((if (v.recommended) "★ " else "") + v.label, sub, selected == v.key, previewing == v.key,
                                    onSelect = { selected = v.key }, onPreview = { preview(v.key) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text("使用此音色") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun VoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    previewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onPreview, enabled = !previewing) { Text(if (previewing) "播放中" else "试听") }
    }
}
