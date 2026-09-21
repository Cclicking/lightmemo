package com.click.lightmemo.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.click.lightmemo.MainActivity
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

private const val CHANNEL_ID = "meal_reminders"
private const val CHANNEL_NAME = "三餐记录提醒"
private const val EXTRA_MEAL = "meal"
private const val MEAL_BREAKFAST = "breakfast"
private const val MEAL_LUNCH = "lunch"
private const val MEAL_DINNER = "dinner"
private const val REQUEST_BREAKFAST = 20_101
private const val REQUEST_LUNCH = 20_102
private const val REQUEST_DINNER = 20_103

private data class MealReminder(
    val key: String,
    val minuteOfDay: Int,
    val requestCode: Int,
)

private fun AppSettings.mealReminders(): List<MealReminder> = listOf(
    MealReminder(MEAL_BREAKFAST, breakfastReminderMinute, REQUEST_BREAKFAST),
    MealReminder(MEAL_LUNCH, lunchReminderMinute, REQUEST_LUNCH),
    MealReminder(MEAL_DINNER, dinnerReminderMinute, REQUEST_DINNER),
)

object MealReminderScheduler {
    fun schedule(context: Context, settings: AppSettings) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        settings.mealReminders().forEach { reminder ->
            alarmManager.cancel(pendingIntent(appContext, reminder))
        }
        if (!settings.mealRemindersEnabled) return

        ensureNotificationChannel(appContext)
        val now = ZonedDateTime.now()
        settings.mealReminders().forEach { reminder ->
            scheduleReminder(alarmManager, appContext, reminder, now)
        }
    }

    fun scheduleNext(context: Context, settings: AppSettings, mealKey: String) {
        if (!settings.mealRemindersEnabled) return
        val reminder = settings.mealReminders().firstOrNull { it.key == mealKey } ?: return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        scheduleReminder(alarmManager, appContext, reminder, ZonedDateTime.now())
    }

    private fun scheduleReminder(
        alarmManager: AlarmManager,
        context: Context,
        reminder: MealReminder,
        now: ZonedDateTime,
    ) {
        val triggerAt = nextTriggerAt(now, reminder.minuteOfDay)
        val operation = pendingIntent(context, reminder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Keep a best-effort fallback until the user grants the exact-alarm special access.
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                operation,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation,
            )
        }
    }

    private fun pendingIntent(context: Context, reminder: MealReminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminder.requestCode,
            Intent(context, MealReminderReceiver::class.java).apply {
                putExtra(EXTRA_MEAL, reminder.key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextTriggerAt(now: ZonedDateTime, minuteOfDay: Int): Long {
        val safeMinute = minuteOfDay.coerceIn(0, 1439)
        val candidate = now.toLocalDate()
            .atTime(safeMinute / 60, safeMinute % 60)
            .atZone(now.zone)
        return (if (candidate.isAfter(now)) candidate else candidate.plusDays(1))
            .toInstant()
            .toEpochMilli()
    }
}

internal object MealReminderNotifications {
    fun show(context: Context, meal: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = when (meal) {
            MEAL_BREAKFAST -> "早餐时间到"
            MEAL_LUNCH -> "午餐时间到"
            MEAL_DINNER -> "晚餐时间到"
            else -> return
        }
        ensureNotificationChannel(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            20_200,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.click.lightmemo.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("来轻食记快速记录你的一餐吧~")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }
}

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "提醒记录早餐、午餐和晚餐"
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(appContext).settings.first()
                when (intent?.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    -> MealReminderScheduler.schedule(appContext, settings)

                    else -> if (settings.mealRemindersEnabled) {
                        intent?.getStringExtra(EXTRA_MEAL)?.let {
                            MealReminderNotifications.show(appContext, it)
                            MealReminderScheduler.scheduleNext(appContext, settings, it)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
