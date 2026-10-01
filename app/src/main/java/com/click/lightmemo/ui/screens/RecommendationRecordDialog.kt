package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.DishRecommendation
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.viewmodel.RecommendationOperation
import com.click.lightmemo.viewmodel.mealTypeForMinuteOfDay
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RecommendationRecordDialog(
    dish: DishRecommendation?,
    operation: RecommendationOperation,
    onDismiss: () -> Unit,
    onSave: (DishRecommendation, Double, MealType) -> Unit,
) {
    var previousDish by remember { mutableStateOf<DishRecommendation?>(null) }
    SideEffect { if (dish != null) previousDish = dish }
    val displayedDish = dish ?: previousDish
    AnimatedOverlayDialog(
        show = dish != null,
        title = "记录饮食",
        summary = displayedDish?.let { "是否将「${it.preset.name}」记录到今日饮食？" },
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            operation.message?.let { Text(it, style = MiuixTheme.textStyles.body2) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDismiss, enabled = !operation.busy, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        displayedDish?.let { selected ->
                            val now = LocalTime.now()
                            onSave(selected, selected.preset.defaultGrams, mealTypeForMinuteOfDay(now.hour * 60 + now.minute))
                        }
                    },
                    enabled = !operation.busy && dish != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(if (operation.busy) "记录中…" else "记录") }
            }
        }
    }
}
