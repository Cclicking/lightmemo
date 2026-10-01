package com.click.lightmemo.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.DiagnosticsViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior, listState: LazyListState) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val summary = state.summary
    val totals = summary.totals
    var showClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::export)
    }
    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
    }
    AnimatedOverlayDialog(show = showClear, title = "清空诊断统计？",
        summary = "清空本机全部诊断统计，饮食记录和个人食物记忆仍会保留。",
        onDismissRequest = { if (!state.busy) showClear = false }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { showClear = false }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = { viewModel.clear(); showClear = false }, enabled = !state.busy,
                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text("清空") }
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
        state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SmallTitle("最近 30 天", modifier = Modifier.offset(x = (-16).dp))
            DiagnosticCard {
                BasicComponent(title = "统计范围", summary = "${state.endDate.minusDays(29)} 至 ${state.endDate}")
                if (state.loading) BasicComponent(title = "正在读取…")
                else if (state.error == null && totals == com.click.lightmemo.data.LocalDiagnosticsEntity(0))
                    BasicComponent(title = "暂无诊断数据", summary = "从本版本开始统计，识别或推荐操作后会更新")
            }
        }
        state.error?.let { error -> item {
            Text(error, modifier = Modifier.padding(horizontal = 16.dp))
            Button(onClick = viewModel::refresh) { Text("重试") }
        } }
        item {
            DiagnosticSection("识别与调用") {
                DiagnosticRow("识别次数", "${totals.recognitionCount} 次", "已完成和失败的任务，不包含取消或等待图片复用选择")
                DiagnosticRow("成功 / 失败", "${totals.successCount} / ${totals.failureCount}")
                DiagnosticRow("成功率", percent(summary.successRate))
                DiagnosticRow("平均耗时", summary.averageDurationMs?.let { "${decimal(it / 1000)} 秒" } ?: "—")
                DiagnosticRow("平均模型调用", summary.averageRecognitionCalls?.let { "${decimal(it)} 次 / 识别" } ?: "—")
                DiagnosticRow("模型调用总数", "${totals.llmCallCount} 次", "包含独立营养估算、提示词测试和已取消任务的实际调用")
            }
        }
        item {
            DiagnosticSection("识别路径") {
                DiagnosticRow("完整识别", "${totals.fullPathCount} 次")
                DiagnosticRow("记忆辅助", "${totals.assistedPathCount} 次")
                DiagnosticRow("加速识别", "${totals.fastPathCount} 次")
                DiagnosticRow("图片结果复用", "${totals.cacheReuseCount} 次", "复用不重复计入以上三种路径")
            }
        }
        item {
            DiagnosticSection("营养匹配") {
                DiagnosticRow("USDA 离线", "${totals.usdaOfflineHits} 次")
                DiagnosticRow("USDA 在线", "${totals.usdaOnlineHits} 次")
                DiagnosticRow("中国食物成分表", "${totals.chinaDbHits} 次")
                DiagnosticRow("AI 营养估算", "${totals.aiNutritionFallbackCount} 次")
                DiagnosticRow("未匹配到营养", "${totals.missingNutritionCount} 次", "按实际自动匹配或单项查询统计，复用固定参考和浏览候选不重复计数")
            }
        }
        item {
            DiagnosticSection("保存与修改") {
                DiagnosticRow("审核保存", "${totals.reviewSaveCount} 次", "识别结果确认保存及已有记录编辑保存")
                DiagnosticRow("发生修改", "${totals.modifiedReviewCount} 次 · ${percent(summary.modifiedRate)}")
                DiagnosticRow("名称修改", "${totals.nameCorrectionCount} 次 · ${percent(summary.reviewRate(totals.nameCorrectionCount))}")
                DiagnosticRow("重量修改", "${totals.weightCorrectionCount} 次 · ${percent(summary.reviewRate(totals.weightCorrectionCount))}")
                DiagnosticRow("组成修改", "${totals.componentCorrectionCount} 次 · ${percent(summary.reviewRate(totals.componentCorrectionCount))}")
                DiagnosticRow("营养条目更换", "${totals.foodReferenceCorrectionCount} 次 · ${percent(summary.reviewRate(totals.foodReferenceCorrectionCount))}")
                DiagnosticRow("识别结果直接接受", "${totals.directAcceptanceCount} 次", "一次保存可涉及多种修改，分类比例无需相加为 100%")
            }
        }
        item {
            DiagnosticSection("推荐操作") {
                DiagnosticRow("打开推荐页", "${totals.recommendationOpenCount} 次")
                DiagnosticRow("完成抽选", "${totals.recommendationDrawCount} 次")
                DiagnosticRow("喜欢", "${totals.recommendationLikeCount} 次")
                DiagnosticRow("换一个", "${totals.recommendationSkipCount} 次")
                DiagnosticRow("确认记录", "${totals.recommendationAcceptCount} 次")
            }
        }
        item {
            DiagnosticSection("诊断管理") {
                BasicComponent(title = "导出诊断信息", summary = "导出最近 30 天的数值统计 JSON",
                    enabled = !state.busy && !state.loading && state.error == null,
                    onClick = { export.launch("LightMemo-diagnostics-${state.endDate}.json") })
                BasicComponent(title = "清空诊断统计", summary = "清空全部日期的本地统计", enabled = !state.busy,
                    endActions = {
                        IconButton(onClick = { showClear = true }, enabled = !state.busy, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.Os4.Delete, contentDescription = "清空诊断统计", modifier = Modifier.size(24.dp))
                        }
                    })
            }
        }
        item { Text("仅保存在本机，不自动上传。导出文件只包含数值，不含 API 密钥、照片、提示词或食物名称。",
            style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable private fun DiagnosticSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(4.dp))
    SmallTitle(title, modifier = Modifier.offset(x = (-16).dp))
    DiagnosticCard(content)
}
@Composable private fun DiagnosticCard(content: @Composable () -> Unit) {
    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) { content() }
}
@Composable private fun DiagnosticRow(title: String, value: String, summary: String? = null) {
    BasicComponent(title = title, summary = summary, endActions = {
        Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    })
}
private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
private fun percent(value: Double?): String = value?.let { "${decimal(it)}%" } ?: "—"
