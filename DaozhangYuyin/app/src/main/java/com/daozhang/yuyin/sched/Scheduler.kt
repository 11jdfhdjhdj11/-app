package com.daozhang.yuyin.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.daozhang.yuyin.data.BroadcastTask
import com.daozhang.yuyin.data.Mode
import com.daozhang.yuyin.data.Status
import java.util.Calendar

/** 基于 AlarmManager 的调度。每次触发后只排下一次，不一次性排满。 */
object Scheduler {

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return am(ctx).canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, t: BroadcastTask) {
        if (t.status != Status.RUNNING) {
            cancel(ctx, t.id)
            return
        }
        val pi = pending(ctx, t.id)
        val am = am(ctx)
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.nextFireAt, pi)
        } else {
            // 未授予精确闹钟：降级，可能延迟数分钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.nextFireAt, pi)
        }
    }

    fun cancel(ctx: Context, taskId: Long) {
        am(ctx).cancel(pending(ctx, taskId))
    }

    private fun pending(ctx: Context, taskId: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            ctx, taskId.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ---------- 纯计算 ----------

    /** 循环任务：严格晚于 now 的下一个触发点（按原节拍对齐，不漂移） */
    fun nextIntervalFire(t: BroadcastTask, now: Long): Long {
        val step = t.intervalSec * 1000L
        var next = t.nextFireAt + step
        if (next <= now) {
            val k = (now - t.nextFireAt) / step + 1
            next = t.nextFireAt + k * step
        }
        return next
    }

    fun inQuietHours(t: BroadcastTask, time: Long): Boolean {
        val s = t.quietStartMin
        val e = t.quietEndMin
        if (s < 0 || e < 0 || s == e) return false
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (s < e) m in s until e else (m >= s || m < e)
    }

    /** 开机或升级后，把过期的任务整理好 */
    fun reconcileAfterBoot(t: BroadcastTask, now: Long): BroadcastTask {
        if (t.status != Status.RUNNING || t.nextFireAt > now) return t
        return when (t.mode) {
            Mode.AT -> t.copy(status = Status.MISSED)
            else -> t.copy(nextFireAt = nextIntervalFire(t, now))
        }
    }
}
