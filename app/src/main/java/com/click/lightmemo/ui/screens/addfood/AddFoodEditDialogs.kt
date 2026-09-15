package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.viewmodel.NewComponentId
import com.click.lightmemo.viewmodel.newFoodComponent
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.ComponentDatabaseOverlay
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.ManualDraftSnapshot
import java.time.LocalDate
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NumberInputDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    initial: String,
    allowDecimal: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(show, initial) { mutableStateOf(initial) }
    AnimatedOverlayDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = draft,
                onValueChange = { value ->
                    draft = value
                },
                singleLine = true,
                colors = dialogFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(draft) },
                    enabled = draft.toDoubleOrNull()?.let { it.isFinite() && it >= 0 && (allowDecimal || it % 1.0 == 0.0) } == true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}


@Composable
fun MealDatePickerOverlay(
    show: Boolean,
    date: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var year by remember(date) { mutableStateOf(date.year) }
    var month by remember(date) { mutableStateOf(date.monthValue) }
    var day by remember(date) { mutableStateOf(date.dayOfMonth) }
    val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(maxDay) { if (day > maxDay) day = maxDay }

    AnimatedOverlayDialog(show = show, title = "选择日期", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(
                    value = year,
                    onValueChange = { year = it },
                    range = 2020..LocalDate.now().year + 1,
                    label = { "${it}年" },
                    visibleItemCount = 3,
                    modifier = Modifier.weight(1.25f),
                )
                NumberPicker(
                    value = month,
                    onValueChange = { month = it },
                    range = 1..12,
                    label = { "${it}月" },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = day,
                    onValueChange = { day = it },
                    range = 1..maxDay,
                    label = { "${it}日" },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(LocalDate.of(year, month, day)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}


@Composable
fun MealTimePickerOverlay(
    show: Boolean,
    minuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var hour by remember(minuteOfDay) { mutableStateOf(minuteOfDay / 60) }
    var minute by remember(minuteOfDay) { mutableStateOf(minuteOfDay % 60) }

    AnimatedOverlayDialog(show = show, title = "选择时间", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 0..23,
                    label = { "%02d".format(it) },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = minute,
                    onValueChange = { minute = it },
                    range = 0..59,
                    label = { "%02d".format(it) },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                onClear?.let {
                    Button(onClick = it, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                        Text("清除")
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(hour * 60 + minute) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

