package com.click.lightmemo.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.click.lightmemo.data.GallerySaveLocation
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun PermissionManagementScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    onNotificationClick: () -> Unit,
    onHealthConnectClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsState()
    var refreshToken by remember { mutableIntStateOf(0) }
    val photoCacheBytes by viewModel.photoCacheBytes.collectAsState()
    val clearingPhotoCache by viewModel.clearingPhotoCache.collectAsState()
    var showClearPhotoCacheConfirm by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshToken) {
        viewModel.refreshPhotoCacheSize()
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

    AnimatedOverlayDialog(
        show = showClearPhotoCacheConfirm,
        title = "清理照片缓存？",
        summary = "将删除应用内的临时拍摄文件和本地照片。饮食记录数据与系统相册不受影响。",
        onDismissRequest = { showClearPhotoCacheConfirm = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { showClearPhotoCacheConfirm = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    showClearPhotoCacheConfirm = false
                    viewModel.clearPhotoCache { freedBytes ->
                        Toast.makeText(
                            context,
                            "已清理 ${formatPhotoCacheBytes(freedBytes)} 照片缓存",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = !clearingPhotoCache,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("清理")
            }
        }
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
                )
                DropdownPref(
                    title = "保存位置",
                    items = GallerySaveLocation.entries.map { it.label },
                    selectedIndex = GallerySaveLocation.entries.indexOf(settings.gallerySaveLocation)
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        GallerySaveLocation.entries.getOrNull(index)?.let(viewModel::setGallerySaveLocation)
                    },
                    enabled = settings.savePhotosToGallery,
                )
                ArrowPreference(
                    title = "清理照片缓存",
                    summary = photoCacheSummary(photoCacheBytes, clearingPhotoCache),
                    enabled = !clearingPhotoCache,
                    onClick = { showClearPhotoCacheConfirm = true },
                )
            }

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
                    status = if (cameraGranted) "已允许" else "未允许",
                    onClick = {
                        if (cameraGranted) openAppSettings()
                        else requestPermission(Manifest.permission.CAMERA)
                    },
                )
                PermissionStatusRow(
                    title = "通知",
                    status = when {
                        !notificationSupported -> "系统管理"
                        notificationGranted -> "已允许"
                        else -> "未允许"
                    },
                    onClick = onNotificationClick,
                )
                ArrowPreference(
                    title = "Health Connect",
                    onClick = onHealthConnectClick,
                )
            }
        }
        item {
            Button(
                onClick = ::openAppSettings,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text("打开系统权限设置")
            }
        }
    }
}

private fun photoCacheSummary(bytes: Long?, clearing: Boolean): String = when {
    clearing -> "正在清理…"
    bytes == null -> "正在统计…"
    bytes <= 0L -> "0 B"
    else -> formatPhotoCacheBytes(bytes)
}

private fun formatPhotoCacheBytes(bytes: Long): String {
    if (bytes < 1024L) return "${bytes} B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

@Composable
private fun PermissionStatusRow(
    title: String,
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
            endActions = endActions,
        )
    } else {
        ArrowPreference(
            title = title,
            endActions = endActions,
            onClick = onClick,
        )
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
