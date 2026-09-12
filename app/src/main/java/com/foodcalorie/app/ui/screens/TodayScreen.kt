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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.ui.components.GlassCard
import com.foodcalorie.app.viewmodel.TodayViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.icon.os4.ChevronForward
import top.yukonga.miuix.kmp.icon.os4.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    contentPadding: PaddingValues,
    onAddClick: () -> Unit,
    backdrop: Backdrop? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val date by viewModel.date.collectAsState()
    val isToday = date == LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = viewModel::previousDay) {
                    Icon(MiuixIcons.Os4.ChevronBackward, contentDescription = "前一天")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isToday) "今天" else date.format(dateFormatter),
                        style = MiuixTheme.textStyles.title2,
                    )
                    Text(
                        text = date.format(dateFormatter),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                IconButton(onClick = viewModel::nextDay, enabled = !isToday) {
                    Icon(MiuixIcons.Os4.ChevronForward, contentDescription = "后一天")
                }
            }
        }

        item {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "热量", style = MiuixTheme.textStyles.subtitle)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = state.total.caloriesKcal.toInt().toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = " / ${state.target.toInt()} kcal",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.secondaryVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(state.progress)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34C759)),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MacroItem("蛋白质", state.total.proteinG, Color(0xFF2563EB))
                        MacroItem("碳水", state.total.carbsG, Color(0xFFF59E0B))
                        MacroItem("脂肪", state.total.fatG, Color(0xFFDB2777))
                    }
                }
            }
        }

        item {
            Button(
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("记录食物")
            }
        }

        if (state.entries.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有记录", style = MiuixTheme.textStyles.title4)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "拍照识别或手动添加一餐，开始统计热量",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        } else {
            MealType.entries.forEach { meal ->
                val mealItems = state.entries.filter { it.mealType == meal }
                if (mealItems.isNotEmpty()) {
                    item {
                        SmallTitle(text = meal.label)
                    }
                    items(mealItems, key = { it.id }) { entry ->
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MiuixTheme.textStyles.body1)
                                    Text(
                                        text = "${entry.grams.toInt()}g · 蛋 ${entry.nutrition.proteinG.toInt()}g · 碳 ${entry.nutrition.carbsG.toInt()}g · 脂 ${entry.nutrition.fatG.toInt()}g",
                                        style = MiuixTheme.textStyles.subtitle,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Text(
                                    text = "${entry.nutrition.caloriesKcal.toInt()} kcal",
                                    style = MiuixTheme.textStyles.title4,
                                )
                                Spacer(Modifier.size(4.dp))
                                IconButton(onClick = { viewModel.delete(entry.id) }) {
                                    Icon(
                                        MiuixIcons.Os4.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(18.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, grams: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.height(6.dp))
        Text(text = "${grams.toInt()}g", style = MiuixTheme.textStyles.body1)
        Text(
            text = label,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
