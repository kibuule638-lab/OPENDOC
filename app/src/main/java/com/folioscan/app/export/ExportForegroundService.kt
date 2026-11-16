package com.folioscan.app.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R

/**
 * Foreground service used during large (>10 page) PDF/JPG exports so OS memory pressure
 * does not terminate export streaming on 4GB entry-level devices.
 */
class ExportForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "folioscan_export_channel"
        private const val NOTIFICATION_ID = 4102
        const val EXTRA_PAGE_COUNT = "page_count"

        fun start(context: Context, pageCount: Int) {
            val intent = Intent(context, ExportForegroundService::class.java).apply {
                putExtra(EXTRA_PAGE_COUNT, pageCount)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ExportForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val pageCount = intent?.getIntExtra(EXTRA_PAGE_COUNT, 0) ?: 0
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.export_foreground_title))
            .setContentText("Processing $pageCount pages offline…")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.export_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.export_notification_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
