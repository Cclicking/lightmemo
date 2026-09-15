package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.QuantityMode
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
internal fun ManualEntryContent(
    padding: PaddingValues,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    initialName: String,
    initialGrams: Double? = null,
    configured: Boolean,
    recognizing: Boolean,
    recognitionStage: RecognitionStage?,
    error: String?,
    quantityMode: QuantityMode,
    onQuantityModeChange: (QuantityMode) -> Unit,
    portionCount: Double,
    onPortionCountChange: (Double) -> Unit,
    estimatedGrams: Double?,
    onRecognizeGrams: (String, Double) -> Unit,
    onRecognizePortions: (String, Double) -> Unit,
    onOpenManualEdit: (name: String, grams: Double?) -> Unit,
    onHasContentChange: (Boolean) -> Unit,
    canRetryRecognition: Boolean = false,
    onRetryRecognition: () -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var grams by rememberSaveable { mutableStateOf(initialGrams?.formatInput() ?: "100") }
    var showPortionDialog by remember { mutableStateOf(false) }
    var showGramsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(estimatedGrams) {
        estimatedGrams?.let { grams = it.formatInput() }
    }
    LaunchedEffect(name) {
        onHasContentChange(name.isNotBlank())
    }

    val effectiveGrams = when (quantityMode) {
        QuantityMode.GRAMS -> grams.toDoubleOrNull() ?: Double.NaN
        QuantityMode.PORTIONS -> if (estimatedGrams != null) grams.toDoubleOrNull() ?: Double.NaN else Double.NaN
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .verticalScroll(rememberScrollState())
            .overScrollVertical()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MealTypeSelector(mealType, onMealType)

        if (recognizing) RecognitionStatusCard(recognitionStage)

        TextField(
            value = name,
            onValueChange = { name = it },
            label = "食物名称",
            singleLine = true,
            colors = sheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column {
                DropdownPref(
                    title = "计量方式",
                    summary = if (quantityMode == QuantityMode.GRAMS) "按克重录入" else "用大模型估计重量",
                    items = QuantityMode.entries.map { it.label },
                    selectedIndex = QuantityMode.entries.indexOf(quantityMode).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        QuantityMode.entries.getOrNull(index)?.let(onQuantityModeChange)
                    },
                )

                if (quantityMode == QuantityMode.GRAMS) {
                    ArrowPreference(
                        title = "克数 g",
                        endActions = {
                            Text(
                                text = grams,
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { showGramsDialog = true },
                    )
                } else {
                    ArrowPreference(
                        title = "份数",
                        summary = estimatedGrams?.let { "估计 ${it.formatInput()} g" } ?: "识别时用大模型估重",
                        endActions = {
                            Text(
                                text = "${portionCount.formatInput()} 份",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { showPortionDialog = true },
                    )
                    if (estimatedGrams != null) {
                        ArrowPreference(
                            title = "克数 g",
                            summary = "由份数估计，可手动调整",
                            endActions = {
                                Text(
                                    text = grams,
                                    fontSize = 14.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { showGramsDialog = true },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val n = name.trim()
                if (n.isEmpty()) return@Button
                when (quantityMode) {
                    QuantityMode.GRAMS -> onRecognizeGrams(n, effectiveGrams)
                    QuantityMode.PORTIONS -> onRecognizePortions(n, portionCount)
                }
            },
            enabled = configured && !recognizing && name.isNotBlank() &&
                (quantityMode == QuantityMode.PORTIONS || (effectiveGrams.isFinite() && effectiveGrams > 0)),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(if (recognizing) "正在识别营养..." else "自动识别热量与营养")
        }

        MethodButton(
            icon = { Icon(MiuixIcons.Os4.Edit, contentDescription = null) },
            label = "手动录入",
            enabled = !recognizing,
            onClick = {
                val n = name.trim()
                val g = grams.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                    ?: estimatedGrams
                onOpenManualEdit(n, g)
            },
        )

        if (!configured) {
            Text(
                text = "自动识别需要先在「我的 → 识别 API」配置服务；也可以手动录入填写营养。",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        RecognitionErrorRow(
            error = error,
            canRetry = canRetryRecognition,
            onRetry = onRetryRecognition,
        )
    }

    NumberInputDialog(
        show = showGramsDialog,
        title = "克数",
        summary = "输入可食用重量",
        initial = grams,
        onDismiss = { showGramsDialog = false },
        onConfirm = { grams = it; showGramsDialog = false },
    )
    NumberInputDialog(
        show = showPortionDialog,
        title = "份数",
        summary = "例如 1 碗、2 份",
        initial = portionCount.formatInput(),
        allowDecimal = true,
        onDismiss = { showPortionDialog = false },
        onConfirm = { value ->
            value.toDoubleOrNull()?.let(onPortionCountChange)
            showPortionDialog = false
        },
    )
}

