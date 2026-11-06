package com.celoext.runtime

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.celoext.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Runs the 9Router gateway (https://github.com/decolua/9router) as a second process inside the
 * same Ubuntu rootfs as `opencode serve`. Node.js + the `9router` npm package are installed with
 * apt/npm inside the guest; the server listens on its own port on 127.0.0.1, so the app and the
 * dashboard WebView reach it like any other localhost service.
 */
class NineRouterController(
    private val app: Application,
    val paths: RuntimePaths,
    private val prefs: Prefs,
) {
    companion object {
        const val NPM_PACKAGE = "9router"
        const val DEFAULT_PORT = 20128
        const val BIN = "/usr/local/bin/9router"
    }

    val state = MutableStateFlow<NineRouterState>(
        if (File(paths.rootfs, BIN.removePrefix("/")).exists()) NineRouterState.Stopped else NineRouterState.NotInstalled
    )
    val log = MutableStateFlow<List<String>>(emptyList())

    private val proot = ProotCommand(paths, prefs)
    @Volatile private var process: Process? = null
    @Volatile private var stopRequested = false
    @Volatile private var installing = false

    fun logLine(s: String) {
        android.util.Log.w("Celo9R", s)
        log.update { (it + s).takeLast(600) }
    }

    val port: Int get() = prefs.ninerouterPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
    val url: String get() = "http://127.0.0.1:$port"
    val dashboardUrl: String get() = "$url/dashboard"
    val baseUrlV1: String get() = "$url/v1"

    val password: String get() = prefs.ninerouterPassword
    val jwtSecret: String get() = prefs.ninerouterJwt

    // ---------- install ----------

    /** Installs Node.js (apt) and the 9Router CLI (npm) into the guest rootfs. */
    suspend fun install(onLine: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (installing) return@withContext false
        installing = true
        try {
            state.value = NineRouterState.Installing("Updating apt")
            var code = runGuest(listOf("/bin/bash", "-lc", "apt-get update"), onLine = onLine)
            if (code != 0) { logLine("apt-get update failed (exit $code)") }

            state.value = NineRouterState.Installing("Installing Node.js")
            code = runGuest(listOf("/bin/bash", "-lc", "apt-get install -y --no-install-recommends nodejs npm"), onLine = onLine)
            if (code != 0) error("Could not install Node.js (exit $code). Check the log; the environment needs internet.")

            state.value = NineRouterState.Installing("Installing 9Router")
            // --no-fund --no-audit keep the output readable on a phone screen
            code = runGuest(listOf("/bin/bash", "-lc", "npm install -g $NPM_PACKAGE --no-fund --no-audit"), onLine = onLine)
            if (code != 0) error("Could not install 9Router with npm (exit $code).")

            state.value = NineRouterState.Installing("Checking startup")
            val out = StringBuilder()
            code = runGuest(listOf(BIN, "--version"), timeoutSec = 120) { out.appendLine(it); onLine(it) }
            // `9router --version` runs the CLI under node: a working node is enough of a smoke test
            val nodeOk = runCatching {
                val o = StringBuilder()
                val c = runGuest(listOf("/usr/bin/env", "node", "--version"), timeoutSec = 60) { o.appendLine(it) }
                c == 0 && o.contains(Regex("""\d+\.\d+\.\d+"""))
            }.getOrDefault(false)
            if (!nodeOk) error("Node does not start inside the environment (exit $code): ${out.toString().trim().takeLast(300)}")
            prefs.ninerouterVersion = out.toString().trim()
            logLine("9router: ${prefs.ninerouterVersion}")
            state.value = NineRouterState.Stopped
            true
        } catch (e: Throwable) {
            logLine("9Router install failed: ${e.message}")
            state.value = NineRouterState.Crashed(e.message ?: e.javaClass.simpleName)
            false
        } finally {
            installing = false
        }
    }

    // ---------- run ----------

    suspend fun supervise() = withContext(Dispatchers.IO) {
        stopRequested = false
        var failures = 0
        var windowStart = System.currentTimeMillis()
        while (isActive && !stopRequested) {
            state.value = NineRouterState.Starting
            killLeftover()
            var waited = 0
            while (isActive && portBusy() && waited < 8_000) { delay(250); waited += 250 }
            if (waited > 0) logLine("Waited ${waited}ms for port $port to be free")
            val pb = proot.processBuilder(
                listOf(
                    BIN, "--port", port.toString(), "--host", "127.0.0.1",
                    "--no-browser", "--skip-update", "--log",
                ),
                cwd = "/root",
                extraEnv = mapOf(
                    "INITIAL_PASSWORD" to password,
                    "JWT_SECRET" to jwtSecret,
                    "DATA_DIR" to "/root/.9router",
                    "NODE_ENV" to "production",
                    "NO_UPDATE_NOTIFIER" to "true",
                ),
            )
            val p = try { pb.start() } catch (e: Exception) {
                state.value = NineRouterState.Crashed("Could not start 9Router: ${e.message}")
                return@withContext
            }
            process = p
            val reader = launch { runCatching { p.inputStream.bufferedReader().forEachLine { logLine(it) } } }
            try {
                var version: String? = null
                val deadline = System.currentTimeMillis() + 180_000
                while (isActive && p.isAlive && System.currentTimeMillis() < deadline && version == null) {
                    version = try { healthVersionOrThrow() } catch (e: Exception) { null }
                    if (version == null) delay(500)
                }
                if (version != null) {
                    state.value = NineRouterState.Running(version)
                    failures = 0
                } else if (p.isAlive) {
                    // The CLI is up but /api/health never answered in 3 minutes: restart it with the rest of the log.
                    logLine("9Router started but never answered on port $port — restarting")
                    p.destroy()
                }
                val dog = launch {
                    var bad = 0
                    while (isActive && p.isAlive) {
                        delay(20_000)
                        val ok = runCatching { kotlinx.coroutines.withTimeout(10_000) { healthVersionOrThrow() } }.isSuccess
                        bad = if (ok) 0 else bad + 1
                        if (bad >= 3) { logLine("9Router stopped answering — restarting"); p.destroy(); break }
                    }
                }
                runInterruptible { p.waitFor() }
                dog.cancel()
            } finally {
                p.destroy()
                if (prefs.rootMode) Root.killProot(paths.rootfs.path)
                if (p.isAlive) { runCatching { p.waitFor(3, TimeUnit.SECONDS) }; if (p.isAlive) p.destroyForcibly() }
                process = null
                reader.cancel()
            }
            if (stopRequested) break
            val code = runCatching { p.exitValue() }.getOrDefault(-1)
            logLine("9Router exited (code $code)")
            val now = System.currentTimeMillis()
            if (now - windowStart > 120_000) { failures = 0; windowStart = now }
            failures++
            state.value = NineRouterState.Crashed("9Router stopped (code $code). Restarting…")
            delay((1500L * failures).coerceAtMost(30_000))
        }
        state.value = NineRouterState.Stopped
    }

    @Volatile var userStopped = false
    val serverAlive get() = process != null

    fun portBusy(): Boolean = try {
        java.net.Socket().use { socket -> socket.connect(InetSocketAddress("127.0.0.1", port), 300); true }
    } catch (_: Exception) { false }

    /** Kill leftover 9Router processes (the CLI and the detached server it spawned). */
    fun killLeftover() {
        runCatching {
            val p = proot.processBuilder(
                listOf("/bin/sh", "-c", "pkill -f '[9]router'; sleep 0.4; pkill -9 -f '[9]router'; exit 0"),
                cwd = "/root",
            ).start()
            if (!p.waitFor(8, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        if (prefs.rootMode) runCatching { Root.run("pkill -f '[9]router'", 10) }
    }

    fun requestStop() {
        stopRequested = true
        process?.destroy()
        if (prefs.rootMode) Thread { Root.killProot(paths.rootfs.path) }.start()
        Thread { killLeftover() }.apply { isDaemon = true }.start()
    }

    fun restartServer() {
        stopServer()
        Thread {
            var waited = 0
            while (waited < 10_000 && (serverAlive || portBusy())) { Thread.sleep(200); waited += 200 }
            killLeftover()
            if (prefs.rootMode) Root.killProot(paths.rootfs.path)
            startServer()
        }.apply { isDaemon = true }.start()
    }

    fun startServer() {
        val s = state.value
        if (s is NineRouterState.NotInstalled || s is NineRouterState.Installing) return
        // The environment (rootfs + proot) is shared with the OpenCode server: without it there is nothing to run in.
        if (!paths.rootfsReady) {
            logLine("9Router needs the environment first — install it from Settings → Local server")
            return
        }
        userStopped = false
        ContextCompat.startForegroundService(app, Intent(app, NineRouterService::class.java).setAction(NineRouterService.ACTION_START))
    }

    fun stopServer() {
        userStopped = true
        requestStop()
        app.stopService(Intent(app, NineRouterService::class.java))
        if (state.value !is NineRouterState.NotInstalled) state.value = NineRouterState.Stopped
    }

    /** Run a command inside the same Ubuntu guest as the OpenCode server. */
    suspend fun runGuest(
        argv: List<String>,
        cwd: String = "/root",
        timeoutSec: Long = 900,
        extraEnv: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val p = proot.processBuilder(argv, cwd, extraEnv).start()
        val rd = launch { runCatching { p.inputStream.bufferedReader().forEachLine { onLine(it); logLine(it) } } }
        val done = try { runInterruptible { p.waitFor(timeoutSec, TimeUnit.SECONDS) } } finally { if (p.isAlive) p.destroyForcibly() }
        if (!done) { rd.cancel(); return@withContext -1 }
        rd.join()
        p.exitValue()
    }

    // ---------- health ----------

    private fun healthVersionOrThrow(): String {
        val c = URL("$url/api/health").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 5_000; c.readTimeout = 5_000
            c.setRequestProperty("User-Agent", "opencode-mobile")
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val v = runCatching { org.json.JSONObject(body).optString("version") }.getOrDefault("")
            if (v.isBlank()) "running" else v
        } finally {
            c.disconnect()
        }
    }
}

sealed interface NineRouterState {
    /** Node.js + 9Router are not installed in the environment yet. */
    data object NotInstalled : NineRouterState
    data class Installing(val step: String) : NineRouterState
    data object Stopped : NineRouterState
    data object Starting : NineRouterState
    data class Running(val version: String) : NineRouterState
    data class Crashed(val message: String) : NineRouterState
}
