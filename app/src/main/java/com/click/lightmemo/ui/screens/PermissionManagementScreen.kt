package com.click.lightmemo.ui.screens

import android.Manifest
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.click.lightmemo.data.GallerySaveLocation
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PermissionManagementScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsState()
    var refreshToken by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraGranted = remember(refreshToken) {
        isGranted(context, Manifest.permission.CAMERA)
    }
    val notificationSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationGranted = remember(refreshToken, notificationSupported) {
        !notificationSupported || isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
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

    fun requestPermission(permission: String) {
        requestPermissionLauncher.launch(permission)
    }

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
                text = "照片保存",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                SwitchPreference(
                    checked = settings.savePhotosToGallery,
                    onCheckedChange = viewModel::setSavePhotosToGallery,
                    title = "拍照后保存到相册",
                    summary = "关闭后，照片只保留在应用内，不写入系统相册",
                )
                DropdownPref(
                    title = "保存位置",
                    summary = if (settings.savePhotosToGallery) {
                        "下一次拍照保存到 ${settings.gallerySaveLocation.label}"
                    } else {
                        "开启上方开关后可选择相册位置"
                    },
                    items = GallerySaveLocation.entries.map { it.label },
                    selectedIndex = GallerySaveLocation.entries.indexOf(settings.gallerySaveLocation)
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        GallerySaveLocation.entries.getOrNull(index)?.let(viewModel::setGallerySaveLocation)
                    },
                    enabled = settings.savePhotosToGallery,
                )
            }
            Text(
                text = "位置选择只影响之后拍摄的照片，已有照片不会移动。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(8.dp))
            SmallTitle(
                text = "应用权限",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                PermissionStatusRow(
                    title = "相机",
                    summary = if (cameraGranted) {
                        "用于拍照识别食物"
                    } else {
                        "拍照识别需要相机权限，点击申请"
                    },
                    status = if (cameraGranted) "已允许" else "未允许",
                    onClick = {
                        if (cameraGranted) openAppSettings()
                        else requestPermission(Manifest.permission.CAMERA)
                    },
                )
                PermissionStatusRow(
                    title = "通知",
                    summary = if (!notificationSupported) {
                        "当前 Android 版本不需要单独申请"
                    } else if (notificationGranted) {
                        "用于后台识别完成提醒"
                    } else {
                        "后台识别需要通知权限，点击申请"
                    },
                    status = when {
                        !notificationSupported -> "系统管理"
                        notificationGranted -> "已允许"
                        else -> "未允许"
                    },
                    onClick = if (notificationSupported) {
                        {
                            if (notificationGranted) openAppSettings()
                            else requestPermission(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        null
                    },
                )
            }
            Text(
                text = "未允许的权限可点击申请；如果系统不再弹窗，请点击下方按钮到系统设置中开启。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Button(
                onClick = ::openAppSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开系统权限设置")
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    summary: String,
    status: String,
    onClick: (() -> Unit)? = null,
) {
    val endActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text(
            text = status,
            fontSize = 14.5.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
    if (onClick == null) {
        ArrowPreference(
            title = title,
            summary = summary,
            endActions = endActions,
        )
    } else {
        ArrowPreference(
            title = title,
            summary = summary,
            endActions = endActions,
            onClick = onClick,
        )
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
