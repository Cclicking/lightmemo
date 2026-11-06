package com.click.lightmemo.ui.components

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.click.lightmemo.ui.secondary.ApiConfigurationActivity
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared, single-line shortcut to the active AI provider's configuration. */
@Composable
fun AiConfigurationBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // Information blue is intentionally independent of the user's accent palette.
    val background = if (dark) Color(0xFF223B55) else Color(0xFFEAF4FF)
    val foreground = if (dark) Color(0xFFA9D1FF) else Color(0xFF2866A6)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(role = Role.Button, onClickLabel = "前往 API 配置") {
                context.startActivity(Intent(context, ApiConfigurationActivity::class.java))
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "未配置 AI",
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.subtitle,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(Modifier.padding(start = 12.dp).size(18.dp)) {
            drawLine(foreground, Offset(size.width * 0.38f, size.height * 0.22f),
                Offset(size.width * 0.66f, size.height * 0.5f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(foreground, Offset(size.width * 0.66f, size.height * 0.5f),
                Offset(size.width * 0.38f, size.height * 0.78f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
