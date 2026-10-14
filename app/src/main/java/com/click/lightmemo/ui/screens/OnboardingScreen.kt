package com.click.lightmemo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.click.lightmemo.ui.theme.FoodTheme
import com.click.lightmemo.viewmodel.OnboardingState
import com.click.lightmemo.viewmodel.OnboardingViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Scan

@Composable
fun OnboardingGate(content: @Composable () -> Unit) {
    val vm: OnboardingViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val lastOnboardingState = remember { mutableStateOf(state) }
    // Keep the final onboarding frame unchanged while its save and exit animation run.
    if (!state.loading && state.step < 4 && !state.busy) lastOnboardingState.value = state
    if (state.loading) {
        FoodTheme {
            Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).safeDrawingPadding(),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.error == null) CircularProgressIndicator()
                else {
                    Text(state.error.orEmpty(), modifier = Modifier.padding(24.dp))
                    Button(onClick = vm::initialize, enabled = !state.busy) { Text("重试") }
                }
            }
        }
        return
    }
    val completed = state.step == 4
    AnimatedContent(targetState = completed, modifier = Modifier.fillMaxSize(), label = "onboardingCompletion",
        transitionSpec = {
            (EnterTransition.None togetherWith
                slideOutVertically(tween(440, easing = FastOutSlowInEasing)) { it })
                .apply { targetContentZIndex = if (targetState) -1f else 1f }
        }) { complete ->
        if (complete) content()
        else FoodTheme { OnboardingScreen(lastOnboardingState.value, vm) }
    }
}

@Composable
private fun OnboardingScreen(state: OnboardingState, vm: OnboardingViewModel) {
    val focus = LocalFocusManager.current
    BackHandler(enabled = state.step > 0 || state.busy) { if (!state.busy) vm.back() }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MiuixTheme.colorScheme.surface,
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surface)
                .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(visible = state.error != null) {
                        Text(state.error.orEmpty(), color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    Button(onClick = { focus.clearFocus(); vm.next() }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) {
                        AnimatedContent(targetState = if (state.busy) "请稍候…" else when (state.step) {
                            0 -> "开始设置"
                            1 -> "确认资料并继续"
                            2 -> "保存目标并继续"
                            else -> "保存并开始记录"
                        }, label = "onboardingAction", transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        }) { Text(it) }
                    }
                    Button(onClick = { focus.clearFocus(); vm.skip() }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(when (state.step) { 0 -> "先体验"; 1 -> "稍后填写"; 2 -> "稍后设置"; else -> "稍后配置，进入首页" })
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 568.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 20.dp)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo((state.step + 1) / 4f, 0f..1f, 3) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { index ->
                        val alpha by animateFloatAsState(if (index <= state.step) 1f else 0.12f, tween(260), label = "stepProgress")
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val pressAlpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(110), label = "stepPress")
                        Box(Modifier.weight(1f).height(32.dp)
                            .then(if (index < state.step) Modifier.clickable(
                                enabled = !state.busy,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "返回第 ${index + 1} 步",
                            ) { focus.clearFocus(); vm.goToStep(index) } else Modifier),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = alpha * pressAlpha)))
                        }
                    }
                }
                AnimatedContent(targetState = state.step, modifier = Modifier.weight(1f), label = "onboardingStep",
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(300)) { direction * it / 8 } + fadeIn(tween(240, 60)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -direction * it / 8 } + fadeOut(tween(160)))
                            .using(SizeTransform(clip = false))
                    }) { step ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        when (step) {
                            0 -> WelcomeContent()
                            1 -> {
                                StepHeading("从了解你开始", "确认身体资料和目标方向，为每日摄入提供参考。")
                                PersonalProfileFields(state.settings, vm::editProfile)
                            }
                            2 -> {
                                StepHeading("找到适合你的目标", if (state.settings.hasPersonalProfile)
                                    "根据已确认资料估算，你可以调整每一个目标。" else
                                    "暂未填写身体资料。下方是通用起始值，可自行调整，并非个性化推荐。")
                                DailyTargetFields(state.targets, vm::editTargets)
                                if (state.settings.hasPersonalProfile) {
                                    Button(onClick = vm::useRecommendation, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                        Text("填入推荐值")
                                    }
                                    Text("参考 Mifflin-St Jeor 公式、活动强度和目标方向估算，后续可在设置中修改。",
                                        fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                val messages = state.targets.errors() + state.targets.warnings()
                                if (messages.isNotEmpty()) Text(messages.joinToString("\n"), fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            3 -> {
                                StepHeading("让 AI 帮你记一餐", "配置后可使用 AI 辅助识别与营养估算。也可以稍后配置，先手动记录。")
                                ApiConnectionFields(state.baseUrl, state.apiKey, state.model,
                                    { vm.editApi(it, state.apiKey, state.model) },
                                    { vm.editApi(state.baseUrl, it, state.model) },
                                    { vm.editApi(state.baseUrl, state.apiKey, it) }, enabled = !state.busy)
                                Text("填写 OpenAI 兼容服务地址与模型名。使用 AI 时，照片和相关描述会发送到你选择的服务。测试会发送一条简短文字请求，服务商可能计费。",
                                    fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Button(onClick = { focus.clearFocus(); vm.testConnection() },
                                    enabled = !state.busy && state.baseUrl.isNotBlank() && state.apiKey.isNotBlank() && state.model.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()) { Text("测试连接") }
                                state.connectionMessage?.let {
                                    Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        Text(description, fontSize = 16.sp, lineHeight = 24.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun WelcomeContent() {
    val primary = MiuixTheme.colorScheme.primary
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val sweep by animateFloatAsState(if (entered) 270f else 0f, tween(700), label = "welcomeSweep")
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(148.dp)) {
            drawCircle(primary.copy(alpha = 0.06f), radius = size.minDimension / 2)
            val inset = size.width * 0.18f
            drawArc(primary.copy(alpha = 0.12f), -90f, 360f, false,
                Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(size.width * 0.055f, cap = StrokeCap.Round))
            drawArc(primary, -90f, sweep, false,
                Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(size.width * 0.055f, cap = StrokeCap.Round))
            drawLine(primary, Offset(size.width * 0.37f, size.height * 0.51f),
                Offset(size.width * 0.46f, size.height * 0.60f), size.width * 0.04f, StrokeCap.Round)
            drawLine(primary, Offset(size.width * 0.46f, size.height * 0.60f),
                Offset(size.width * 0.64f, size.height * 0.40f), size.width * 0.04f, StrokeCap.Round)
        }
    }
    StepHeading("好好吃饭，\n慢慢了解自己。", "欢迎使用轻食记。从记录一餐开始，让每天的饮食更清晰。")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("轻松记录" to "手动添加每一餐，留下自己的饮食日常。",
            "看懂营养" to "查看热量和营养素，了解摄入与目标。",
            "AI 辅助" to "拍照辅助识别，减少输入；配置可稍后完成。").forEachIndexed { index, feature ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (index) {
                        0 -> Icon(MiuixIcons.Regular.Edit, contentDescription = null, modifier = Modifier.size(34.dp), tint = primary)
                        1 -> Icon(MiuixIcons.Regular.SelectAll, contentDescription = null, modifier = Modifier.size(34.dp), tint = primary)
                        else -> Icon(MiuixIcons.Regular.Scan, contentDescription = null, modifier = Modifier.size(34.dp), tint = primary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(feature.first, fontWeight = FontWeight.SemiBold)
                        Text(feature.second, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
}
