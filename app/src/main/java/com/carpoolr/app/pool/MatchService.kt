package com.carpoolr.app.pool

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.carpoolr.app.MainActivity
import com.carpoolr.app.R
import com.carpoolr.app.data.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps looking while you're searching, even with the app in the background, and
 * notifies you the moment you're pooled (or when the search runs out). Stops itself after.
 */
class MatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        val ongoing = NotificationCompat.Builder(this, CH_SEARCH)
            .setSmallIcon(R.drawable.ic_route_mark)
            .setContentTitle("finding your pool")
            .setContentText("looking for riders heading your way")
            .setOngoing(true)
            .setContentIntent(openApp(this))
            .build()
        ServiceCompat.startForeground(
            this, ID_SEARCH, ongoing,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        if (job?.isActive != true) job = scope.launch {
            val started = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - started < 20 * 60_000) {
                val rows = runCatching { Backend.myPool() }.getOrNull()
                if (rows != null) {
                    if (rows.isEmpty()) break
                    val first = rows.first()
                    if (first.status == "matched" && rows.size > 1) {
                        val others = rows.filter { !it.isMe }.joinToString(", ") { it.displayName ?: "rider" }
                        notifyMatched(this@MatchService, others, rows.firstOrNull { it.isMe }?.dropoffLabel)
                        break
                    }
                }
                delay(5000)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CH_SEARCH = "searching"
        private const val CH_MATCH = "matched"
        private const val ID_SEARCH = 1
        private const val ID_MATCH = 2

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MatchService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MatchService::class.java))
        }

        private fun channels(c: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_SEARCH, "searching", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_MATCH, "pool found", NotificationManager.IMPORTANCE_HIGH))
        }

        private fun openApp(c: Context) = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun notifyMatched(c: Context, others: String, drop: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            channels(c)
            val n = NotificationCompat.Builder(c, CH_MATCH)
                .setSmallIcon(R.drawable.ic_route_mark)
                .setContentTitle("you're pooled")
                .setContentText("riding with $others" + (drop?.let { " to $it" } ?: ""))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(c))
                .build()
            c.getSystemService(NotificationManager::class.java).notify(ID_MATCH, n)
        }
    }
}
