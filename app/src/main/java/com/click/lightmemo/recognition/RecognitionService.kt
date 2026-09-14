package com.click.lightmemo.recognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.click.lightmemo.FoodApp
import com.click.lightmemo.MainActivity
import com.click.lightmemo.R
import com.click.lightmemo.data.FoodImages
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.domain.splitDishes
import com.click.lightmemo.network.RecognitionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val EXTRA_OPEN_RECOGNITION = "com.click.lightmemo.extra.OPEN_RECOGNITION"

/**
 * Owns recognition work independently of the Compose lifecycle. The service is started directly
 * from a user action, so it remains eligible to run while the user switches to another app.
 */
class RecognitionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var taskJob: Job? = null

    private val app: FoodApp
        get() = application as FoodApp

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID)
            val currentId = app.recognitionTaskStore.state.value?.request?.id
            if (taskId == null || taskId == currentId) {
                taskJob?.cancel()
                app.recognitionTaskStore.clear(taskId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            return if (app.recognitionTaskStore.state.value?.status == RecognitionTaskStatus.RUNNING) {
                START_REDELIVER_INTENT
            } else {
                START_NOT_STICKY
            }
        }

        val request = intent?.getStringExtra(EXTRA_REQUEST)?.let { raw ->
            runCatching { json.decodeFromString<RecognitionRequest>(raw) }.getOrNull()
        } ?: app.recognitionTaskStore.state.value?.request

        if (request == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val current = app.recognitionTaskStore.state.value
        if (current?.request?.id == request.id && current.status != RecognitionTaskStatus.RUNNING) {
            // START_REDELIVER_INTENT may replay the last start after the result was already
            // committed. Do not call the model twice for the same task.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        taskJob?.cancel()
        if (current?.request?.id != request.id) {
            app.recognitionTaskStore.begin(request)
        }

        // This call is intentionally immediate: startForegroundService must be promoted before
        // the system's startup deadline, even if image decoding or network setup takes time.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildProgressNotification(request, app.recognitionTaskStore.state.value?.stage ?: RecognitionStage.PREPARING),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (error: Exception) {
            val message = error.message ?: "无法启动前台识别服务"
            if (isCurrentTask(request.id)) {
                app.recognitionTaskStore.fail(request.id, message)
                postFinishedNotification(request, success = false, message = message)
            }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        taskJob = serviceScope.launch {
            try {
                execute(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.message ?: "识别失败"
                if (isCurrentTask(request.id)) {
                    app.recognitionTaskStore.fail(request.id, message)
                }
            } finally {
                // A canceled/replaced task must not stop the newer task that now owns the service.
                if (isCurrentTask(request.id) &&
                    app.recognitionTaskStore.state.value?.status != RecognitionTaskStatus.RUNNING
                ) {
                    val record = app.recognitionTaskStore.state.value
                    if (record?.status == RecognitionTaskStatus.COMPLETED) {
                        retainCompletedLiveNotification(request, startId, record)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        postFinishedNotification(
                            request,
                            success = false,
                            message = record?.error ?: "识别失败",
                        )
                        stopSelfResult(startId)
                    }
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private suspend fun execute(request: RecognitionRequest) {
        val settings = app.settingsRepository.settings.first()
        if (!settings.isRecognitionConfigured) {
            throw RecognitionException("请先在设置中配置 API Key 与 Base URL")
        }

        val client = app.recognitionClient
        val nutritionDatabase = app.nutritionDatabase
        val userDescription = buildList {
            settings.systemBackground.takeIf { it.isNotBlank() }?.let { add("用户背景：$it") }
            request.selectedTags.joinToString("、").takeIf { it.isNotBlank() }?.let { add("本餐说明：$it") }
            request.note.takeIf { it.isNotBlank() }?.let { add("备注：$it") }
        }.joinToString("\n")

        when (request.type) {
            RecognitionRequestType.MANUAL_GRAMS -> {
                val foodName = request.foodName ?: throw RecognitionException("食物名称为空")
                val grams = request.grams ?: throw RecognitionException("食物重量为空")
                updateStage(request, RecognitionStage.RECOGNIZING)
                val query = client.normalizeFoodQuery(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    foodName = foodName,
                )
                updateStage(request, RecognitionStage.MATCHING)
                val reference = nutritionDatabase.lookup(query, settings.foodDataCentralApiKey)
                    ?: throw RecognitionException("USDA 数据库中未找到该食物，请尝试更具体的名称")
                complete(
                    request,
                    manualNutrition = reference.per100g * (grams / 100.0),
                )
            }

            RecognitionRequestType.MANUAL_PORTIONS -> {
                val foodName = request.foodName ?: throw RecognitionException("食物名称为空")
                val portions = request.portions ?: throw RecognitionException("份数为空")
                updateStage(request, RecognitionStage.RECOGNIZING)
                val grams = client.estimatePortionGrams(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    foodName = foodName,
                    portions = portions,
                    portionHint = request.portionHint,
                )
                val query = client.normalizeFoodQuery(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    foodName = foodName,
                )
                updateStage(request, RecognitionStage.MATCHING)
                val reference = nutritionDatabase.lookup(query, settings.foodDataCentralApiKey)
                    ?: throw RecognitionException("USDA 数据库中未找到该食物，请尝试更具体的名称")
                complete(
                    request,
                    manualNutrition = reference.per100g * (grams / 100.0),
                    estimatedPortionGrams = grams,
                )
            }

            RecognitionRequestType.TEXT -> {
                val text = request.text ?: throw RecognitionException("食物描述为空")
                updateStage(request, RecognitionStage.RECOGNIZING)
                val visualResult = client.recognizeFromText(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    text = text,
                    userDescription = userDescription,
                    mealType = request.mealType.label,
                    plateSize = request.plateSize,
                )
                if (visualResult.dishes.isEmpty()) {
                    throw RecognitionException("未能识别到可记录的食物，请换个说法")
                }
                updateStage(request, RecognitionStage.MATCHING)
                complete(request, result = nutritionDatabase.enrich(visualResult, settings.foodDataCentralApiKey).splitDishes())
            }

            RecognitionRequestType.IMAGE -> {
                val imageUri = request.imageUri ?: throw RecognitionException("图片地址为空")
                updateStage(request, RecognitionStage.PREPARING)
                val base64 = FoodImages.encode(this, android.net.Uri.parse(imageUri))
                val visualResult = client.recognize(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    imageBase64 = base64,
                    mimeType = "image/jpeg",
                    userDescription = userDescription,
                    mealType = request.mealType.label,
                    plateSize = request.plateSize,
                    onStage = { stage -> updateStage(request, stage) },
                )
                if (!visualResult.isFoodImage || visualResult.dishes.isEmpty()) {
                    throw RecognitionException("图片中没有识别到可记录的食物")
                }
                updateStage(request, RecognitionStage.MATCHING)
                val result = nutritionDatabase.enrich(visualResult, settings.foodDataCentralApiKey).splitDishes()
                val savedImageUri = FoodImages.persistEncoded(this, base64).toString()
                complete(request, result = result, imageUri = savedImageUri)
            }

            RecognitionRequestType.REPLACE_DISH -> {
                val name = request.foodName ?: throw RecognitionException("菜品名称为空")
                val original = request.baseResult?.dishes?.find { it.id == request.replaceDishId }
                    ?: throw RecognitionException("原菜品不存在，请重试")
                updateStage(request, RecognitionStage.RECOGNIZING)
                val visual = client.recognizeFromText(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    text = "${original.grams}克${name.trim()}",
                    userDescription = "更正一道菜品，保持总重量，重新识别组成。",
                    mealType = request.mealType.label,
                    plateSize = request.plateSize,
                )
                updateStage(request, RecognitionStage.MATCHING)
                val replacement = nutritionDatabase.enrich(visual, settings.foodDataCentralApiKey).splitDishes()
                require(replacement.dishes.isNotEmpty() && replacement.dishes.all { it.allComponents.isNotEmpty() }) {
                    "未识别到新菜品，请重试"
                }
                complete(request, result = replacement)
            }
        }
    }

    private fun updateStage(request: RecognitionRequest, stage: RecognitionStage) {
        if (!isCurrentTask(request.id)) return
        app.recognitionTaskStore.updateStage(request.id, stage)
        if (isCurrentTask(request.id)) {
            notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(request, stage))
        }
    }

    private fun complete(
        request: RecognitionRequest,
        result: MealRecognition? = null,
        imageUri: String? = null,
        manualNutrition: com.click.lightmemo.domain.Nutrition? = null,
        estimatedPortionGrams: Double? = null,
    ) {
        if (!isCurrentTask(request.id)) return
        app.recognitionTaskStore.complete(
            taskId = request.id,
            result = result,
            imageUri = imageUri,
            manualNutrition = manualNutrition,
            estimatedPortionGrams = estimatedPortionGrams,
        )
    }

    private fun isCurrentTask(taskId: String): Boolean =
        app.recognitionTaskStore.state.value?.request?.id == taskId

    private suspend fun retainCompletedLiveNotification(
        request: RecognitionRequest,
        startId: Int,
        record: RecognitionTaskRecord,
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildProgressNotification(request, RecognitionStage.COMPLETED),
        )
        delay(COMPLETION_NOTIFICATION_RETENTION_MS)

        // Saving the result clears the task record. A newer task, however, owns a different ID
        // and must keep the foreground notification untouched.
        val current = app.recognitionTaskStore.state.value
        if (current != null && current.request.id != request.id) return

        stopForeground(STOP_FOREGROUND_REMOVE)
        if (current?.request?.id == request.id) {
            postFinishedNotification(
                request,
                success = true,
                message = record.stage.detail,
            )
        }
        stopSelfResult(startId)
    }

    private fun buildProgressNotification(
        request: RecognitionRequest,
        stage: RecognitionStage,
    ): Notification {
        val progressColor = 0xff4f8fe8.toInt()
        val upcomingPointColor = 0xff356da8.toInt()
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgress(stage.progress)
            .setStyledByProgress(true)
            .setProgressSegments(
                listOf(
                    // Equal segments keep each milestone exactly centered at a seam.
                    NotificationCompat.ProgressStyle.Segment(25).setColor(progressColor),
                    NotificationCompat.ProgressStyle.Segment(25).setColor(progressColor),
                    NotificationCompat.ProgressStyle.Segment(25).setColor(progressColor),
                    NotificationCompat.ProgressStyle.Segment(25).setColor(progressColor),
                ),
            )
            .setProgressPoints(
                listOf(
                    RecognitionStage.RECOGNIZING,
                    RecognitionStage.REVIEWING,
                    RecognitionStage.MATCHING,
                ).map { milestone ->
                    val pointColor = if (milestone.progress <= stage.progress) {
                        progressColor
                    } else {
                        // Upcoming milestones use a darker blue without dimming completed work.
                        upcomingPointColor
                    }
                    NotificationCompat.ProgressStyle.Point(milestone.progress).setColor(pointColor)
                },
            )
        return baseNotification(request)
            .setContentTitle(stage.title)
            .setContentText(stage.detail)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setRequestPromotedOngoing(true)
            .apply {
                if (stage != RecognitionStage.COMPLETED) {
                    addAction(
                        NotificationCompat.Action(
                            R.mipmap.ic_launcher,
                            "取消",
                            cancelPendingIntent(request),
                        ),
                    )
                }
                addAction(
                    NotificationCompat.Action(
                        R.mipmap.ic_launcher,
                        "查看",
                        openAppPendingIntent(request),
                    ),
                )
            }
            .build()
    }

    private fun postFinishedNotification(request: RecognitionRequest, success: Boolean, message: String) {
        val notification = baseNotification(request)
            .setContentTitle(if (success) "轻食记 · 识别完成" else "轻食记 · 识别失败")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun baseNotification(request: RecognitionRequest): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setContentIntent(openAppPendingIntent(request))

    private fun openAppPendingIntent(request: RecognitionRequest): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_RECOGNITION, true)
            putExtra(EXTRA_TASK_ID, request.id)
        }
        return PendingIntent.getActivity(
            this,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPendingIntent(request: RecognitionRequest): PendingIntent {
        val intent = Intent(this, RecognitionService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TASK_ID, request.id)
        }
        return PendingIntent.getService(
            this,
            request.id.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.recognition_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示食物识别的实时进度"
                },
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        taskJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CANCEL = "com.click.lightmemo.action.CANCEL_RECOGNITION"
        private const val EXTRA_REQUEST = "com.click.lightmemo.extra.RECOGNITION_REQUEST"
        private const val EXTRA_TASK_ID = "com.click.lightmemo.extra.RECOGNITION_TASK_ID"
        private const val CHANNEL_ID = "food_recognition"
        private const val NOTIFICATION_ID = 4107
        private const val COMPLETION_NOTIFICATION_RETENTION_MS = 12_000L

        fun start(context: Context, request: RecognitionRequest) {
            val intent = Intent(context, RecognitionService::class.java).apply {
                putExtra(EXTRA_REQUEST, Json.encodeToString(request))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, taskId: String? = null) {
            val intent = Intent(context, RecognitionService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }
}
