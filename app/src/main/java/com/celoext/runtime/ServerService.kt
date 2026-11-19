package com.celoext.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.compose.runtime.snapshotFlow
import com.celoext.Graph
import com.celoext.MainActivity
import com.celoext.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Foreground service that hosts the `opencode serve` process. */
class ServerService : Service() {
    companion object {
        const val ACTION_START = "com.celoext.START"
        const val ACTION_STOP = "com.celoext.STOP"
        private const val CHANNEL = "server"
        private const val ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val wake by lazy { WorkWake(this, scope, "ocmobile:server") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(ID, notification("Starting server…"))
        if (intent?.action == ACTION_STOP) {
            Graph.runtime.stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                launch {
                    Graph.runtime.state.collect { s ->
                        val text = when (s) {
                            is RuntimeState.Running -> "OpenCode ${s.version} · localhost:${Graph.prefs.serverPort}"
                            is RuntimeState.Crashed -> "Server crashed"
                            else -> "Starting server…"
                        }
                        getSystemService(NotificationManager::class.java).notify(ID, notification(text))
                    }
                }
                launch { followWork() }
                Graph.runtime.supervise()
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** Holds the CPU only while there is work; see [needsWakeLock]. */
    private suspend fun followWork() {
        combine(
            Graph.runtime.state,
            Graph.chats.statuses,
            Graph.chats.sending,
            snapshotFlow { Graph.prefs.keepAwake },
        ) { state, statuses, sending, keepAwake ->
            needsWakeLock(
                keepAwake = keepAwake,
                starting = state is RuntimeState.Starting || state is RuntimeState.Crashed,
                busySessions = statuses.size,
                sendsInFlight = sending,
            )
        }.distinctUntilChanged().collect { wake.setWork(it) }
    }

    override fun onDestroy() {
        Graph.runtime.requestStop()
        wake.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "OpenCode server", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("OpenCode")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
