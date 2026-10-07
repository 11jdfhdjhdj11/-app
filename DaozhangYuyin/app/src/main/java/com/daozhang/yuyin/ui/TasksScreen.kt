package com.daozhang.yuyin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.daozhang.yuyin.data.AppDb
import com.daozhang.yuyin.data.BroadcastTask
import com.daozhang.yuyin.data.Mode
import com.daozhang.yuyin.data.Settings
import com.daozhang.yuyin.data.Status
import com.daozhang.yuyin.sched.TaskActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TasksScreen(modifier: Modifier, openSettings: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val tasksFlow = remember { dao.observeAll() }
    val tasks by tasksFlow.collectAsState(initial = emptyList())
    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val countFlow = remember { dao.observePlayCountSince(startOfDay) }
    val todayCount by countFlow.collectAsState(initial = 0)
    var confirmClear by remember { mutableStateOf(false) }

    var exactOk by remember { mutableStateOf(true) }
    var globalOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        exactOk = Permissions.exactAlarmGranted(ctx)
        globalOn = Settings(ctx).globalEnabled
        onPauseOrDispose { }
    }

    fun act(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("任务", style = MaterialTheme.typography.headlineSmall)
                    Text("今日已播报 $todayCount 次", style = MaterialTheme.typography.bodyMedium)
                }
                if (tasks.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("清空") }
            }
        }
        if (!globalOn) item { Banner("全局开关已关闭，到点的任务会被跳过", "去设置", openSettings) }
        if (!exactOk) item { Banner("未开启“闹钟和提醒”权限，播报可能延迟几分钟", "去开启") { Permissions.openExactAlarm(ctx) } }
        if (tasks.isEmpty()) {
            item { Text("还没有任务。在“播报”页填好金额后点“创建任务”。", Modifier.padding(top = 24.dp)) }
        }
        items(tasks, key = { it.id }) { t ->
            TaskCard(
                t,
                onPause = { act { TaskActions.pause(ctx, t.id) } },
                onResume = { act { TaskActions.resume(ctx, t.id) } },
                onDelete = { act { TaskActions.delete(ctx, t.id) } },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有任务？") },
            text = { Text("会删除全部任务和播报记录，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; act { TaskActions.clearAll(ctx) } }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun TaskCard(t: BroadcastTask, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA) }
    val (statusText, active) = when (t.status) {
        Status.RUNNING -> "运行中" to true
        Status.PAUSED -> "已暂停" to false
        Status.MISSED -> "已错过" to false
        else -> "已完成" to false
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(statusText) })
            }
            Text("“${t.speechText}” · 每次 ${t.playTimes} 遍", style = MaterialTheme.typography.bodyMedium)
            val detail = buildString {
                if (t.mode == Mode.INTERVAL) {
                    append("已播 ${t.repeatDone}")
                    append(if (t.repeatTotal == 0) " 次（无限循环）" else " / ${t.repeatTotal} 次")
                }
                if (t.quietStartMin >= 0) {
                    if (isNotEmpty()) append(" · ")
                    append("静默 %02d:%02d–%02d:%02d".format(t.quietStartMin / 60, t.quietStartMin % 60, t.quietEndMin / 60, t.quietEndMin % 60))
                }
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
            if (active) Text("下次：${fmt.format(Date(t.nextFireAt))}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (t.status) {
                    Status.RUNNING -> TextButton(onClick = onPause) { Text("暂停") }
                    Status.PAUSED -> TextButton(onClick = onResume) { Text("继续") }
                }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
