package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.RecognitionStage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
internal fun RecognitionErrorRow(
    error: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    if (error == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = error,
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.subtitle,
        )
        if (canRetry) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("重试识别")
            }
        }
    }
}

@Composable
internal fun RecognitionStatusCard(stage: RecognitionStage?) {
    val currentStage = stage ?: RecognitionStage.PREPARING
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Text(currentStage.title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = currentStage.progress / 100f,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            currentStage.detail,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "已启用后台通知，离开应用后会继续处理",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
