package com.click.lightmemo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.secondary.SecondarySettingsActivity
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

class WidgetSettingsActivity : SecondarySettingsActivity() {
    override val pageTitle = "小组件设置"
    private var revision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        revision++
    }

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        val instances = remember(revision) { FoodWidgets.instances(this) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                ) {
                    BasicComponent(
                        title = "每个小组件独立设置",
                        summary = "选择一个桌面实例进入编辑页。点击动作、圆角大小和桌面边距都会按实例保存。",
                    )
                }
            }
            if (instances.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                    ) {
                        BasicComponent(
                            title = "尚未添加小组件",
                            summary = "在桌面长按空白处，进入小组件列表，选择轻食记的连续记录、今日摄入、饮食建议或快捷记录。",
                        )
                    }
                }
            }
            instances.forEach { (id, kind) ->
                item(key = id) {
                    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                        ArrowPreference(
                            title = kind.title,
                            summary = "实例 #$id · 点击进入独立编辑页",
                            onClick = {
                                startActivity(
                                    Intent(this@WidgetSettingsActivity, WidgetEditActivity::class.java)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

class WidgetEditActivity : SecondarySettingsActivity() {
    private val widgetId: Int
        get() = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

    override val pageTitle: String
        get() = FoodWidgets.kindForId(this, widgetId)?.title ?: "编辑小组件"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
    }

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        val id = widgetId
        val kind = remember(id) { FoodWidgets.kindForId(this, id) }
        if (kind == null) {
            WidgetEditMessage(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                title = "找不到这个小组件实例",
                summary = "它可能已经从桌面移除，请返回后重新打开小组件设置。",
            )
            return
        }

        val preferences = remember { WidgetPreferences(this) }
        val scope = rememberCoroutineScope()
        var savedRevision by remember { mutableIntStateOf(0) }
        var radius by remember(id, savedRevision) {
            mutableFloatStateOf(preferences.cornerRadius(id, kind))
        }
        var marginPreset by remember(id, savedRevision) {
            mutableStateOf(preferences.marginPreset(id))
        }
        var marginTop by remember(id, savedRevision) {
            mutableFloatStateOf(preferences.marginTop(id).toFloat())
        }
        var marginBottom by remember(id, savedRevision) {
            mutableFloatStateOf(preferences.marginBottom(id).toFloat())
        }

        fun updateWidget() {
            scope.launch { FoodWidgets.updateAll(this@WidgetEditActivity) }
        }

        fun saveAndUpdate() {
            preferences.setCornerRadius(id, radius)
            if (marginPreset == WidgetMarginPreset.CUSTOM) {
                preferences.setMargins(id, marginTop.roundToInt(), marginBottom.roundToInt())
            } else {
                preferences.setMarginPreset(id, marginPreset)
            }
            updateWidget()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SmallTitle(
                    text = "点击跳转",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                ) {
                    val slots = if (kind == WidgetKind.QUICK) listOf(
                        Triple("camera", "左侧按钮", WidgetAction.CAMERA),
                        Triple("gallery", "中间按钮", WidgetAction.GALLERY),
                        Triple("manual", "右侧按钮", WidgetAction.MANUAL),
                    ) else {
                        listOf(Triple("body", "点击动作", kind.defaultAction))
                    }
                    slots.forEach { (slot, label, fallback) ->
                        val selected = remember(savedRevision, id, slot) {
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
                                    updateWidget()
                                }
                            },
                        )
                    }
                    if (kind == WidgetKind.SUGGESTION) {
                        BasicComponent(
                            title = "刷新按钮",
                            summary = "点击右侧箭头切换饮食建议。",
                        )
                    }
                    if (kind == WidgetKind.STREAK) {
                        BasicComponent(
                            title = "连续记录规则",
                            summary = "每天至少一条记录；热力图展示最近五周。",
                        )
                    }
                }
            }
            item {
                SmallTitle(
                    text = "卡片圆角",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(0.dp),
                ) {
                    BasicComponent(
                        title = "圆角大小",
                        endActions = {
                            Text(
                                text = "${radius.roundToInt()}dp",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        },
                    )
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        onValueChangeFinished = {
                            preferences.setCornerRadius(id, radius)
                            updateWidget()
                        },
                        valueRange = WidgetPreferences.MIN_RADIUS_DP..WidgetPreferences.MAX_RADIUS_DP,
                        steps = 47,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
            item {
                SmallTitle(
                    text = "桌面边距预设",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                    DropdownPref(
                        title = "边距预设",
                        items = WidgetMarginPreset.entries.map { it.title },
                        selectedIndex = marginPreset.ordinal,
                        onSelectedIndexChange = { index ->
                            val preset = WidgetMarginPreset.entries.getOrNull(index)
                            if (preset != null) {
                                preferences.setMarginPreset(id, preset)
                                marginPreset = preset
                                if (preset.topDp != null && preset.bottomDp != null) {
                                    marginTop = preset.topDp.toFloat()
                                    marginBottom = preset.bottomDp.toFloat()
                                }
                                savedRevision++
                                updateWidget()
                            }
                        },
                    )
                }
            }
            if (marginPreset == WidgetMarginPreset.CUSTOM) {
                item {
                    SmallTitle(
                        text = "自定义上下外边距",
                        modifier = Modifier.offset(x = (-16).dp),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        MarginSlider(
                            title = "顶部外边距",
                            value = marginTop,
                            onValueChange = { marginTop = it },
                            onValueChangeFinished = {
                                preferences.setMargins(id, marginTop.roundToInt(), marginBottom.roundToInt())
                                updateWidget()
                            },
                        )
                        MarginSlider(
                            title = "底部外边距",
                            value = marginBottom,
                            onValueChange = { marginBottom = it },
                            onValueChangeFinished = {
                                preferences.setMargins(id, marginTop.roundToInt(), marginBottom.roundToInt())
                                updateWidget()
                            },
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        saveAndUpdate()
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun MarginSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        BasicComponent(
            title = title,
            endActions = {
                Text(
                    text = "${value.roundToInt()}dp",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
            },
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = WidgetPreferences.MIN_MARGIN_DP.toFloat()..WidgetPreferences.MAX_MARGIN_DP.toFloat(),
            steps = WidgetPreferences.MAX_MARGIN_DP - WidgetPreferences.MIN_MARGIN_DP - 1,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun WidgetEditMessage(
    contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior,
    title: String,
    summary: String,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                BasicComponent(title = title, summary = summary)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
