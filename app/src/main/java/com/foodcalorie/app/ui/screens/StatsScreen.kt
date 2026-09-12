package com.foodcalorie.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.ui.components.GlassCard
import com.foodcalorie.app.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    backdrop: Backdrop? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val dayLabel = DateTimeFormatter.ofPattern("M/d")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "摄入统计", style = MiuixTheme.textStyles.title2)
        }

        item {
            val selected = if (state.rangeDays == 7) 0 else 1
            TabRow(
                tabs = listOf("近 7 天", "近 30 天"),
                selectedTabIndex = selected,
                onTabSelected = { index ->
                    viewModel.setRange(if (index == 0) 7 else 30)
                },
            )
        }

        item {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "日均热量", style = MiuixTheme.textStyles.subtitle)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.averageKcal.toInt()} kcal",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "有记录 ${state.daily.size} 天 · 达标 ${state.daysHitTarget} 天 · 峰值 ${state.maxKcal.toInt()} kcal",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "每日热量", style = MiuixTheme.textStyles.subtitle)
                    Spacer(Modifier.height(12.dp))
                    if (!state.hasData) {
                        Text(
                            text = "暂无数据，记录几餐后这里会出现趋势",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        WeeklyBars(
                            days = state.daily.map { it.dateEpochDay to it.total.caloriesKcal },
                            rangeDays = state.rangeDays,
                            maxKcal = state.maxKcal.coerceAtLeast(1.0),
                            labeler = dayLabel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBars(
    days: List<Pair<Long, Double>>,
    rangeDays: Int,
    maxKcal: Double,
    labeler: DateTimeFormatter,
) {
    val today = LocalDate.now()
    val map = days.toMap()
    val columns = (0 until rangeDays).map { offset ->
        val date = today.minusDays((rangeDays - 1 - offset).toLong())
        date to (map[date.toEpochDay()] ?: 0.0)
    }
    val barColor = Color(0xFF34C759)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        columns.forEach { (date, kcal) ->
            val ratio = (kcal / maxKcal).toFloat().coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((120 * ratio).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (kcal <= 0.0) Color.Transparent else barColor),
                )
                if (rangeDays <= 7) {
                    Text(
                        text = date.format(labeler),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    if (rangeDays > 7) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "共 ${rangeDays} 天 · 竖柱越高当日摄入越多",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
