package com.daozhang.yuyin.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.daozhang.yuyin.sched.Scheduler

/** 权限中心：检测状态 + 跳转到对应的系统设置页 */
object Permissions {

    fun notificationsGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun exactAlarmGranted(ctx: Context) = Scheduler.canExact(ctx)

    fun batteryUnrestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun openExactAlarm(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launch(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(ctx)))
        }
    }

    @SuppressLint("BatteryLife")
    fun openBattery(ctx: Context) {
        if (!launch(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(ctx)))) {
            launch(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openNotificationSettings(ctx: Context) {
        val ok = launch(
            ctx,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        )
        if (!ok) openAppDetails(ctx)
    }

    fun openAppDetails(ctx: Context): Boolean =
        launch(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx)))

    /** 各厂商“自启动/后台管理”页面，逐个尝试，都失败就打开应用详情 */
    fun openAutostart(ctx: Context) {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        )
        for ((p, c) in candidates) {
            if (launch(ctx, Intent().setComponent(ComponentName(p, c)))) return
        }
        openAppDetails(ctx)
    }

    /** 按厂商给出的文字指引（跳转失败时也能照着做） */
    fun vendorHint(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "小米/红米：应用设置 → 省电策略选“无限制”，并打开“自启动”。"
        "huawei" -> "华为：应用启动管理 → 关闭“自动管理”，手动打开自启动、关联启动、后台活动。"
        "honor" -> "荣耀：应用启动管理 → 关闭“自动管理”，手动打开三项开关。"
        "oppo", "oneplus", "realme" -> "OPPO/一加/realme：应用信息 → 耗电管理 → 允许后台运行、允许自启动。"
        "vivo", "iqoo" -> "vivo/iQOO：i 管家 → 应用管理 → 自启动；电池 → 后台高耗电，允许本应用。"
        else -> "在系统设置中允许本应用自启动、后台运行，并关闭电池优化。"
    }

    private fun pkg(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    private fun launch(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
