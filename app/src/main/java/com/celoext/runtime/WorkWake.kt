package com.celoext.runtime

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the CPU has to stay awake: the switch is on and the runtime is starting, a session is busy,
 * or a prompt is on its way to the server. Idle is not a reason to keep a phone awake.
 */
internal fun needsWakeLock(keepAwake: Boolean, starting: Boolean, busySessions: Int, sendsInFlight: Int): Boolean =
    keepAwake && (starting || busySessions > 0 || sendsInFlight > 0)

/**
 * A partial wake lock that follows work instead of the life of the service.
 *
 * The services used to acquire it on start and release it on stop, which meant the CPU never slept
 * from the first `opencode serve` until the user pressed Stop: an idle night cost as much battery as
 * a running task. This holds it while the server boots, while any session is busy and while a prompt
 * is in flight, then releases it after [GRACE_MS] of quiet. Doze may then suspend the app, which is
 * the point, and the event stream reconnects when it wakes.
 */
class WorkWake(context: Context, private val scope: CoroutineScope, private val tag: String) {
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var lock: PowerManager.WakeLock? = null
    private var work = false
    private var closed = false
    private var releaseJob: Job? = null

    fun setWork(value: Boolean) {
        if (value == work || closed) return
        work = value
        releaseJob?.cancel()
        if (value) {
            val l = lock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
                .apply { setReferenceCounted(false) }
                .also { lock = it }
            if (!l.isHeld) l.acquire()
        } else if (lock?.isHeld == true) {
            // A task that starts right after the previous one finished should not pay for a new
            // acquisition, and a phone that went idle should not stay awake a second longer than it must.
            releaseJob = scope.launch {
                delay(GRACE_MS)
                lock?.let { if (it.isHeld) it.release() }
            }
        }
    }

    fun close() {
        closed = true
        releaseJob?.cancel()
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }

    private companion object {
        const val GRACE_MS = 60_000L
    }
}
