package com.foodcalorie.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.icon.os4.Image
import top.yukonga.miuix.kmp.icon.os4.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddFoodRoute(
    viewModel: AddFoodViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    viewModel.backToPick()
                    onDone()
                }) {
                    Icon(MiuixIcons.Os4.ChevronBackward, contentDescription = "返回")
                }
                Text(
                    text = "记录食物",
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        when (val step = state.step) {
            is AddStep.PickSource -> PickSourceContent(
                padding = padding,
                configured = settings.isRecognitionConfigured,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                error = state.error,
                onGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCamera = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                },
                onManual = viewModel::openManual,
            )

            is AddStep.Manual -> ManualEntryContent(
                padding = padding,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                onSave = { name, grams, nutrition ->
                    viewModel.saveManual(name, grams, nutrition)
                    onDone()
                },
            )

            is AddStep.Review -> ReviewContent(
                padding = padding,
                items = step.items,
                recognizing = state.recognizing,
                mealType = state.mealType,
                onMealType = viewModel::setMealType,
                onSave = {
                    viewModel.saveRecognized(step.items, step.imageUri)
                    onDone()
                },
            )
        }

        if (state.recognizing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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

@Composable
private fun PickSourceContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    configured: Boolean,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    error: String?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
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
    padding: androidx.compose.foundation.layout.PaddingValues,
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
            .padding(padding)
            .padding(16.dp)
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
    padding: androidx.compose.foundation.layout.PaddingValues,
    items: List<RecognizedFood>,
    recognizing: Boolean,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    onSave: () -> Unit,
) {
    val editable = remember(items) { items.toMutableStateList() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
            GlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextField(
                        value = name,
                        onValueChange = {
                            name = it
                            editable[index] = item.copy(name = it)
                        },
                        label = "名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = grams,
                        onValueChange = {
                            grams = it
                            editable[index] = item.copy(grams = it.toDoubleOrNull() ?: 0.0)
                        },
                        label = "克数",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = kcal,
                        onValueChange = {
                            kcal = it
                            editable[index] = item.copy(
                                nutrition = item.nutrition.copy(
                                    caloriesKcal = it.toDoubleOrNull() ?: 0.0,
                                ),
                            )
                        },
                        label = "热量 kcal",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "蛋白 ${item.nutrition.proteinG.toInt()}g · 碳水 ${item.nutrition.carbsG.toInt()}g · 脂肪 ${item.nutrition.fatG.toInt()}g",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        item {
            Button(
                onClick = onSave,
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
    Column {
        SmallTitle(text = "餐次")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { type ->
                TextButton(
                    text = type.label,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

private fun <T> List<T>.toMutableStateList(): androidx.compose.runtime.snapshots.SnapshotStateList<T> {
    val list = androidx.compose.runtime.mutableStateListOf<T>()
    list.addAll(this)
    return list
}

@Suppress("unused")
private fun unusedLaunchedEffect() {
    // keep import surface stable for future lifecycle hooks
}

@Composable
private fun SuppressUnused() {
    LaunchedEffect(Unit) { }
}
