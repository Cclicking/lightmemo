package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.PersonalFoodMemoryViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PersonalFoodMemoryScreen(
    viewModel: PersonalFoodMemoryViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior,
    listState: LazyListState,
) {
    val state by viewModel.uiState.collectAsState()
    var memoryToDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var clearMemories by remember { mutableStateOf(false) }

    AnimatedOverlayDialog(
        show = clearMemories || memoryToDelete != null,
        title = if (clearMemories) "清空个人食物记忆？" else "删除这条个人记忆？",
        summary = if (clearMemories) "之后识别将不再参考这些历史建议，饮食记录仍会保留。"
            else "删除「${memoryToDelete?.second.orEmpty()}」的历史建议，饮食记录仍会保留。",
        onDismissRequest = { clearMemories = false; memoryToDelete = null },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { clearMemories = false; memoryToDelete = null }, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = {
                    if (clearMemories) viewModel.clear() else memoryToDelete?.let { viewModel.delete(it.first) }
                    clearMemories = false
                    memoryToDelete = null
                },
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("确认删除") }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle(text = "记忆管理", modifier = Modifier.offset(x = (-16).dp))
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                BasicComponent(
                    title = "清空个人记忆",
                    summary = "保留饮食记录，之后重新学习",
                    enabled = !state.busy && state.memories.isNotEmpty(),
                    endActions = {
                        MemoryDeleteButton(
                            description = "清空全部个人食物记忆",
                            enabled = !state.busy && state.memories.isNotEmpty(),
                            onClick = { clearMemories = true },
                        )
                    },
                )
            }
        }
        state.error?.let { error ->
            item {
                Text(error, modifier = Modifier.padding(horizontal = 16.dp))
                Button(onClick = viewModel::refresh, enabled = !state.busy) { Text("重试") }
            }
        }
        if (state.memories.isEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SmallTitle(text = if (state.loading) "正在读取…" else "已记住的食物 · 0 条", modifier = Modifier.offset(x = (-16).dp))
                if (!state.loading && state.error == null) {
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        BasicComponent(title = "暂无食物记忆", summary = "确认保存食物后，会在这里积累历史建议")
                    }
                }
            }
        }
        itemsIndexed(state.memories, key = { _, row -> row.first }) { index, (id, memory) ->
            if (index == 0) {
                Spacer(Modifier.height(4.dp))
                SmallTitle(text = "已记住的食物 · ${state.memories.size} 条", modifier = Modifier.offset(x = (-16).dp))
            }
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                BasicComponent(
                    title = memory.canonicalName,
                    summary = buildString {
                        append("记录 ${memory.useCount} 次 · 纠正 ${memory.correctionCount} 次 · 通常 ${memory.typicalGrams.toInt()}g")
                        if (memory.aliases.isNotEmpty()) append("\n别名：${memory.aliases.joinToString("、")}")
                    },
                    enabled = !state.busy,
                    endActions = {
                        MemoryDeleteButton(
                            description = "删除${memory.canonicalName}的记忆",
                            enabled = !state.busy,
                            onClick = { memoryToDelete = id to memory.canonicalName },
                        )
                    },
                )
            }
        }
        item {
            Text(
                "仅保存在本机，个人记忆暂不包含在数据备份中。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun MemoryDeleteButton(description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = MiuixIcons.Os4.Delete,
            contentDescription = description,
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}
