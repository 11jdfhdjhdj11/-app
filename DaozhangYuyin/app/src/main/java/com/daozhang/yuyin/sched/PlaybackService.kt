package com.daozhang.yuyin.sched

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daozhang.yuyin.R
import com.daozhang.yuyin.audio.Announcer
import com.daozhang.yuyin.core.AmountSpeller
import com.daozhang.yuyin.data.AppDb
import com.daozhang.yuyin.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 前台服务：只在存在运行中的定时/循环任务时存在。
 * 常驻通知显示下一次播报，并提供“暂停全部”。没有任务且播完后自动停止。
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idle: () -> Unit = { refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Announcer.init(this)
        Announcer.addIdleListener(idle)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须尽快进入前台
        goForeground(buildNotification("正在准备…"))
        when (intent?.action) {
            ACTION_FIRE -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_TASK_ID, -1)
                scope.launch {
                    runCatching { TaskRunner.fire(this@PlaybackService, id) }
                        .onFailure { Log.e(TAG, "fire failed", it) }
                    refresh()
                }
            }
            else -> refresh()
        }
        return START_NOT_STICKY
    }

    private fun refresh() {
        scope.launch {
            val running = AppDb.get(this@PlaybackService).dao().running()
            if (running.isEmpty()) {
                if (Announcer.isIdle) {
                    ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }
            val next = running.first()
            val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
            val text = "下次播报 ${fmt.format(Date(next.nextFireAt))} · ${AmountSpeller.formatYuan(next.amountFen)} 元" +
                if (running.size > 1) "（共 ${running.size} 个任务）" else ""
            nm().notify(NOTIF_ID, buildNotification(text))
        }
    }

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getBroadcast(
            this, 1, Intent(this, PauseAllReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("模拟播报任务运行中")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "暂停全部", pause)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        Announcer.removeIdleListener(idle)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 1001
        private const val ACTION_FIRE = "fire"
        private const val ACTION_REFRESH = "refresh"

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "播报任务", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "定时/循环播报任务运行时的常驻通知"
                        setShowBadge(false)
                    },
                )
            }
        }

        /** 闹钟触发时调用。返回 false 表示系统不允许此时启动前台服务。 */
        fun tryFire(ctx: Context, taskId: Long): Boolean = try {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, PlaybackService::class.java)
                    .setAction(ACTION_FIRE)
                    .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId),
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService refused", e)
            false
        }

        /** 任务变化后刷新通知 / 启停服务。界面在前台时调用。 */
        fun refresh(ctx: Context) {
            try {
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, PlaybackService::class.java).setAction(ACTION_REFRESH),
                )
            } catch (e: Exception) {
                Log.w(TAG, "refresh refused", e)
            }
        }
    }
}
