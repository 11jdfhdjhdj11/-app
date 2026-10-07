package com.daozhang.yuyin.sched

import android.content.Context
import android.os.PowerManager
import com.daozhang.yuyin.audio.Announcer
import com.daozhang.yuyin.data.AppDb
import com.daozhang.yuyin.data.Mode
import com.daozhang.yuyin.data.PlayLog
import com.daozhang.yuyin.data.Settings
import com.daozhang.yuyin.data.Status
import kotlinx.coroutines.delay

/** 闹钟触发后的处理：判断开关/静默时段 → 播报 → 记日志 → 排下一次 */
object TaskRunner {

    suspend fun fire(ctx: Context, taskId: Long) {
        val app = ctx.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "daozhang:fire").apply { acquire(60_000) }
        try {
            Announcer.init(app)
            val dao = AppDb.get(app).dao()
            val t = dao.get(taskId) ?: return
            if (t.status != Status.RUNNING) return
            val now = System.currentTimeMillis()
            val settings = Settings(app)

            val skipReason = when {
                !settings.globalEnabled -> "SKIP_OFF"
                Scheduler.inQuietHours(t, now) -> "SKIP_QUIET"
                else -> null
            }

            if (skipReason == null) {
                Announcer.enqueue(t.speechText, t.playTimes)
                dao.insertLog(PlayLog(taskId = t.id, speechText = t.speechText, result = "OK"))
            } else {
                dao.insertLog(PlayLog(taskId = t.id, speechText = t.speechText, result = skipReason))
            }

            val updated = when (t.mode) {
                Mode.AT -> t.copy(status = Status.DONE)
                else -> {
                    // 跳过的不计入已播次数
                    val done = t.repeatDone + if (skipReason == null) 1 else 0
                    if (t.repeatTotal in 1..done) t.copy(repeatDone = done, status = Status.DONE)
                    else t.copy(repeatDone = done, nextFireAt = Scheduler.nextIntervalFire(t, now))
                }
            }
            dao.update(updated)
            Scheduler.schedule(app, updated)
            dao.pruneLogs(now - 30L * 24 * 3600 * 1000)

            // 播放是异步的：持有唤醒锁直到播完，避免息屏时 CPU 休眠导致卡顿
            val deadline = System.currentTimeMillis() + 45_000
            while (!Announcer.isIdle && System.currentTimeMillis() < deadline) delay(200)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }
}
