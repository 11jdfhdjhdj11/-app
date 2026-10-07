package com.daozhang.yuyin.sched

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daozhang.yuyin.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** 闹钟到点：优先交给前台服务执行；系统不允许启动前台服务时，就地执行 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (id < 0) return
        if (PlaybackService.tryFire(ctx, id)) return

        Log.w("AlarmReceiver", "无法启动前台服务，降级为广播内执行")
        val pr = goAsync()
        receiverScope.launch {
            try {
                // 广播最多约 10 秒，留余量
                withTimeoutOrNull(9_000) { TaskRunner.fire(ctx, id) }
            } finally {
                pr.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.daozhang.yuyin.FIRE"
        const val EXTRA_TASK_ID = "task_id"
    }
}

/**
 * 开机 / 应用升级 / 精确闹钟权限变化：重新排闹钟。
 * Android 15 起开机广播不能启动 mediaPlayback 类型的前台服务，
 * 所以这里只排闹钟，等第一次触发时再由闹钟拉起服务。
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
            else -> return
        }
        val pr = goAsync()
        receiverScope.launch {
            try {
                val dao = AppDb.get(ctx).dao()
                val now = System.currentTimeMillis()
                for (t in dao.running()) {
                    val fixed = Scheduler.reconcileAfterBoot(t, now)
                    if (fixed != t) dao.update(fixed)
                    Scheduler.schedule(ctx, fixed)
                }
            } finally {
                pr.finish()
            }
        }
    }
}

/** 通知栏“暂停全部”按钮 */
class PauseAllReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pr = goAsync()
        receiverScope.launch {
            try {
                TaskActions.pauseAll(ctx)
            } finally {
                pr.finish()
            }
        }
    }
}
