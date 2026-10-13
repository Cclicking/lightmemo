package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.click.lightmemo.viewmodel.AddFoodUiState
import com.click.lightmemo.network.FoodChatQuestion
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FoodChatContent(
    padding: PaddingValues,
    state: AddFoodUiState,
    configured: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onOption: (String) -> Unit,
    onSupplement: () -> Unit,
    onRetry: () -> Unit,
    onRecognize: () -> Unit,
) {
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val keyboardBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // 浮层尺寸固定，只在内容内部避开键盘；偏移量由页面宿主一并传入。
    val safeBottom = maxOf(navigationBottom, keyboardBottom)
    val extraItems = (if (!configured) 1 else 0) + (if (state.chatSending) 1 else 0) +
        (if (state.chatError != null) 1 else 0) + (if (state.chatSummary.isNotBlank()) 1 else 0) +
        (if (state.chatReady && state.chatError == null) 1 else 0)
    val itemCount = state.chatMessages.size + extraItems
    LaunchedEffect(itemCount) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it == itemCount }
        if (state.chatMessages.isNotEmpty()) {
            val target = if (state.chatMessages.last().question != null && !state.chatSending && state.chatError == null)
                state.chatMessages.lastIndex + (if (!configured) 1 else 0) else itemCount - 1
            listState.animateScrollToItem(target)
        }
    }
    LaunchedEffect(state.chatAddingFood) {
        if (state.chatAddingFood) inputFocus.requestFocus()
    }
    Column(
        modifier = Modifier.fillMaxWidth().height(AddFoodSheetHeight)
            .padding(start = 16.dp, end = 16.dp,
                bottom = padding.calculateBottomPadding() + safeBottom + 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // 保持列表视口延伸到标题背后，让内容滚动穿过完整的渐变模糊。
                contentPadding = PaddingValues(top = 96.dp, bottom = 8.dp),
            ) {
                if (!configured) item {
                    Text("请先到「我的 → 识别 API」配置服务，再开始对话。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                itemsIndexed(state.chatMessages) { index, message ->
                    ChatBubble(
                        message.text, message.fromUser,
                        question = message.question,
                        optionsEnabled = index == state.chatMessages.lastIndex && !state.chatSending &&
                            state.chatError == null && state.chatInput.isBlank(),
                        onOption = onOption,
                        onCustomInput = {
                            inputFocus.requestFocus()
                            keyboard?.show()
                        },
                    )
                }
                if (state.chatSending) item {
                    Text("正在整理餐食…", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                state.chatError?.let { error ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error, color = MiuixTheme.colorScheme.error)
                            if (state.chatMessages.lastOrNull()?.fromUser == true) {
                                Button(onClick = onRetry, enabled = !state.chatSending,
                                    colors = ButtonDefaults.buttonColorsPrimary()) { Text("重试回复") }
                            }
                        }
                    }
                }
                if (state.chatSummary.isNotBlank()) item {
                    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp,
                        insideMargin = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("本餐草稿", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                            Text(state.chatSummary, style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
                if (state.chatReady && state.chatError == null) item {
                    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp,
                        insideMargin = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("还有没有要补充的食物？", style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium)
                            Button(onClick = onSupplement, enabled = !state.chatSending,
                                modifier = Modifier.fillMaxWidth()) { Text("补充食物") }
                            Button(onClick = onRecognize,
                                enabled = configured && !state.chatSending && state.chatInput.isBlank(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                modifier = Modifier.fillMaxWidth()) { Text("没有了，生成记录") }
                        }
                    }
                }
            }
            if (state.chatMessages.isEmpty() && state.chatInput.isBlank()) {
                Box(Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.CenterStart) {
                    Text("吃了什么？尽管说", style = MiuixTheme.textStyles.title1,
                        textAlign = TextAlign.Start)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = state.chatInput,
                onValueChange = onInputChange,
                label = if (state.chatAddingFood) "补充吃过的食物和份量" else "描述食物，或输入其他答案",
                enabled = configured && !state.chatSending,
                singleLine = false,
                maxLines = 3,
                colors = sheetFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.weight(1f).focusRequester(inputFocus),
            )
            Button(onClick = onSend,
                enabled = configured && !state.chatSending && state.chatInput.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary()) { Text("发送") }
        }
    }
}

@Composable
private fun ChatBubble(
    text: String,
    fromUser: Boolean,
    question: FoodChatQuestion? = null,
    optionsEnabled: Boolean = false,
    onOption: (String) -> Unit = {},
    onCustomInput: () -> Unit = {},
) {
    if (fromUser) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Card(modifier = Modifier.widthIn(max = maxWidth * 0.9f), cornerRadius = 20.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Text(text, style = MiuixTheme.textStyles.body1)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = MiuixTheme.textStyles.body1)
            if (question != null) {
                var selectedOption by rememberSaveable(question) { mutableStateOf<String?>(null) }
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp,
                    insideMargin = PaddingValues(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(question.prompt, style = MiuixTheme.textStyles.body1)
                        Column(Modifier.fillMaxWidth()) {
                            (question.answerOptions + "自行输入").forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                        .toggleable(value = selectedOption == option, enabled = optionsEnabled,
                                            role = Role.Checkbox, onValueChange = { checked ->
                                                selectedOption = option.takeIf { checked }
                                                if (checked && option == "自行输入") onCustomInput()
                                            })
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Checkbox(state = if (selectedOption == option) ToggleableState.On else ToggleableState.Off,
                                        onClick = null,
                                        enabled = optionsEnabled,
                                        modifier = Modifier.clearAndSetSemantics {})
                                    Text(option, style = MiuixTheme.textStyles.body1, modifier = Modifier.weight(1f),
                                        color = if (optionsEnabled) MiuixTheme.colorScheme.onSurface
                                        else MiuixTheme.colorScheme.disabledOnSurface)
                                }
                            }
                        }
                        if (optionsEnabled && selectedOption != null && selectedOption != "自行输入") {
                            Button(onClick = { selectedOption?.let(onOption) },
                                colors = ButtonDefaults.buttonColorsPrimary()) { Text("确认选择") }
                        }
                    }
                }
            }
        }
    }
}
