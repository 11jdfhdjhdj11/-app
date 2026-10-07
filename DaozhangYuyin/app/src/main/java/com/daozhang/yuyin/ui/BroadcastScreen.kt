package com.daozhang.yuyin.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daozhang.yuyin.audio.Announcer
import com.daozhang.yuyin.core.AmountSpeller
import com.daozhang.yuyin.data.AppDb
import com.daozhang.yuyin.data.BroadcastTask
import com.daozhang.yuyin.data.Mode
import com.daozhang.yuyin.data.PlayLog
import com.daozhang.yuyin.data.Settings
import com.daozhang.yuyin.sched.TaskActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val INTERVAL_PRESETS = listOf(60 to "1 分钟", 300 to "5 分钟", 600 to "10 分钟", 1800 to "半小时", 3600 to "1 小时")

@Composable
fun BroadcastScreen(modifier: Modifier, requestNotifications: () -> Unit, onTaskCreated: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()

    var amountText by remember { mutableStateOf("19.9") }
    var prefix by remember { mutableStateOf(settings.lastPrefix) }
    var suffix by remember { mutableStateOf(settings.lastSuffix) }
    var playTimes by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    val fen = AmountSpeller.parseFen(amountText)
    val speech = if (fen > 0) AmountSpeller.compose(prefix, fen, suffix, settings.spellOptions()) else null

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun saveTemplate() { settings.lastPrefix = prefix; settings.lastSuffix = suffix }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(PagePadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("到账语音工坊", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = amountText,
            onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() || it == '.' }) amountText = v },
            label = { Text("金额（元）") },
            supportingText = { Text(if (fen > 0) "范围 0.01 – 99999.99" else "请输入 0.01 – 99999.99，最多两位小数") },
            isError = fen <= 0,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(prefix, { if (it.length <= 12) prefix = it }, label = { Text("前缀") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(suffix, { if (it.length <= 12) suffix = it }, label = { Text("后缀") }, singleLine = true, modifier = Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("播报内容", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text("♪ 提示音 + " + (speech ?: "—"), style = MaterialTheme.typography.titleLarge)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("连播")
            (1..3).forEach { n -> FilterChip(playTimes == n, { playTimes = n }, label = { Text("$n 遍") }) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = speech != null && !busy,
                onClick = {
                    val text = speech!!
                    saveTemplate()
                    busy = true
                    Announcer.enqueue(text, playTimes) { err ->
                        busy = false
                        if (err != null) toast(err)
                    }
                    scope.launch(Dispatchers.IO) {
                        AppDb.get(ctx).dao().insertLog(PlayLog(taskId = 0, speechText = text, result = "OK"))
                    }
                },
            ) { Text(if (busy) "播报中…" else "立即播报") }
            OutlinedButton(
                enabled = speech != null,
                onClick = {
                    Announcer.exportAsync(speech!!, AmountSpeller.formatYuan(fen)) { r ->
                        r.onSuccess { toast("已保存到 音乐/${Announcer.EXPORT_DIR}") }
                            .onFailure { toast("导出失败：${it.message}") }
                    }
                },
            ) { Text("导出 WAV") }
        }

        Spacer(Modifier.height(4.dp))
        ScheduleCard(
            speech = speech,
            fen = fen,
            playTimes = playTimes,
            onCreate = { task ->
                saveTemplate()
                requestNotifications()
                scope.launch {
                    withContext(Dispatchers.IO) { TaskActions.create(ctx, task) }
                    toast("任务已创建")
                    onTaskCreated()
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(speech: String?, fen: Long, playTimes: Int, onCreate: (BroadcastTask) -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(Mode.INTERVAL) }
    var atTime by remember { mutableLongStateOf(System.currentTimeMillis() + 5 * 60_000) }
    var intervalSec by remember { mutableIntStateOf(300) }
    var customInterval by remember { mutableStateOf("") }
    var repeatText by remember { mutableStateOf("0") }
    var quietOn by remember { mutableStateOf(false) }
    var quietStart by remember { mutableIntStateOf(22 * 60) }
    var quietEnd by remember { mutableIntStateOf(8 * 60) }
    var askExact by remember { mutableStateOf(false) }
    var pendingTask by remember { mutableStateOf<BroadcastTask?>(null) }

    val customSec = customInterval.toIntOrNull()
    val effectiveInterval = if (customInterval.isNotBlank()) customSec ?: 0 else intervalSec
    val repeat = repeatText.toIntOrNull()
    val error = when {
        speech == null -> "请先输入有效金额"
        mode == Mode.AT && atTime <= System.currentTimeMillis() -> "触发时间需晚于现在"
        mode == Mode.INTERVAL && effectiveInterval < 30 -> "循环间隔最短 30 秒"
        mode == Mode.INTERVAL && (repeat == null || repeat !in 0..999) -> "循环次数为 0–999（0 = 无限）"
        else -> null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("加入计划", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == Mode.INTERVAL, { mode = Mode.INTERVAL }, label = { Text("间隔循环") })
                FilterChip(mode == Mode.AT, { mode = Mode.AT }, label = { Text("定点播报") })
            }

            if (mode == Mode.AT) {
                DateTimeField(atTime, { atTime = it })
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INTERVAL_PRESETS.forEach { (sec, label) ->
                        FilterChip(
                            customInterval.isBlank() && intervalSec == sec,
                            { intervalSec = sec; customInterval = "" },
                            label = { Text(label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        customInterval, { if (it.length <= 6 && it.all(Char::isDigit)) customInterval = it },
                        label = { Text("自定义间隔（秒）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        repeatText, { if (it.length <= 3 && it.all(Char::isDigit)) repeatText = it },
                        label = { Text("次数（0=无限）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("创建后约 5 秒开始第一次播报", style = MaterialTheme.typography.bodySmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("静默时段（到点跳过，不补播）", Modifier.weight(1f))
                Switch(quietOn, { quietOn = it })
            }
            if (quietOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinuteOfDayButton("从", quietStart) { quietStart = it }
                    MinuteOfDayButton("到", quietEnd) { quietEnd = it }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            FilledTonalButton(
                enabled = error == null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val now = System.currentTimeMillis()
                    val yuan = AmountSpeller.formatYuan(fen)
                    val first = if (mode == Mode.AT) atTime else now + 5_000
                    val task = BroadcastTask(
                        name = if (mode == Mode.AT) "定点 $yuan 元" else "每 ${describeInterval(effectiveInterval)} · $yuan 元",
                        amountFen = fen,
                        speechText = speech!!,
                        mode = mode,
                        startAt = first,
                        intervalSec = if (mode == Mode.INTERVAL) effectiveInterval else 0,
                        repeatTotal = if (mode == Mode.INTERVAL) repeat!! else 1,
                        playTimes = playTimes,
                        quietStartMin = if (quietOn) quietStart else -1,
                        quietEndMin = if (quietOn) quietEnd else -1,
                        nextFireAt = first,
                    )
                    if (!Permissions.exactAlarmGranted(ctx)) {
                        pendingTask = task
                        askExact = true
                    } else {
                        onCreate(task)
                    }
                },
            ) { Text("创建任务") }
        }
    }

    if (askExact) {
        AlertDialog(
            onDismissRequest = { askExact = false },
            title = { Text("建议开启“闹钟和提醒”权限") },
            text = { Text("未开启时，系统可能把播报推迟几分钟。开启后定时误差在几秒内。") },
            confirmButton = {
                TextButton(onClick = { askExact = false; Permissions.openExactAlarm(ctx) }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askExact = false
                    pendingTask?.let(onCreate)
                    pendingTask = null
                }) { Text("仍然创建") }
            },
        )
    }
}

fun describeInterval(sec: Int): String = when {
    sec % 3600 == 0 -> "${sec / 3600} 小时"
    sec % 60 == 0 -> "${sec / 60} 分钟"
    else -> "$sec 秒"
}
