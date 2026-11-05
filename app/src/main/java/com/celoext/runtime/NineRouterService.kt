package com.celoext.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.celoext.Graph
import com.celoext.MainActivity
import com.celoext.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that hosts the local 9Router gateway, next to [ServerService]. */
class NineRouterService : Service() {
    companion object {
        const val ACTION_START = "com.celoext.NR_START"
        const val ACTION_STOP = "com.celoext.NR_STOP"
        private const val CHANNEL = "9router"
        private const val ID = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(ID, notification("Starting 9Router…"))
        if (intent?.action == ACTION_STOP) {
            Graph.ninerouter.stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        if (Graph.prefs.keepAwake && wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ocmobile:ninerouter").apply { setReferenceCounted(false); acquire() }
        }
        if (job == null) {
            job = scope.launch {
                launch {
                    Graph.ninerouter.state.collect { s ->
                        val text = when (s) {
                            is NineRouterState.Running -> "9Router ${s.version} · localhost:${Graph.ninerouter.port}"
                            is NineRouterState.Crashed -> "9Router crashed"
                            is NineRouterState.Installing -> s.step
                            else -> "Starting 9Router…"
                        }
                        getSystemService(NotificationManager::class.java).notify(ID, notification(text))
                    }
                }
                Graph.ninerouter.supervise()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Graph.ninerouter.requestStop()
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "9Router server", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("9Router")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
