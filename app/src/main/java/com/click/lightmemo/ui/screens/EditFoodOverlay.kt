package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.data.isValid
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun EditFoodOverlay(entry: FoodLog, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (FoodLog) -> Unit) {
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }
    var grams by rememberSaveable(entry.id) { mutableStateOf(entry.grams.toString()) }
    var date by rememberSaveable(entry.id) { mutableStateOf(LocalDate.ofEpochDay(entry.dateEpochDay).toString()) }
    var time by rememberSaveable(entry.id) { mutableStateOf(entry.mealMinuteOfDay?.let { LocalTime.of(it / 60, it % 60).toString() }.orEmpty()) }
    var meal by rememberSaveable(entry.id) { mutableIntStateOf(entry.mealType.ordinal) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note.orEmpty()) }
    var kcal by rememberSaveable(entry.id) { mutableStateOf(entry.nutrition.caloriesKcal.toString()) }
    var protein by rememberSaveable(entry.id) { mutableStateOf(entry.nutrition.proteinG.toString()) }
    var carbs by rememberSaveable(entry.id) { mutableStateOf(entry.nutrition.carbsG.toString()) }
    var fat by rememberSaveable(entry.id) { mutableStateOf(entry.nutrition.fatG.toString()) }
    val weight = grams.toDoubleOrNull()
    val day = runCatching { LocalDate.parse(date) }.getOrNull()
    val clock = runCatching { LocalTime.parse(time) }.getOrNull()
    val nutrition = Nutrition(kcal.toDoubleOrNull() ?: Double.NaN, protein.toDoubleOrNull() ?: Double.NaN,
        carbs.toDoubleOrNull() ?: Double.NaN, fat.toDoubleOrNull() ?: Double.NaN)
    val valid = name.isNotBlank() && weight != null && weight.isFinite() && weight > 0 &&
        day != null && (time.isBlank() || clock != null) && nutrition.isValid()
    val ratio = if (weight != null && entry.grams > 0) weight / entry.grams else 1.0
    val scaled = entry.nutrition * ratio
    val preservesComponents = nutrition == scaled

    AnimatedOverlayDialog(show = true, title = "编辑记录", onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(value = name, onValueChange = { name = it }, label = "食物名称", enabled = !busy)
            TextField(value = grams, onValueChange = { value ->
                grams = value
                value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let {
                    val updated = entry.nutrition * (it / entry.grams)
                    kcal = updated.caloriesKcal.toString(); protein = updated.proteinG.toString()
                    carbs = updated.carbsG.toString(); fat = updated.fatG.toString()
                }
            }, label = "重量 g（同步调整营养）", enabled = !busy)
            TextField(value = kcal, onValueChange = { kcal = it }, label = "热量 kcal", enabled = !busy)
            TextField(value = protein, onValueChange = { protein = it }, label = "蛋白质 g", enabled = !busy)
            TextField(value = carbs, onValueChange = { carbs = it }, label = "碳水 g", enabled = !busy)
            TextField(value = fat, onValueChange = { fat = it }, label = "脂肪 g", enabled = !busy)
            if (!preservesComponents && entry.components.isNotEmpty()) Text("手动修改营养后，该记录将使用填写的总量，并移除原估算组成。")
            TextField(value = date, onValueChange = { date = it }, label = "日期 YYYY-MM-DD", enabled = !busy)
            TextField(value = time, onValueChange = { time = it }, label = "用餐时间 HH:mm（可留空）", enabled = !busy)
            Button(onClick = { meal = (meal + 1) % MealType.entries.size }, enabled = !busy) { Text("餐次：${MealType.entries[meal].label}（点击切换）") }
            TextField(value = note, onValueChange = { note = it }, label = "备注", enabled = !busy)
            if (!valid) Text("请检查名称、日期、时间和数值；重量须大于 0，营养素须非负。")
            error?.let { Text(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = {
                    onSave(entry.copy(name = name.trim(), grams = weight!!, nutrition = nutrition,
                        dateEpochDay = day!!.toEpochDay(), mealMinuteOfDay = clock?.let { it.hour * 60 + it.minute },
                        mealType = MealType.entries[meal], note = note.trim().ifBlank { null },
                        components = if (preservesComponents) entry.components.map { it.copy(
                            estimatedWeightG = it.estimatedWeightG * ratio, weightMinG = it.weightMinG * ratio,
                            weightMaxG = it.weightMaxG * ratio,
                        ) } else emptyList()))
                }, enabled = valid && !busy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(if (busy) "保存中…" else "保存")
                }
            }
        }
    }
}
