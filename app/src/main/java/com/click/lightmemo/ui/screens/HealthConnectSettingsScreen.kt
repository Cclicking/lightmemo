package com.click.lightmemo.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.click.lightmemo.health.HealthConnectRepository
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

private const val XiaomiHealthGuideUrl =
    "https://www.coolapk.com/feed/71630060?s=NzNjNDI0Y2YxMWY1NDZnNmFjZWViMzJ6a1661"

@Composable
fun HealthConnectSettingsScreen(
    contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior?,
    listState: LazyListState,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().overScrollVertical().then(
            if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        ),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "Health Connect 权限管理",
                    summary = "在 Health Connect 中管理总消耗热量和历史数据读取权限",
                    onClick = { openHealthConnectPermissions(context) },
                )
                ArrowPreference(
                    title = "小米运动健康指南",
                    summary = "查看小米运动健康连接 Health Connect 的方法",
                    onClick = {
                        openHealthIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(XiaomiHealthGuideUrl)))
                    },
                )
            }
        }
    }
}

private fun openHealthConnectPermissions(context: Context) {
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> {
            Toast.makeText(context, "此设备暂不支持 Health Connect", Toast.LENGTH_SHORT).show()
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
            openHealthIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/apps/details?id=${HealthConnectRepository.PROVIDER_PACKAGE}",
            )))
        }
        else -> {
            openHealthIntent(context, Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }
    }
}

private fun openHealthIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有可打开此页面的应用", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "系统不允许打开此页面，请从系统设置中进入", Toast.LENGTH_SHORT).show()
    }
}
