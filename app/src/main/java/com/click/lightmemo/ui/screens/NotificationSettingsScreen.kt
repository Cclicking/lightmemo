package com.click.lightmemo.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun NotificationSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsState()
    var refreshToken by remember { mutableIntStateOf(0) }
    var activeMeal by remember { mutableStateOf<MealReminderKind?>(null) }
    val notificationSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationGranted = remember(refreshToken, notificationSupported) {
        !notificationSupported || isNotificationGranted(context)
    }
    val exactAlarmSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val exactAlarmGranted = remember(refreshToken, exactAlarmSupported) {
        !exactAlarmSupported || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshToken, settings.mealRemindersEnabled, exactAlarmGranted) {
        if (settings.mealRemindersEnabled) viewModel.refreshMealReminderSchedule()
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshToken++
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun requestNotificationPermission() {
        if (!notificationSupported) return
        if (notificationGranted) openAppSettings()
        else requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openExactAlarmSettings() {
        if (!exactAlarmSupported) return
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun setMealRemindersEnabled(value: Boolean) {
        viewModel.setMealRemindersEnabled(value)
        if (value && exactAlarmSupported && !exactAlarmGranted) openExactAlarmSettings()
    }

    MealTimePickerOverlay(
        show = activeMeal != null,
        minuteOfDay = activeMeal?.let { kind ->
            when (kind) {
                MealReminderKind.Breakfast -> settings.breakfastReminderMinute
                MealReminderKind.Lunch -> settings.lunchReminderMinute
                MealReminderKind.Dinner -> settings.dinnerReminderMinute
            }
        } ?: settings.breakfastReminderMinute,
        onDismiss = { activeMeal = null },
        onConfirm = { minute ->
            when (activeMeal) {
                MealReminderKind.Breakfast -> viewModel.setBreakfastReminderMinute(minute)
                MealReminderKind.Lunch -> viewModel.setLunchReminderMinute(minute)
                MealReminderKind.Dinner -> viewModel.setDinnerReminderMinute(minute)
                null -> Unit
            }
            activeMeal = null
        },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
    ) {
        item {
            SmallTitle(
                text = "通知权限",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "允许通知",
                    summary = when {
                        !notificationSupported -> "当前 Android 版本不需要单独申请"
                        notificationGranted -> "已允许通知，可正常接收三餐提醒"
                        else -> "允许后才能接收三餐记录提醒，点击申请"
                    },
                    endActions = {
                        Text(
                            text = when {
                                !notificationSupported -> "系统管理"
                                notificationGranted -> "已允许"
                                else -> "未允许"
                            },
                            fontSize = 14.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onClick = ::requestNotificationPermission,
                )
            }
            Text(
                text = "如果系统不再弹窗，请点击上方入口到系统设置中开启通知。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(8.dp))
            SmallTitle(
                text = "三餐提醒",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                SwitchPreference(
                    checked = settings.mealRemindersEnabled,
                    onCheckedChange = ::setMealRemindersEnabled,
                    title = "三餐记录提醒",
                    summary = if (settings.mealRemindersEnabled) {
                        when {
                            !notificationGranted -> "已开启提醒，但需要先允许通知权限"
                            !exactAlarmGranted -> "已开启提醒，但需要允许精确闹钟才能准时提醒"
                            else -> "每天按下方时间提醒记录一餐"
                        }
                    } else {
                        "关闭后不会在早餐、午餐和晚餐时间发送通知"
                    },
                )
                ReminderTimePreference(
                    title = "早餐",
                    minuteOfDay = settings.breakfastReminderMinute,
                    onClick = { activeMeal = MealReminderKind.Breakfast },
                )
                ReminderTimePreference(
                    title = "午餐",
                    minuteOfDay = settings.lunchReminderMinute,
                    onClick = { activeMeal = MealReminderKind.Lunch },
                )
                ReminderTimePreference(
                    title = "晚餐",
                    minuteOfDay = settings.dinnerReminderMinute,
                    onClick = { activeMeal = MealReminderKind.Dinner },
                )
            }
        }
    }
}

private enum class MealReminderKind {
    Breakfast,
    Lunch,
    Dinner,
}

@Composable
private fun ReminderTimePreference(
    title: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = "每天提醒时间",
        endActions = {
            Text(
                text = formatReminderTime(minuteOfDay),
                fontSize = 14.5.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = onClick,
    )
}

private fun formatReminderTime(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

private fun isNotificationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
