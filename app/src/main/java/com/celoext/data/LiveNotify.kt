package com.celoext.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.celoext.Graph
import com.celoext.MainActivity
import com.celoext.R
import com.celoext.api.EventHub
import com.celoext.api.obj
import com.celoext.api.parsePermission
import com.celoext.api.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The agent on the lock screen: while the app is in the background and a session runs, a notification shows
 * the current step ("Editing Composer.kt") and a count of steps and files. On Android 16 it becomes
 * a Live Update (a chip in the status bar). When the agent asks for permission, a separate notification
 * with Allow / Always / Deny buttons lets you answer right from the lock screen.
 */
object LiveNotify {
    private const val CH_LIVE = "live"
    private const val CH_ASK = "ask"
    private const val CH_ALERT = "alerts"

    /** No visible event for this long means the agent is probably stuck. A running tool gets longer. */
    private const val STALL_MS = 5 * 60_000L
    private const val TOOL_STALL_MS = 15 * 60_000L

    private class Run(
        var step: String = "Thinking…",
        var steps: Int = 0,
        val files: MutableSet<String> = linkedSetOf(),
        val started: Long = System.currentTimeMillis(),
        var lastProgress: Long = System.currentTimeMillis(),
        var toolSince: Long? = null,
        var waiting: Boolean = false,
        var alerted: Boolean = false,
    )
    private val runs = HashMap<String, Run>()
    private val retryAlertedAt = HashMap<String, Long>()

    private val ctx: Context get() = Graph.app
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    fun start() {
        if (nm.getNotificationChannel(CH_LIVE) == null) nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Agent at work", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
        if (nm.getNotificationChannel(CH_ASK) == null) nm.createNotificationChannel(NotificationChannel(CH_ASK, "Agent needs your OK", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        if (nm.getNotificationChannel(CH_ALERT) == null) nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Agent alerts", NotificationManager.IMPORTANCE_DEFAULT))
        Graph.scope.launch { Graph.events.events.collect { runCatching { handle(it) } } }
        // The agent can go quiet without finishing: a dead model call, a lost stream, a tool that never returns.
        // Check every half minute and speak up once per run; any new event rearms the watchdog.
        Graph.scope.launch {
            while (isActive) {
                delay(30_000)
                runCatching { checkStalls() }
            }
        }
    }

    /** The app is on screen again: progress and stale attention notifications are no longer needed. */
    fun onForeground() {
        runs.keys.forEach { nm.cancel(liveId(it)); nm.cancel(alertId(it)) }
        retryAlertedAt.clear()
    }

    /** The app went to the background: show what's running now. */
    fun onBackground() = runs.keys.toList().forEach(::show)

    private fun liveId(sid: String) = 0x4000 + (sid.hashCode() and 0xfff)
    private fun askId(rid: String) = 0x6000 + (rid.hashCode() and 0xfff)
    private fun alertId(sid: String) = 0x7000 + (sid.hashCode() and 0xfff)

    private val waitingRequests = HashMap<String, String>()

    private fun handle(e: EventHub.Event) {
        val p = e.props
        when (e.type) {
            "session.status" -> {
                val sid = p.str("sessionID") ?: return
                val st = p.obj("status")
                val type = st?.str("type")
                if (type == "idle") done(sid) else {
                    runs.getOrPut(sid) { Run() }
                    touch(sid)
                    if (type == "retry" && st != null) retry(sid, st)
                    show(sid)
                }
            }
            "session.idle" -> p.str("sessionID")?.let(::done)
            "session.error" -> p.str("sessionID")?.let(::touch)
            "message.part.updated" -> {
                val part = p.obj("part") ?: return
                val sid = part.str("sessionID") ?: return
                val r = runs[sid] ?: return
                touch(sid)
                when (part.str("type")) {
                    "tool" -> {
                        val tool = part.str("tool") ?: return
                        val st = part.obj("state")
                        val input = st?.obj("input")
                        when (st?.str("status")) {
                            "running", "pending" -> {
                                r.step = label(tool, input)
                                if (r.toolSince == null) r.toolSince = System.currentTimeMillis()
                            }
                            "completed" -> {
                                r.toolSince = null
                                r.steps++
                                if (tool in setOf("edit", "write", "apply_patch", "patch", "multiedit")) input?.str("filePath")?.let { r.files += it }
                            }
                            else -> r.toolSince = null
                        }
                    }
                    "reasoning" -> r.step = "Thinking…"
                    "text" -> r.step = "Writing the answer"
                }
                show(sid)
            }
            "message.part.delta" -> p.str("sessionID")?.let(::touch)
            "message.updated" -> p.obj("info")?.str("sessionID")?.let(::touch)
            "session.step.started", "session.step.ended", "session.step.failed" -> p.str("sessionID")?.let(::touch)
            "permission.asked" -> {
                val req = parsePermission(p)
                // Auto-allowed requests are answered by ChatHub; only a manual one makes the run wait for the user.
                if (!Graph.chats.autoAllows(req.permission)) {
                    waitFor(req.sessionID, req.id)
                    if (!Graph.chats.foreground && Graph.prefs.liveNotify) ask(req, e.directory)
                }
            }
            "permission.replied" -> p.str("requestID")?.let { rid -> nm.cancel(askId(rid)); answered(rid) }
            "question.asked" -> {
                val q = com.celoext.api.parseQuestion(p)
                waitFor(q.sessionID, q.id)
                if (!Graph.chats.foreground && Graph.prefs.liveNotify) question(p)
            }
            "question.replied", "question.rejected" -> p.str("requestID")?.let { rid -> nm.cancel(askId(rid)); answered(rid) }
        }
    }

    /** New output or a new step: the run is alive, so the watchdog starts over. */
    private fun touch(sid: String) {
        val r = runs[sid] ?: return
        r.lastProgress = System.currentTimeMillis()
        r.alerted = false
    }

    private fun waitFor(sid: String, rid: String) {
        waitingRequests[rid] = sid
        runs[sid]?.waiting = true
    }

    private fun answered(rid: String) {
        val sid = waitingRequests.remove(rid) ?: return
        runs[sid]?.let {
            it.waiting = false
            it.lastProgress = System.currentTimeMillis()
            it.alerted = false
        }
    }

    private fun checkStalls() {
        val now = System.currentTimeMillis()
        if (!Graph.prefs.liveNotify || Graph.chats.foreground) return
        for ((sid, r) in runs.toList()) {
            if (Graph.chats.isIncognito(sid)) continue
            if (!stallDue(now, r.lastProgress, r.toolSince, r.waiting, r.alerted, STALL_MS, TOOL_STALL_MS)) continue
            r.alerted = true
            val limit = if (r.toolSince != null) TOOL_STALL_MS else STALL_MS
            alert(sid, "OpenCode may be stuck", "No progress for ${limit / 60_000} minutes. Tap to check the chat.")
        }
    }

    private fun done(sid: String) {
        runs.remove(sid)
        retryAlertedAt.remove(sid)
        waitingRequests.entries.removeAll { it.value == sid }
        nm.cancel(liveId(sid))
        nm.cancel(alertId(sid))
    }

    private fun base(name: String) = name.substringAfterLast('/')

    private fun label(tool: String, input: kotlinx.serialization.json.JsonObject?): String = when {
        tool == "bash" -> input?.str("description")?.let { it.replaceFirstChar { c -> c.uppercase() } } ?: "Running a command"
        tool == "read" -> "Reading " + (input?.str("filePath")?.let(::base) ?: "a file")
        tool in setOf("edit", "write", "apply_patch", "patch", "multiedit") -> "Editing " + (input?.str("filePath")?.let(::base) ?: "files")
        tool in setOf("glob", "grep", "list") -> "Searching the code"
        tool == "webfetch" -> "Reading a web page"
        tool == "websearch" -> "Searching the web"
        tool == "task" -> "Working with a subagent"
        tool == "todowrite" -> "Planning the steps"
        tool == "skill" -> "Using a skill"
        tool.startsWith("${PhoneBridge.NAME}_") -> "Using the phone"
        else -> "Using $tool"
    }

    private fun openChat(sid: String) = PendingIntent.getActivity(
        ctx, sid.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("session", sid).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(kind: String, rid: String, sid: String, dir: String?) = PendingIntent.getBroadcast(
        ctx, (kind + rid).hashCode(),
        Intent(ctx, Action::class.java).putExtra("kind", kind).putExtra("rid", rid).putExtra("sid", sid).putExtra("dir", dir),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun show(sid: String) {
        val r = runs[sid] ?: return
        if (!Graph.prefs.liveNotify || Graph.chats.foreground || Graph.chats.isIncognito(sid)) { nm.cancel(liveId(sid)); return }
        val title = Graph.chats.session(sid)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        val counts = listOfNotNull(
            "${r.steps} ${if (r.steps == 1) "step" else "steps"}",
            r.files.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "file" else "files"}" },
        ).joinToString(" · ")
        val b = Notification.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(r.step).setContentText(title).setSubText(counts)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setUsesChronometer(true).setWhen(r.started).setShowWhen(true)
            .setContentIntent(openChat(sid))
            .addAction(Notification.Action.Builder(null, "Stop", action("stop", "", sid, Graph.chats.session(sid)?.directory)).build())
            .setPublicVersion(Notification.Builder(ctx, CH_LIVE).setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode is working").setContentText(counts).build())
            .setProgress(0, 0, true)
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16: Live Update, a chip in the status bar and a spot at the top of the shade
            b.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            b.setShortCriticalText(if (r.files.isNotEmpty()) "${r.files.size} files" else "Working")
            runCatching { b.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(b, true) }
        }
        nm.notify(liveId(sid), b.build())
    }

    private fun retry(sid: String, status: kotlinx.serialization.json.JsonObject) {
        val now = System.currentTimeMillis()
        if (now - (retryAlertedAt[sid] ?: 0L) < 120_000L) return
        retryAlertedAt[sid] = now
        alert(sid, "OpenCode is retrying", status.str("message") ?: "The model did not answer. Retrying…")
    }

    private fun alert(sid: String, title: String, text: String) {
        if (!Graph.prefs.liveNotify || Graph.chats.foreground || Graph.chats.isIncognito(sid)) return
        val sessionTitle = Graph.chats.session(sid)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        nm.notify(alertId(sid), Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text).setSubText(sessionTitle)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openChat(sid)).setAutoCancel(true).build())
    }

    private fun ask(req: com.celoext.api.PermissionReq, eventDir: String?) {
        val dir = eventDir ?: Graph.chats.session(req.sessionID)?.directory
        val what = when (req.permission) {
            "bash" -> "Run: " + (req.metadata.str("command") ?: req.patterns.joinToString(" "))
            "edit", "write" -> "Edit " + req.patterns.joinToString(", ") { base(it) }
            "webfetch" -> "Open " + req.patterns.joinToString(", ")
            "external_directory" -> "Work outside the project: " + req.patterns.joinToString(", ")
            else -> req.permission.replaceFirstChar { it.uppercase() } + " " + req.patterns.joinToString(", ")
        }.take(300)
        val title = Graph.chats.session(req.sessionID)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        nm.notify(askId(req.id), Notification.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode needs your OK").setContentText(what).setSubText(title)
            .setStyle(Notification.BigTextStyle().bigText(what))
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(ctx, CH_ASK).setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode needs your OK").build())
            .setContentIntent(openChat(req.sessionID)).setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Allow", action("once", req.id, req.sessionID, dir)).build())
            .addAction(Notification.Action.Builder(null, "Always", action("always", req.id, req.sessionID, dir)).build())
            .addAction(Notification.Action.Builder(null, "Deny", action("reject", req.id, req.sessionID, dir)).build())
            .build())
    }

    private fun question(p: kotlinx.serialization.json.JsonObject) {
        val q = com.celoext.api.parseQuestion(p)
        val text = q.questions.firstOrNull()?.question ?: "OpenCode has a question"
        nm.notify(askId(q.id), Notification.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode is asking you").setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openChat(q.sessionID)).setAutoCancel(true).build())
    }

    /** Notification buttons: a permission answer or stopping the session. */
    class Action : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val kind = i.getStringExtra("kind") ?: return
            val rid = i.getStringExtra("rid").orEmpty()
            val sid = i.getStringExtra("sid").orEmpty()
            val dir = i.getStringExtra("dir")
            val pr = goAsync()
            Graph.scope.launch {
                try {
                    if (kind == "stop") { runCatching { Graph.api.abort(sid, dir) }; done(sid) }
                    else {
                        Graph.chats.permissions.value = Graph.chats.permissions.value.filterNot { it.id == rid }
                        runCatching { Graph.api.replyPermission(rid, dir, kind, sid) }
                        c.getSystemService(NotificationManager::class.java).cancel(askId(rid))
                    }
                } finally { pr.finish() }
            }
        }
    }
}

/**
 * True when a running session has shown no output for long enough to look stuck.
 * A tool that is still running gets a longer window, because a build or install can be quiet for minutes.
 * Waiting for the user is not stuck, and an alert already sent is not repeated.
 */
internal fun stallDue(
    now: Long,
    lastProgress: Long,
    toolSince: Long?,
    waiting: Boolean,
    alerted: Boolean,
    noToolMs: Long = 5 * 60_000L,
    toolMs: Long = 15 * 60_000L,
): Boolean {
    if (waiting || alerted) return false
    val limit = if (toolSince != null) toolMs else noToolMs
    return now - lastProgress >= limit
}
