package com.click.lightmemo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.secondary.SecondarySettingsActivity
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

class WidgetSettingsActivity : SecondarySettingsActivity() {
    override val pageTitle = "小组件设置"
    private var revision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
    }

    override fun onResume() { super.onResume(); revision++ }

    @Composable
    override fun PageContent(settingsVm: SettingsViewModel, backupVm: BackupViewModel,
        contentPadding: PaddingValues, scrollBehavior: SharedScrollBehavior) {
        val requestedId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val instances = remember(revision) { FoodWidgets.instances(this).filter { requestedId == AppWidgetManager.INVALID_APPWIDGET_ID || it.first == requestedId } }
        val preferences = remember { WidgetPreferences(this) }
        val scope = rememberCoroutineScope()
        var savedRevision by remember { mutableIntStateOf(0) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                    BasicComponent(title = "每个小组件独立设置", summary = "长按桌面添加小组件后，在下方选择对应实例。深浅色跟随系统，摄入图表跟随个性化配色。")
                }
            }
            if (instances.isEmpty()) item {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                    BasicComponent(title = "尚未添加小组件", summary = "在桌面长按空白处，进入小组件列表，选择轻食记的连续记录、今日摄入、饮食建议或快捷记录。")
                }
            }
            instances.forEach { (id, kind) ->
                item(key = id) {
                    SmallTitle(
                        text = "${kind.title} · #$id",
                        modifier = Modifier.offset(x = (-16).dp),
                    )
                    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                        val slots = if (kind == WidgetKind.QUICK) listOf(
                            Triple("camera", "左侧按钮", WidgetAction.CAMERA),
                            Triple("gallery", "中间按钮", WidgetAction.GALLERY),
                            Triple("manual", "右侧按钮", WidgetAction.MANUAL),
                        ) else listOf(Triple("body", "点击动作", kind.defaultAction))
                        slots.forEach { (slot, label, fallback) ->
                            val selected = remember(revision, savedRevision, id, slot) {
                                preferences.action(id, slot, fallback)
                            }
                            DropdownPref(
                                title = label,
                                items = WidgetAction.entries.map { it.title },
                                selectedIndex = WidgetAction.entries.indexOf(selected).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    WidgetAction.entries.getOrNull(index)?.let { action ->
                                        preferences.setAction(id, slot, action)
                                        savedRevision++
                                        scope.launch { FoodWidgets.updateAll(this@WidgetSettingsActivity) }
                                    }
                                },
                            )
                        }
                        if (kind == WidgetKind.SUGGESTION) BasicComponent(title = "刷新按钮", summary = "点击右侧箭头切换饮食建议")
                        if (kind == WidgetKind.STREAK) BasicComponent(title = "连续记录规则", summary = "每天至少一条记录；今天尚未记录时保留截至昨天的连续天数。热力图展示最近五周。")
                    }
                }
            }
            if (requestedId != AppWidgetManager.INVALID_APPWIDGET_ID && instances.isNotEmpty()) item {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                    ArrowPreference(title = "完成", onClick = {
                        scope.launch {
                            FoodWidgets.updateAll(this@WidgetSettingsActivity)
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, requestedId))
                            finish()
                        }
                    })
                }
            }
        }
    }
}
