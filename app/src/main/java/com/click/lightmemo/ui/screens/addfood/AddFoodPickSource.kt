package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.ui.utils.overScrollVertical
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.icon.os4.FavoritesFill
import top.yukonga.miuix.kmp.icon.os4.Image
import top.yukonga.miuix.kmp.icon.os4.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
internal fun PickSourceContent(
    padding: PaddingValues,
    configured: Boolean,
    recognizing: Boolean,
    recognitionStage: RecognitionStage?,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    quickInput: String,
    onQuickInputChange: (String) -> Unit,
    onQuickRecognize: () -> Unit,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    minuteOfDay: Int,
    onMinuteChange: (Int) -> Unit,
    targetDateEpochDay: Long,
    onDateChange: (LocalDate) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    error: String?,
    permissionHint: String?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
    onPreset: () -> Unit,
    canRetryRecognition: Boolean = false,
    onRetryRecognition: () -> Unit = {},
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    MealDatePickerOverlay(
        show = showDatePicker,
        date = LocalDate.ofEpochDay(targetDateEpochDay),
        onDismiss = { showDatePicker = false },
        onConfirm = {
            onDateChange(it)
            showDatePicker = false
        },
    )
    MealTimePickerOverlay(
        show = showTimePicker,
        minuteOfDay = minuteOfDay,
        onDismiss = { showTimePicker = false },
        onConfirm = {
            onMinuteChange(it)
            showTimePicker = false
        },
    )

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

        // 快速识别输入
        TextField(
            value = quickInput,
            onValueChange = onQuickInputChange,
            label = "点击输入，快速识别",
            singleLine = true,
            enabled = !recognizing,
            colors = sheetFieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { onQuickRecognize() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (recognizing) RecognitionStatusCard(recognitionStage)

        if (!configured) {
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column {
                    Text("尚未配置识别 API", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "请到「我的 → 识别 API」填写 Base URL 与 API Key。文字录入不依赖网络。",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // 本餐说明 + 时间
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("本餐说明", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                    MealTagRow(selectedTags = selectedTags, onToggle = onToggleTag)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("时间", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimePill(
                            text = LocalDate.ofEpochDay(targetDateEpochDay).format(timeFormatter),
                            onClick = { showDatePicker = true },
                        )
                        TimePill(
                            text = minuteOfDay.toLocalTime().format(clockFormatter),
                            onClick = { showTimePicker = true },
                        )
                    }
                }
            }
        }

        TextField(
            value = note,
            onValueChange = onNoteChange,
            label = "请输入备注",
            singleLine = true,
            colors = sheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (permissionHint != null) {
            Text(
                text = permissionHint,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }

        // 分隔后的录入方式：与备注更贴近
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Image, contentDescription = null) },
                label = if (configured) "拍照识别" else "拍照识别（需先配置 API）",
                enabled = configured && !recognizing,
                onClick = onCamera,
            )
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Photos, contentDescription = null) },
                label = "从相册选择",
                enabled = configured && !recognizing,
                onClick = onGallery,
            )
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Edit, contentDescription = null) },
                label = "文字录入",
                enabled = !recognizing,
                onClick = onManual,
            )
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.FavoritesFill, contentDescription = null) },
                label = "预设",
                enabled = !recognizing,
                onClick = onPreset,
            )
        }

        RecognitionErrorRow(
            error = error,
            canRetry = canRetryRecognition,
            onRetry = onRetryRecognition,
        )
    }
}


