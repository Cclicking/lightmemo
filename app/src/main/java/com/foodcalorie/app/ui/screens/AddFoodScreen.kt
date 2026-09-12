package com.foodcalorie.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import com.foodcalorie.app.domain.FoodComponent
import com.foodcalorie.app.domain.MealRecognition
import com.foodcalorie.app.domain.RecognizedDish
import com.foodcalorie.app.viewmodel.AddFoodViewModel
import com.foodcalorie.app.viewmodel.AddStep
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.icon.os4.Image
import top.yukonga.miuix.kmp.icon.os4.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val AddFoodSheetHeight = 780.dp

@Composable
fun AddFoodRoute(
    viewModel: AddFoodViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.recognizeFromUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            viewModel.recognizeFromUri(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            cameraUri?.let { cameraLauncher.launch(it) }
        } else {
            cameraPermissionDenied = true
        }
    }

    fun prepareCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun onCameraClick() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        val uri = prepareCameraUri()
        cameraUri = uri
        if (granted) {
            cameraPermissionDenied = false
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        when (val step = state.step) {
            is AddStep.PickSource -> PickSourceContent(
                padding = contentPadding,
                configured = settings.isRecognitionConfigured,
                recognizing = state.recognizing,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                plateSize = state.plateSize,
                onPlateSizeChange = viewModel::setPlateSize,
                photoDescription = state.photoDescription,
                onPhotoDescriptionChange = viewModel::setPhotoDescription,
                error = state.error,
                permissionHint = if (cameraPermissionDenied) {
                    "相机权限被拒绝，请在系统设置中开启，或改用相册"
                } else {
                    null
                },
                onGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCamera = ::onCameraClick,
                onManual = viewModel::openManual,
                scrollBehavior = scrollBehavior,
            )

            is AddStep.Manual -> ManualEntryContent(
                padding = contentPadding,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                configured = settings.isRecognitionConfigured,
                recognizing = state.recognizing,
                nutritionSuggestion = state.manualNutrition,
                error = state.error,
                onRecognize = viewModel::recognizeManual,
                onSave = { name, grams, nutrition ->
                    viewModel.saveManual(name, grams, nutrition)
                    onDone()
                },
                scrollBehavior = scrollBehavior,
            )

            is AddStep.Review -> ReviewContent(
                padding = contentPadding,
                result = step.result,
                recognizing = state.recognizing,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                onWeightChange = viewModel::updateComponentWeight,
                onSave = {
                    viewModel.saveRecognized(step.result, step.imageUri)
                    onDone()
                },
                scrollBehavior = scrollBehavior,
                listState = listState,
            )
        }

    }
}

@Composable
private fun PickSourceContent(
    padding: PaddingValues,
    configured: Boolean,
    recognizing: Boolean,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    plateSize: String,
    onPlateSizeChange: (String) -> Unit,
    photoDescription: String,
    onPhotoDescriptionChange: (String) -> Unit,
    error: String?,
    permissionHint: String?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
    scrollBehavior: ScrollBehavior?,
) {
    val connection = scrollBehavior?.nestedScrollConnection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (connection != null) Modifier.nestedScroll(connection) else Modifier)
            .verticalScroll(rememberScrollState())
            .height(AddFoodSheetHeight)
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealTypeSelector(mealType, onMealType)

        TextField(
            value = plateSize,
            onValueChange = onPlateSizeChange,
            label = "餐具尺寸（可选，如直径 24cm）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = photoDescription,
            onValueChange = onPhotoDescriptionChange,
            label = "本餐说明（可选，如少油、无糖）",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!configured) {
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column {
                    Text("尚未配置识别 API", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "请到「我的 → 识别 API」填写 Base URL 与 API Key。手动录入不依赖网络。",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        if (permissionHint != null) {
            Text(
                text = permissionHint,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }

        Button(
            onClick = onCamera,
            enabled = configured && !recognizing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(color = Color.White, contentColor = MiuixTheme.colorScheme.onSurface),
        ) {
            Icon(MiuixIcons.Os4.Image, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text(if (configured) "拍照识别" else "拍照识别（需先配置 API）")
        }

        Button(
            onClick = onGallery,
            enabled = configured && !recognizing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(color = Color.White, contentColor = MiuixTheme.colorScheme.onSurface),
        ) {
            Icon(MiuixIcons.Os4.Photos, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("从相册选择")
        }

        Button(
            onClick = onManual,
            enabled = !recognizing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(color = Color.White, contentColor = MiuixTheme.colorScheme.onSurface),
        ) {
            Icon(MiuixIcons.Os4.Edit, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("手动录入")
        }

        if (error != null) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
    }
}

@Composable
private fun ManualEntryContent(
    padding: PaddingValues,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    configured: Boolean,
    recognizing: Boolean,
    nutritionSuggestion: Nutrition?,
    error: String?,
    onRecognize: (String, Double) -> Unit,
    onSave: (String, Double, Nutrition) -> Unit,
    scrollBehavior: ScrollBehavior?,
) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val connection = scrollBehavior?.nestedScrollConnection

    LaunchedEffect(nutritionSuggestion) {
        nutritionSuggestion?.let { nutrition ->
            kcal = nutrition.caloriesKcal.formatInput()
            protein = nutrition.proteinG.formatInput()
            carbs = nutrition.carbsG.formatInput()
            fat = nutrition.fatG.formatInput()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (connection != null) Modifier.nestedScroll(connection) else Modifier)
            .verticalScroll(rememberScrollState())
            .height(AddFoodSheetHeight)
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
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "食物名称",
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = grams,
            onValueChange = { grams = it },
            label = "克数（默认 100g）",
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val amount = grams.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 100.0
                onRecognize(name, amount)
            },
            enabled = configured && !recognizing && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(
                color = Color(0xFF0A84FF),
                contentColor = Color.White,
            ),
        ) {
            Text(if (recognizing) "正在识别营养..." else "自动识别热量与营养")
        }
        if (!configured) {
            Text(
                text = "自动识别需要先在「我的 → 识别 API」配置服务；也可以继续手动填写。",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (error != null) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
        TextField(
            value = kcal,
            onValueChange = { kcal = it },
            label = "热量 kcal",
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = protein,
            onValueChange = { protein = it },
            label = "蛋白质 g",
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = carbs,
            onValueChange = { carbs = it },
            label = "碳水 g",
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = fat,
            onValueChange = { fat = it },
            label = "脂肪 g",
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val n = name.trim()
                if (n.isEmpty()) return@Button
                onSave(
                    n,
                    grams.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 100.0,
                    Nutrition(
                        caloriesKcal = kcal.toDoubleOrNull() ?: 0.0,
                        proteinG = protein.toDoubleOrNull() ?: 0.0,
                        carbsG = carbs.toDoubleOrNull() ?: 0.0,
                        fatG = fat.toDoubleOrNull() ?: 0.0,
                    ),
                )
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(color = Color(0xFF0A84FF), contentColor = Color.White),
        ) {
            Text("保存")
        }
    }
}

private fun Double.formatInput(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

@Composable
private fun ReviewContent(
    padding: PaddingValues,
    result: MealRecognition,
    recognizing: Boolean,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    onSave: () -> Unit,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val connection = scrollBehavior?.nestedScrollConnection

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (connection != null) Modifier.nestedScroll(connection) else Modifier)
            .height(AddFoodSheetHeight)
            .navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MealTypeSelector(mealType, onMealType) }
        item {
            Text("${result.mealName} · 约 ${result.nutrition.caloriesKcal.toInt()} kcal", style = MiuixTheme.textStyles.title4)
        }
        item {
            Text(
                text = "估计范围 ${result.nutritionMin.caloriesKcal.toInt()}–${result.nutritionMax.caloriesKcal.toInt()} kcal · 识别可信度 ${(result.overallConfidence * 100).toInt()}%",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (result.imageQualityIssues.isNotEmpty()) {
            item {
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Text("图片质量：${result.imageQualityIssues.joinToString("；")}")
                }
            }
        }
        items(result.dishes, key = { it.id }) { dish ->
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                DishReview(dish = dish, onWeightChange = onWeightChange)
            }
        }
        if (result.confirmationQuestions.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("建议确认", style = MiuixTheme.textStyles.title4)
                    result.confirmationQuestions.forEach { question ->
                        Text(
                            text = "• $question",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = onSave,
                enabled = !recognizing && result.dishes.isNotEmpty() && result.dishes.all { dish ->
                    dish.allComponents.all { it.nutritionReference != null }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存 ${result.dishes.size} 道菜")
            }
        }
    }
}

@Composable
private fun DishReview(
    dish: RecognizedDish,
    onWeightChange: (String, Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dish.name, style = MiuixTheme.textStyles.title4)
            Text("约 ${dish.nutrition.caloriesKcal.toInt()} kcal", fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = "蛋白质 ${dish.nutrition.proteinG.formatInput()}g · 碳水 ${dish.nutrition.carbsG.formatInput()}g · 脂肪 ${dish.nutrition.fatG.formatInput()}g",
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        dish.components.forEach { component ->
            ComponentReview(component = component, onWeightChange = onWeightChange)
        }
        dish.children.forEach { child ->
            Text(child.name, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.SemiBold)
            child.components.forEach { component ->
                ComponentReview(component = component, onWeightChange = onWeightChange)
            }
        }
        if (dish.uncertaintyReason != null) {
            Text(
                text = "主要误差：${dish.uncertaintyReason}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ComponentReview(
    component: FoodComponent,
    onWeightChange: (String, Double) -> Unit,
) {
    var input by remember(component.id) { mutableStateOf(component.estimatedWeightG.formatInput()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(component.name)
            Text(
                text = component.nutritionReference?.let {
                    "${it.description} · ${it.dataType} #${it.sourceId}"
                } ?: "未匹配到营养数据",
                style = MiuixTheme.textStyles.footnote2,
                color = if (component.nutritionReference == null) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        TextField(
            value = input,
            onValueChange = { value ->
                input = value
                value.toDoubleOrNull()?.let { onWeightChange(component.id, it) }
            },
            label = "克",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.55f),
        )
        Text("${component.nutrition.caloriesKcal.toInt()} kcal")
    }
}

@Composable
fun MealTypeSelector(
    selected: MealType,
    onSelect: (MealType) -> Unit,
) {
    val types = MealType.entries
    TabRowWithContour(
        tabs = types.map { it.label },
        selectedTabIndex = types.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> onSelect(types[index]) },
    )
}
