package com.helywin.leggedjoystick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.helywin.leggedjoystick.MainActivity
import com.helywin.leggedjoystick.R
import timber.log.Timber

class RemoteControlForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(
            connectionText = intent?.getStringExtra(EXTRA_CONNECTION_TEXT) ?: "遥控链路保持中",
            inputText = intent?.getStringExtra(EXTRA_INPUT_TEXT) ?: "外部输入监听中"
        )
        startForeground(NOTIFICATION_ID, notification)
        Timber.i("[前台服务] 已启动遥控前台服务")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        Timber.i("[前台服务] 已停止遥控前台服务")
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "机器狗遥控链路",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持遥控连接、输入监听和状态同步"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(connectionText: String, inputText: String): Notification {
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_dog)
            .setContentTitle("机器狗遥控运行中")
            .setContentText(connectionText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$connectionText\n$inputText")
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.helywin.leggedjoystick.action.START_REMOTE_CONTROL_SERVICE"
        private const val EXTRA_CONNECTION_TEXT = "connection_text"
        private const val EXTRA_INPUT_TEXT = "input_text"
        private const val CHANNEL_ID = "remote_control_connection"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, connectionText: String, inputText: String) {
            val intent = Intent(context, RemoteControlForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONNECTION_TEXT, connectionText)
                putExtra(EXTRA_INPUT_TEXT, inputText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteControlForegroundService::class.java))
        }
    }
}
