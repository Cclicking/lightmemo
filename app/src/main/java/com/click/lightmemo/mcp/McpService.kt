package com.click.lightmemo.mcp

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
import com.click.lightmemo.R
import com.click.lightmemo.ui.secondary.McpSettingsActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

data class McpState(val running: Boolean = false, val starting: Boolean = false, val address: String = "", val error: String? = null)

object McpRuntime {
    internal val mutableState = MutableStateFlow(McpState())
    val state = mutableState.asStateFlow()

    fun start(context: Context) {
        if (state.value.running || state.value.starting) return
        mutableState.value = McpState(starting = true)
        try {
            ContextCompat.startForegroundService(context, Intent(context, McpService::class.java))
        } catch (_: Exception) {
            mutableState.value = McpState(error = "服务启动失败，请保持轻食记在前台并重试。")
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, McpService::class.java))
    }
}

class McpService : Service() {
    private var server: McpHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server != null) return START_NOT_STICKY
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "MCP 服务", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, McpSettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, McpService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("轻食记 MCP 服务正在运行")
                .setContentText("支持营养查询与文字录入，点按管理连接")
                .setContentIntent(open).setOngoing(true)
                .addAction(0, "停止服务", stop).build()
            ServiceCompat.startForeground(this, NOTIFICATION, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            // A fresh, memory-only credential for every service lifetime; closing revokes it.
            val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val app = application as FoodApp
            val recording = ExistingTextRecordingTool(currentTask = { app.recognitionTaskStore.state.value },
                start = { request ->
                    app.recognitionTaskStore.begin(request)
                    try { com.click.lightmemo.recognition.RecognitionService.start(app, request) }
                    catch (_: Exception) {
                        val message = "无法启动文字识别，请打开轻食记后重试。"
                        app.recognitionTaskStore.fail(request.id, message)
                        throw TextRecordingStartException(message)
                    }
                }, openResult = {
                    app.startActivity(Intent(app, com.click.lightmemo.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(com.click.lightmemo.recognition.EXTRA_OPEN_RECOGNITION, true))
                })
            server = McpHttpServer(McpProtocol(LocalNutritionProvider(app.foodLogRepository), recording), token,
                onFailure = {
                    android.os.Handler(mainLooper).post {
                        McpRuntime.mutableState.value = McpState(error = "连接已中断，请重新开启服务。")
                        stopSelf()
                    }
                })
            McpRuntime.mutableState.value = McpState(running = true, address = "http://127.0.0.1:8765/mcp?token=$token")
        } catch (e: Exception) {
            server?.close()
            server = null
            McpRuntime.mutableState.value = McpState(error = if (e is java.net.BindException)
                "连接端口已被占用，请关闭其他 MCP 服务后重试。" else "服务启动失败，请稍后重试。")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server?.close()
        server = null
        McpRuntime.mutableState.value = McpState(error = McpRuntime.state.value.error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mcp_service"
        private const val NOTIFICATION = 8765
        private const val ACTION_STOP = "com.click.lightmemo.mcp.STOP"
    }
}
