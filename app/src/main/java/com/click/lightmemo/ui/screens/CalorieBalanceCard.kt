package com.click.lightmemo.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.click.lightmemo.domain.CalorieBalanceDay
import com.click.lightmemo.domain.calorieBalanceDays
import com.click.lightmemo.health.CalorieExpenditureState
import com.click.lightmemo.health.HealthConnectStatus
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CalorieBalanceCard(
    state: StatsViewModel.StatsUiState,
    expenditure: CalorieExpenditureState,
    selectedEpochDay: Long,
    onSelect: (Long) -> Unit,
    onRefresh: () -> Unit,
    palette: FoodPaletteColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var actionError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        actionError = null
        onRefresh()
    }
    // Prevent displaying expenditure from the previous period during a date switch.
    val matchingPeriod = state.daily.firstOrNull()?.dateEpochDay == expenditure.periodStart?.toEpochDay() &&
        state.daily.lastOrNull()?.dateEpochDay == expenditure.periodEnd?.toEpochDay()
    val days = calorieBalanceDays(state.daily, state.loggedEpochDays,
        expenditure.dailyKcal.takeIf { matchingPeriod }.orEmpty())
    val complete = days.filter { it.dateEpochDay < LocalDate.now().toEpochDay() && it.balanceKcal != null }
    val selected = days.firstOrNull { it.dateEpochDay == selectedEpochDay }
    fun average(value: (CalorieBalanceDay) -> Double?): String =
        if (complete.isEmpty()) "—" else complete.mapNotNull(value).average().kcal()

    Card(modifier, cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
        Text("热量差", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimingMetric("日均消耗", average { it.expenditureKcal }, "kcal / 配对记录日", Modifier.weight(1f))
            TimingMetric("日均热量差", average { it.balanceKcal }, "消耗 − 摄入 · kcal", Modifier.weight(1f))
            TimingMetric("总热量差", if (complete.isEmpty()) "—" else complete.sumOf { it.balanceKcal!! }.kcal(),
                "消耗 − 摄入 · kcal", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EnergyLegend("摄入热量", palette.calorie)
            EnergyLegend("总消耗热量", palette.protein)
        }
        Spacer(Modifier.height(12.dp))
        if (days.isNotEmpty()) EnergyLineChart(days, palette.calorie, palette.protein, selectedEpochDay, onSelect)
        selected?.let { day ->
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.07f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(LocalDate.ofEpochDay(day.dateEpochDay).format(DateTimeFormatter.ofPattern("M月d日")),
                        style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.SemiBold)
                    Text("热量差 ${day.balanceKcal.kcalOrMissing()}", style = MiuixTheme.textStyles.footnote1)
                }
                Text("摄入 ${day.intakeKcal.kcalOrMissing()} · 消耗 ${day.expenditureKcal.kcalOrMissing()}",
                    style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        val message = when (expenditure.status) {
            HealthConnectStatus.LOADING -> "正在读取 Health Connect…"
            HealthConnectStatus.PERMISSION_REQUIRED -> null
            HealthConnectStatus.UPDATE_REQUIRED -> "请安装或更新 Health Connect，以读取总消耗热量。"
            HealthConnectStatus.UNAVAILABLE -> "此设备暂不支持 Health Connect。"
            HealthConnectStatus.ERROR -> "消耗热量读取失败，请重试。"
            HealthConnectStatus.AVAILABLE -> null
        }
        message?.let {
            Text(it, modifier = Modifier.padding(top = 12.dp), style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (expenditure.status in setOf(HealthConnectStatus.PERMISSION_REQUIRED, HealthConnectStatus.UPDATE_REQUIRED) ||
            (expenditure.status == HealthConnectStatus.AVAILABLE && expenditure.historyLimited && expenditure.permissions.size > 1)) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when (expenditure.status) {
                    HealthConnectStatus.PERMISSION_REQUIRED -> EnergyAction("连接 Health Connect") {
                        actionError = null
                        try { permissionLauncher.launch(expenditure.permissions) }
                        catch (_: ActivityNotFoundException) { actionError = "无法打开授权页面，请检查 Health Connect 是否可用。" }
                    }
                    HealthConnectStatus.UPDATE_REQUIRED -> EnergyAction("安装 / 更新") {
                        actionError = null
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }
                        catch (_: ActivityNotFoundException) { actionError = "无法打开安装页面。" }
                    }
                    HealthConnectStatus.AVAILABLE -> {
                        if (expenditure.historyLimited && expenditure.permissions.size > 1) EnergyAction("授权历史数据") {
                            permissionLauncher.launch(expenditure.permissions)
                        }
                    }
                    else -> Unit
                }
            }
        }
        actionError?.let { Text(it, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.error) }
    }
}

@Composable
private fun EnergyAction(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun EnergyLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun EnergyLineChart(days: List<CalorieBalanceDay>, intakeColor: Color, expenditureColor: Color,
    selectedEpochDay: Long, onSelect: (Long) -> Unit) {
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val gridColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.25f)
    val maxValue = days.flatMap { listOfNotNull(it.intakeKcal, it.expenditureKcal) }.maxOrNull() ?: 0.0
    val ceiling = (ceil(maxValue / 500.0) * 500.0).coerceAtLeast(500.0)
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(36.dp).height(130.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(ceiling, ceiling / 2, 0.0).forEach {
                Text(it.kcal(), style = MiuixTheme.textStyles.footnote2, color = labelColor)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val itemWidth = if (days.size <= 7) maxWidth / days.size else maxOf(42.dp, maxWidth / days.size)
            Column(Modifier.horizontalScroll(rememberScrollState()).width(itemWidth * days.size)) {
                Box(Modifier.fillMaxWidth().height(130.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 5.dp.toPx()
                        val plotHeight = size.height - inset * 2
                        fun point(index: Int, value: Double) = Offset(itemWidth.toPx() * (index + 0.5f),
                            inset + plotHeight * (1 - value / ceiling).toFloat())
                        listOf(0f, 0.5f, 1f).forEach { ratio ->
                            val y = inset + plotHeight * ratio
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                        fun series(color: Color, value: (CalorieBalanceDay) -> Double?) {
                            var previous: Offset? = null
                            days.forEachIndexed { index, day ->
                                val current = value(day)?.let { point(index, it) }
                                if (current != null) {
                                    previous?.let { drawLine(color, it, current, 2.dp.toPx(), StrokeCap.Round) }
                                    drawCircle(color, if (day.dateEpochDay == selectedEpochDay) 4.dp.toPx() else 2.5.dp.toPx(), current)
                                }
                                previous = current
                            }
                        }
                        series(intakeColor) { it.intakeKcal }
                        series(expenditureColor) { it.expenditureKcal }
                    }
                    Row(Modifier.fillMaxSize()) {
                        days.forEach { day ->
                            Box(Modifier.width(itemWidth).fillMaxHeight().clickable(interactionSource = null,
                                indication = null, role = Role.Button) { onSelect(day.dateEpochDay) }.semantics {
                                contentDescription = "${LocalDate.ofEpochDay(day.dateEpochDay)}，摄入 ${day.intakeKcal.kcalOrMissing()}，消耗 ${day.expenditureKcal.kcalOrMissing()}，热量差 ${day.balanceKcal.kcalOrMissing()}"
                            })
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp)) {
                    days.forEach { day ->
                        Text(LocalDate.ofEpochDay(day.dateEpochDay).format(DateTimeFormatter.ofPattern(if (days.size <= 7) "M/d" else "d")),
                            modifier = Modifier.width(itemWidth), style = MiuixTheme.textStyles.footnote2,
                            color = labelColor,
                            textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun Double.kcal(): String = String.format(Locale.ROOT, "%.0f", this)
private fun Double?.kcalOrMissing(): String = this?.let { "${it.kcal()} kcal" } ?: "—"
