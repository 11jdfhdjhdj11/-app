package com.daozhang.yuyin.sched

import android.content.Context
import com.daozhang.yuyin.data.AppDb
import com.daozhang.yuyin.data.BroadcastTask
import com.daozhang.yuyin.data.Mode
import com.daozhang.yuyin.data.Status

/** 界面与通知对任务的所有写操作都走这里，保证“数据库 + 闹钟 + 前台服务”三者一致 */
object TaskActions {

    suspend fun create(ctx: Context, t: BroadcastTask): Long {
        val dao = AppDb.get(ctx).dao()
        val id = dao.insert(t)
        Scheduler.schedule(ctx, t.copy(id = id))
        PlaybackService.refresh(ctx)
        return id
    }

    suspend fun pause(ctx: Context, id: Long) {
        val dao = AppDb.get(ctx).dao()
        val t = dao.get(id) ?: return
        if (t.status != Status.RUNNING) return
        val u = t.copy(status = Status.PAUSED)
        dao.update(u)
        Scheduler.cancel(ctx, id)
        PlaybackService.refresh(ctx)
    }

    suspend fun resume(ctx: Context, id: Long) {
        val dao = AppDb.get(ctx).dao()
        val t = dao.get(id) ?: return
        if (t.status != Status.PAUSED) return
        val now = System.currentTimeMillis()
        val u = when {
            t.nextFireAt > now -> t.copy(status = Status.RUNNING)
            t.mode == Mode.INTERVAL -> t.copy(status = Status.RUNNING, nextFireAt = Scheduler.nextIntervalFire(t, now))
            else -> t.copy(status = Status.MISSED) // 定点时间已过
        }
        dao.update(u)
        Scheduler.schedule(ctx, u)
        PlaybackService.refresh(ctx)
    }

    suspend fun delete(ctx: Context, id: Long) {
        Scheduler.cancel(ctx, id)
        AppDb.get(ctx).dao().delete(id)
        PlaybackService.refresh(ctx)
    }

    suspend fun clearAll(ctx: Context) {
        val dao = AppDb.get(ctx).dao()
        dao.allIds().forEach { Scheduler.cancel(ctx, it) }
        dao.deleteAll()
        dao.clearLogs()
        PlaybackService.refresh(ctx)
    }

    suspend fun pauseAll(ctx: Context) {
        val dao = AppDb.get(ctx).dao()
        for (t in dao.running()) {
            dao.update(t.copy(status = Status.PAUSED))
            Scheduler.cancel(ctx, t.id)
        }
        PlaybackService.refresh(ctx)
    }
}
