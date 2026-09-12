package com.foodcalorie.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import com.foodcalorie.app.domain.RecognizedFood
import com.foodcalorie.app.ui.components.GlassCard
import com.foodcalorie.app.viewmodel.AddFoodViewModel
import com.foodcalorie.app.viewmodel.AddStep
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.icon.os4.Image
import top.yukonga.miuix.kmp.icon.os4.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddFoodRoute(
    viewModel: AddFoodViewModel,
    contentPadding: PaddingValues,
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (val step = state.step) {
            is AddStep.PickSource -> PickSourceContent(
                padding = contentPadding,
                configured = settings.isRecognitionConfigured,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
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
            )

            is AddStep.Manual -> ManualEntryContent(
                padding = contentPadding,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                onSave = { name, grams, nutrition ->
                    viewModel.saveManual(name, grams, nutrition)
                    onDone()
                },
            )

            is AddStep.Review -> ReviewContent(
                padding = contentPadding,
                items = step.items,
                recognizing = state.recognizing,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                onSave = { edited ->
                    viewModel.saveRecognized(edited, step.imageUri)
                    onDone()
                },
            )
        }

        if (state.recognizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(backdrop = null, modifier = Modifier.padding(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("正在识别食物…", style = MiuixTheme.textStyles.title3)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "图片将发送到你配置的视觉 API",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickSourceContent(
    padding: PaddingValues,
    configured: Boolean,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    error: String?,
    permissionHint: String?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            )
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealTypeSelector(mealType, onMealType)

        if (!configured) {
            GlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("尚未配置识别 API", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "请到「设置」填写 Base URL 与 API Key。手动录入不依赖网络。",
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
            enabled = configured,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(MiuixIcons.Os4.Image, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text(if (configured) "拍照识别" else "拍照识别（需先配置 API）")
        }

        Button(
            onClick = onGallery,
            enabled = configured,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(MiuixIcons.Os4.Photos, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("从相册选择")
        }

        Button(
            onClick = onManual,
            modifier = Modifier.fillMaxWidth(),
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
    onSave: (String, Double, Nutrition) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            )
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MealTypeSelector(mealType, onMealType)
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "食物名称",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = grams,
            onValueChange = { grams = it },
            label = "克数",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = kcal,
            onValueChange = { kcal = it },
            label = "热量 kcal",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = protein,
            onValueChange = { protein = it },
            label = "蛋白质 g",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = carbs,
            onValueChange = { carbs = it },
            label = "碳水 g",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = fat,
            onValueChange = { fat = it },
            label = "脂肪 g",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val n = name.trim()
                if (n.isEmpty()) return@Button
                onSave(
                    n,
                    grams.toDoubleOrNull() ?: 0.0,
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
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun ReviewContent(
    padding: PaddingValues,
    items: List<RecognizedFood>,
    recognizing: Boolean,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    onSave: (List<RecognizedFood>) -> Unit,
) {
    val editable = remember(items) { items.toMutableStateList() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MealTypeSelector(mealType, onMealType) }
        item {
            Text("识别结果（可修改后再保存）", style = MiuixTheme.textStyles.title4)
        }
        if (editable.isEmpty()) {
            item {
                GlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                    Text("没有识别到食物，请重拍或手动录入")
                }
            }
        }
        itemsIndexed(editable) { index, item ->
            var name by remember(item) { mutableStateOf(item.name) }
            var grams by remember(item) { mutableStateOf(item.grams.toInt().toString()) }
            var kcal by remember(item) { mutableStateOf(item.nutrition.caloriesKcal.toInt().toString()) }
            var protein by remember(item) { mutableStateOf(item.nutrition.proteinG.toInt().toString()) }
            var carbs by remember(item) { mutableStateOf(item.nutrition.carbsG.toInt().toString()) }
            var fat by remember(item) { mutableStateOf(item.nutrition.fatG.toInt().toString()) }

            fun commit() {
                editable[index] = item.copy(
                    name = name.trim().ifEmpty { item.name },
                    grams = grams.toDoubleOrNull() ?: item.grams,
                    nutrition = Nutrition(
                        caloriesKcal = kcal.toDoubleOrNull() ?: item.nutrition.caloriesKcal,
                        proteinG = protein.toDoubleOrNull() ?: item.nutrition.proteinG,
                        carbsG = carbs.toDoubleOrNull() ?: item.nutrition.carbsG,
                        fatG = fat.toDoubleOrNull() ?: item.nutrition.fatG,
                    ),
                )
            }

            GlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it; commit() },
                        label = "名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = grams,
                        onValueChange = { grams = it; commit() },
                        label = "克数",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = kcal,
                        onValueChange = { kcal = it; commit() },
                        label = "热量 kcal",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = protein,
                        onValueChange = { protein = it; commit() },
                        label = "蛋白质 g",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = carbs,
                        onValueChange = { carbs = it; commit() },
                        label = "碳水 g",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = fat,
                        onValueChange = { fat = it; commit() },
                        label = "脂肪 g",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Button(
                onClick = { onSave(editable.toList()) },
                enabled = !recognizing && editable.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存 ${editable.size} 项")
            }
        }
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

private fun <T> List<T>.toMutableStateList(): androidx.compose.runtime.snapshots.SnapshotStateList<T> {
    val list = androidx.compose.runtime.mutableStateListOf<T>()
    list.addAll(this)
    return list
}
