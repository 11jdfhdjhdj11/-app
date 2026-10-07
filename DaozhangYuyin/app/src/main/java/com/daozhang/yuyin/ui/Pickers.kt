package com.daozhang.yuyin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 选择日期 + 时分秒。value 为本地时间毫秒。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val cal = Calendar.getInstance().apply { timeInMillis = value }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showDate = true }) {
            Text(SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(value)))
        }
        OutlinedButton(onClick = { showTime = true }) {
            Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(value)))
        }
        SecondsStepper(cal.get(Calendar.SECOND)) { s ->
            onChange(Calendar.getInstance().apply { timeInMillis = value; set(Calendar.SECOND, s) }.timeInMillis)
        }
    }

    if (showDate) {
        // DatePicker 使用 UTC 零点表示所选日期
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            val now = Calendar.getInstance()
            clear()
            set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val state = rememberDatePickerState(
            initialSelectedDateMillis = utc.timeInMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { sel ->
                        val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = sel }
                        val local = Calendar.getInstance().apply {
                            timeInMillis = value
                            set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
                        }
                        onChange(local.timeInMillis)
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }

    if (showTime) {
        TimeDialog(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), onDismiss = { showTime = false }) { h, m ->
            onChange(Calendar.getInstance().apply {
                timeInMillis = value
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
            }.timeInMillis)
            showTime = false
        }
    }
}

/** 选择一天中的时刻，value 为分钟数 */
@Composable
fun MinuteOfDayButton(label: String, minutes: Int, onChange: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }) {
        Text("$label %02d:%02d".format(minutes / 60, minutes % 60))
    }
    if (show) {
        TimeDialog(minutes / 60, minutes % 60, onDismiss = { show = false }) { h, m ->
            onChange(h * 60 + m)
            show = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = { TimePicker(state) },
    )
}

@Composable
private fun SecondsStepper(seconds: Int, onChange: (Int) -> Unit) {
    OutlinedButton(onClick = { onChange((seconds + 10) % 60) }) {
        Text("%02d 秒".format(seconds))
    }
}
