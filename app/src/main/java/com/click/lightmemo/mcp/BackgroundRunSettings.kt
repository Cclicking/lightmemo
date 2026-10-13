package com.click.lightmemo.mcp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/** System settings remain user-controlled; OEM activities are optional shortcuts. */
object BackgroundRunSettings {
    fun ignoresBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun openBatteryOptimization(context: Context) {
        val request = if (ignoresBatteryOptimizations(context)) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        }
        openFirst(context, listOf(request,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(context)))
    }

    fun openAutostart(context: Context) {
        val shortcuts = if (isXiaomi()) listOf(Intent().apply {
            component = ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")
            putExtra("package_name", context.packageName)
        }) else emptyList()
        openFirst(context, shortcuts + appDetails(context),
            "请在系统设置中找到轻食记，手动允许自启动或后台自动启动。")
    }

    fun openBackgroundBattery(context: Context) {
        val shortcuts = if (isXiaomi()) listOf(Intent().apply {
            component = ComponentName("com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            putExtra("package_name", context.packageName)
            putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
        }) else emptyList()
        openFirst(context, shortcuts + appDetails(context),
            "请将轻食记的省电策略设为无限制，或允许后台活动。")
    }

    private fun isXiaomi() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        || Build.BRAND.equals("Redmi", ignoreCase = true)
        || Build.BRAND.equals("POCO", ignoreCase = true)

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")
    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun openFirst(context: Context, intents: List<Intent>, guidance: String? = null) {
        // Try the launch itself: package visibility checks can hide OEM activities.
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                guidance?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Unsupported system version: try the next settings entry.
            } catch (_: SecurityException) {
                // Some OEMs keep their settings activities private.
            }
        }
        Toast.makeText(context, "无法打开系统设置，请在手机设置中找到轻食记进行配置。", Toast.LENGTH_LONG).show()
    }
}
