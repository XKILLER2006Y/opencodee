package com.celoext.runtime

import com.celoext.data.Vault
import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.celoext.api.OpenCodeApi
import com.celoext.data.Memory
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
import java.util.concurrent.TimeUnit

/**
 * Owns the local server: installs the Ubuntu rootfs and OpenCode, runs
 * `opencode serve` inside proot and keeps the process alive.
 */
class RuntimeController(
    private val app: Application,
    val paths: RuntimePaths,
    private val prefs: Prefs,
    private val api: OpenCodeApi,
) {
    companion object {
        const val ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
        const val ROOTFS_SHA = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"

        // Native OpenCode: the opencode-termux community port (bionic binaries that run on Android
        // without Termux, targetSdk 28 keeps exec from app storage allowed). Pinned release + the
        // four Termux packages it depends on, checksums from the release SHA256SUMS / the repo index.
        const val OCT_REPO = "Spectra010s/opencode-termux"
        const val OCT_VERSION = "2.2.7"
        const val OCT_SHA = "432c809a6e2dcb4ea08be79d96694b755c22129de10d07ff1abf2bebae4c8266"
        fun octUrl(v: String) = "https://github.com/$OCT_REPO/releases/download/v$v/opencode-$v-android-aarch64-bundle.tar.gz"
        const val TERMUX = "https://packages.termux.dev/apt/termux-main/pool/main"
        class Deb(val name: String, val url: String, val sha: String)
        val TERMUX_DEBS = listOf(
            Deb("bun", "$TERMUX/b/bun/bun_1.4.4-3_aarch64.deb", "ceb20de1f3eff7cd1230d80d85e2d916611a89e90a93113d682d1d721591f4d8"),
            Deb("libicu", "$TERMUX/libi/libicu/libicu_79.1_aarch64.deb", "83c980a010e957738e3567127801d66a1b9bd7e404a4fda98788048ee57d606c"),
            Deb("ripgrep", "$TERMUX/r/ripgrep/ripgrep_15.2.0_aarch64.deb", "38e28bc297000517b24702568a483eca7dc3323eb6bdccc9033f031776bdcc6c"),
            Deb("pcre2", "$TERMUX/p/pcre2/pcre2_10.49_aarch64.deb", "c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724"),
        )
    }

    val state = MutableStateFlow<RuntimeState>(
        if (prefs.runtimeReady && paths.nativeReady) RuntimeState.Stopped else RuntimeState.NotInstalled
    )
    val log = MutableStateFlow<List<String>>(emptyList())

    private val proot = ProotCommand(paths, prefs)
    @Volatile private var process: Process? = null
    @Volatile private var stopRequested = false
    @Volatile private var installing = false
    @Volatile private var resetting = false
    private val supervising = java.util.concurrent.atomic.AtomicBoolean(false)

    fun logLine(s: String) {
        android.util.Log.w("CeloOC", s)
        log.update { (it + s).takeLast(600) }
    }

    // ---------- install ----------

    suspend fun install() = withContext(Dispatchers.IO) {
        if (installing || resetting) return@withContext
        installing = true
        try {
            // Rootfs (~800 MB) + OpenCode (~240 MB) + downloads: fail early with a clear message instead of ENOSPC.
            val root = paths.rootfs.parentFile ?: paths.rootfs
            val free = runCatching { android.os.StatFs(root.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (free < 1_500_000_000L) {
                error("Not enough free space: about 1.5 GB is needed, ${free / (1024 * 1024)} MB is free")
            }
            step("Preparing", null)
            paths.libDir.mkdirs()
            File(paths.nativeDir, "libtalloc.so").copyTo(File(paths.libDir, "libtalloc.so.2"), overwrite = true)

            if (!paths.rootfsReady) {
                val tgz = File(paths.downloads, "ubuntu-base.tar.gz")
                step("Downloading environment", 0f)
                Downloader.fetch(ROOTFS_URL, tgz, ROOTFS_SHA) { d, t -> step("Downloading environment", frac(d, t), mb(d, t)) }
                step("Unpacking environment", 0f)
                paths.rootfs.deleteRecursively()
                val r = TarExtractor.extract(tgz, paths.rootfs) { d, t -> step("Unpacking environment", frac(d, t)) }
                logLine("rootfs: ${r.files} files, skipped ${r.skipped}")
                configureRootfs()
                prefs.seededSkills = "" // fresh Ubuntu: the default skills have to be copied in again
                tgz.delete()
            }

            Vault.restore()
            Memory.sync() // copies the default skills and writes their rules into AGENTS.md
            installNative()
            nativeSmokeTest()

            prefs.runtimeReady = true
            state.value = RuntimeState.Stopped
        } catch (e: Throwable) {
            logLine("Install failed: ${e.message}")
            state.value = RuntimeState.InstallFailed(e.message ?: e.javaClass.simpleName)
        } finally {
            installing = false
        }
    }

    /**
     * Native runtime, no proot: the opencode-termux bundle (a sh launcher + the JS module graph +
     * bionic native libraries) plus the Termux packages it runs on — bun, libicu, ripgrep, pcre2.
     * Everything is downloaded with pinned sha-256s and unpacked with our own extractors.
     */
    private fun installNative() {
        if (paths.opencodeNativeBin.isFile && !runCatching { File(paths.opencodeBundle, "index.js").isFile }.getOrDefault(false)) {
            paths.bundleDir.deleteRecursively()
        }
        if (!paths.opencodeNativeBin.isFile) {
            step("Downloading OpenCode", 0f)
            val tgz = File(paths.downloads, "opencode-native-$OCT_VERSION.tar.gz")
            Downloader.fetch(octUrl(OCT_VERSION), tgz, OCT_SHA) { d, t -> step("Downloading OpenCode", frac(d, t), mb(d, t)) }
            step("Installing OpenCode", 0f)
            paths.bundleDir.deleteRecursively()
            TarExtractor.extract(tgz, paths.bundleDir) { d, t -> step("Installing OpenCode", frac(d, t)) }
            tgz.delete()
            // The bundle is built against @TERMUX_PREFIX@ so one artefact fits every Termux
            // prefix; point it at our own directory instead (same as the upstream installer).
            val marker = "@TERMUX_PREFIX@/lib/opencode"
            val ours = paths.opencodeBundle.path
            var patched = 0
            paths.bundleDir.walkTopDown().filter { it.isFile && it.length() < 40 * 1_000_000 }.forEach { f ->
                // Never rewrite binaries: a stray marker inside a .so would corrupt them.
                if (f.extension in setOf("so", "node", "bin")) return@forEach
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                if (text.contains(marker)) {
                    f.writeText(text.replace(marker, ours))
                    patched++
                }
            }
            logLine("bundle: patched $patched files from @TERMUX_PREFIX@ to ${paths.bundleDir.path}")
            val leftover = paths.bundleDir.walkTopDown()
                .filter { it.isFile && it.length() < 40 * 1_000_000 && it.extension !in setOf("so", "node", "bin") }
                .any { runCatching { it.readText().contains("@TERMUX_PREFIX@") }.getOrDefault(false) }
            if (leftover) error("Release bundle still contains placeholder paths; it cannot run here")
            // The launcher ships with a Termux shebang; Android's /system/bin/sh is the equivalent here.
            val launcher = paths.opencodeNativeBin
            val shebang = "#!/system/bin/sh\n"
            runCatching {
                val text = launcher.readText()
                if (text.startsWith("#!/data/data/com.termux")) launcher.writeText(shebang + text.substringAfter('\n'))
            }
            if (runCatching { launcher.readText().startsWith("#!/system/bin/sh") }.getOrDefault(false)) {
                logLine("launcher: shebang patched to /system/bin/sh")
            } else {
                // Unexpected launcher layout: bypass the script at runtime and exec bun directly.
                logLine("launcher: unexpected format, the server will exec bun directly")
            }
            launcher.setExecutable(true, false)
        }
        // The Termux packages the bundle runs on. Nothing here is apt: pure downloads + unzip.
        val usrRoot = File(paths.nativeUsr, "data/data/com.termux/files/usr")
        for (deb in TERMUX_DEBS) {
            val have = when (deb.name) {
                "bun" -> File(usrRoot, "bin/bun").isFile
                "libicu" -> File(usrRoot, "lib/libicuuc.so.79.1").isFile
                "ripgrep" -> File(usrRoot, "bin/rg").isFile
                "pcre2" -> File(usrRoot, "lib/libpcre2-8.so").isFile
                else -> false
            }
            if (have) { logLine("termux ${deb.name}: already installed"); continue }
            step("Downloading ${deb.name}", 0f)
            val f = File(paths.downloads, "${deb.name}-${deb.sha.take(8)}.deb")
            Downloader.fetch(deb.url, f, deb.sha) { d, t -> step("Downloading ${deb.name}", frac(d, t), mb(d, t)) }
            step("Installing ${deb.name}", 0f)
            val r = DebExtractor.extract(f, paths.nativeUsr) { d, t -> step("Installing ${deb.name}", frac(d, t)) }
            logLine("termux ${deb.name}: ${r.files} files")
            f.delete()
        }
        if (!File(usrRoot, "bin/bun").isFile) error("bun did not land in the Termux layout; install failed")
        paths.tmp.mkdirs()
        paths.nativeHome.mkdirs()
        // One-time migration: an existing proot install keeps its /root inside the old rootfs.
        val oldRoot = File(paths.rootfs, "root")
        if (oldRoot.isDirectory && oldRoot.list()?.isNotEmpty() == true && paths.nativeHome.list()?.isEmpty() != false) {
            logLine("migrating the old /root into the native home")
            runCatching { oldRoot.copyRecursively(paths.nativeHome, overwrite = false) }
        }
        paths.nativeHome.resolve("work").mkdirs()
        paths.nativeHome.resolve(".config/opencode").mkdirs()
        paths.nativeHome.resolve("uploads").mkdirs()
        paths.nativeHome.resolve("outbox").mkdirs()
        prefs.opencodeVersion = OCT_VERSION
    }

    /** The environment the native server runs with: XDG paths all live in the native home. */
    private fun nativeEnv(): Map<String, String> = mapOf(
        "HOME" to paths.nativeHome.path,
        "XDG_CONFIG_HOME" to File(paths.nativeHome, ".config").path,
        "XDG_DATA_HOME" to File(paths.nativeHome, ".local/share").path,
        "XDG_STATE_HOME" to File(paths.nativeHome, ".local/state").path,
        "XDG_CACHE_HOME" to File(paths.nativeHome, ".cache").path,
        "TMPDIR" to paths.tmp.path,
        "PATH" to "${paths.nativeBin.path}:/system/bin:/system/xbin",
        "LD_LIBRARY_PATH" to "${paths.nativeLib.path}:${paths.nativeDir}",
        "LANG" to "C.UTF-8",
        "TERM" to "xterm-256color",
        "SHELL" to "/system/bin/sh",
        "OPENCODE_BUN" to File(paths.nativeBin, "bun").path,
        "OPENCODE_ROOT" to paths.opencodeBundle.path,
        "OPENCODE_DISABLE_AUTOUPDATE" to "true",
    )

    /** Runs a native binary directly (bun etc.): same guardrails as runGuest, no proot. */
    private suspend fun runNative(
        argv: List<String>,
        timeoutSec: Long = 120,
        onLine: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val env = nativeEnv()
        val (p, viaRoot) = Root.spawnWithFallback(argv, env, paths.nativeHome.path) { pb -> pb.environment().putAll(env); pb.redirectErrorStream(true) }
        if (viaRoot) logLine("started through su (root)")
        val rd = launch { runCatching { p.inputStream.bufferedReader().forEachLine { onLine(it); logLine(it) } } }
        val done = try { runInterruptible { p.waitFor(timeoutSec, TimeUnit.SECONDS) } } finally { if (p.isAlive) p.destroyForcibly() }
        if (!done) { rd.cancel(); return@withContext -1 }
        rd.join()
        p.exitValue()
    }

    private suspend fun nativeSmokeTest() {
        step("Checking startup", null)
        val out = StringBuilder()
        val code = runNative(listOf(paths.opencodeNativeBin.path, "--version"), timeoutSec = 180) { out.appendLine(it) }
        if (code != 0 || !Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(out.toString())) {
            error("opencode does not start (code $code): ${out.toString().trim().takeLast(400)}")
        }
        logLine("opencode native: ${out.toString().trim()}")
    }

    private fun configureRootfs() {
        fun w(guest: String, text: String) {
            val f = paths.host(guest)
            f.parentFile?.mkdirs()
            f.delete()
            f.writeText(text)
        }
        w("/etc/resolv.conf", "nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        w("/etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
        w("/etc/hostname", "opencode-mobile\n")
        // apt inside proot can't drop privileges to the _apt user.
        w("/etc/apt/apt.conf.d/99proot", "APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\n")
        val pem = app.assets.open("cacert.pem").use { it.readBytes() }
        for (p in listOf("/etc/ssl/certs/ca-certificates.crt", "/etc/ssl/cert.pem")) {
            val f = paths.host(p); f.parentFile?.mkdirs(); f.delete(); f.writeBytes(pem)
        }
        paths.host("/root/work").mkdirs()
        paths.host("/root/.config/opencode").mkdirs()
        paths.host("/tmp").mkdirs()
        File(paths.rootfs, ".oc-configured").writeText("1")
    }

    private fun step(name: String, p: Float?, detail: String = "") {
        state.value = RuntimeState.Installing(name, p, detail)
    }
    private fun frac(d: Long, t: Long) = if (t > 0) (d.toFloat() / t).coerceIn(0f, 1f) else null
    private fun mb(d: Long, t: Long) = if (t > 0) "%.0f / %.0f MB".format(d / 1e6, t / 1e6) else "%.0f MB".format(d / 1e6)

    // ---------- run ----------

    suspend fun supervise() = withContext(Dispatchers.IO) {
        // Two loops (the service got recreated while the old loop was still winding down) take turns
        // killing each other's server and crash-loop on "port 4096 in use". Only one may run.
        if (!supervising.compareAndSet(false, true)) return@withContext
        try {
            superviseLoop()
        } finally {
            supervising.set(false)
        }
    }

    private suspend fun superviseLoop() = withContext(Dispatchers.IO) {
        stopRequested = false
        var failures = 0
        var windowStart = System.currentTimeMillis()
        while (isActive && !stopRequested) {
            state.value = RuntimeState.Starting
            // leftovers from the run before (or a crash) would keep the port: kill them first
            freePort()
            if (portBusy()) logLine("Port ${prefs.serverPort} is still busy, trying to start anyway")
            val argv = listOf(paths.opencodeNativeBin.path, "serve", "--hostname", "127.0.0.1", "--port", prefs.serverPort)
            val env = nativeEnv() + mapOf("OPENCODE_SERVER_PASSWORD" to prefs.serverPassword)
            val (p, viaRoot) = try {
                Root.spawnWithFallback(argv, env, paths.serverPath("/root/work")) { pb ->
                    pb.environment().putAll(env); pb.redirectErrorStream(true)
                }
            } catch (e: Exception) {
                state.value = RuntimeState.Crashed("Could not start the server: ${e.message}")
                return@withContext
            }
            if (viaRoot) logLine("Server started through su (root)")
            process = p
            val reader = launch { runCatching { p.inputStream.bufferedReader().forEachLine { logLine(it) } } }
            try {
                var version: String? = null
                val deadline = System.currentTimeMillis() + 120_000
                while (isActive && p.isAlive && System.currentTimeMillis() < deadline && version == null) {
                    val t0 = System.currentTimeMillis()
                    version = try { api.healthVersionOrThrow() } catch (e: Exception) { android.util.Log.d("ocm-health", "err ${e.javaClass.simpleName} ${e.message}"); logLine("health: ${e.javaClass.simpleName} ${e.message}"); null }
                    android.util.Log.d("ocm-health", "version=$version in ${System.currentTimeMillis() - t0}ms")
                    if (version == null) delay(250)
                }
                if (version != null) {
                    state.value = RuntimeState.Running(version)
                    prefs.opencodeVersion = version
                    failures = 0
                }
                // Watchdog: the process is alive but not answering (hung), so restart it instead of waiting forever.
                val dog = launch {
                    var bad = 0
                    while (isActive && p.isAlive) {
                        delay(15_000)
                        val ok = runCatching { kotlinx.coroutines.withTimeout(10_000) { api.healthVersionOrThrow() } }.isSuccess
                        bad = if (ok) 0 else bad + 1
                        if (bad >= 3) { logLine("Server stopped answering — restarting"); p.destroy(); break }
                    }
                }
                runInterruptible { p.waitFor() }
                dog.cancel()
            } finally {
                p.destroy()
                if (p.isAlive) { runCatching { p.waitFor(3, TimeUnit.SECONDS) }; if (p.isAlive) p.destroyForcibly() }
                process = null
                reader.cancel()
            }
            if (stopRequested) break
            val code = runCatching { p.exitValue() }.getOrDefault(-1)
            logLine("Server exited (code $code)")
            val now = System.currentTimeMillis()
            if (now - windowStart > 120_000) { failures = 0; windowStart = now }
            failures++
            state.value = RuntimeState.Crashed("Server stopped (code $code). Restarting…")
            // don't give up: restart with a growing pause (up to 30 s) until the user stops the server
            delay((1500L * failures).coerceAtMost(30_000))
        }
        state.value = RuntimeState.Stopped
    }

    /**
     * Free the server port before starting a new server.
     * The previous `opencode serve` (or its bun process after a crash) holds the port, which crashed
     * every new server with "Is port 4096 in use?". The /proc scan kills them, with su as fallback
     * for a root-run server this app cannot signal.
     */
    private suspend fun freePort(attempts: Int = 3) {
        for (i in 0 until attempts) {
            if (!portBusy()) return
            if (i > 0 || !waitPortFree(2_000)) {
                logLine("Port ${prefs.serverPort} still busy, killing leftover server (attempt ${i + 1})")
                killLeftoverJava()
                if (waitPortFree(4_000)) return
                Root.pidsOf("bun").filter { pid -> isLeftoverServer(pid) }.forEach { Root.kill(it) }
                if (waitPortFree(2_000)) return
            }
        }
    }

    private suspend fun waitPortFree(maxWaitMs: Long): Boolean {
        var waited = 0
        while (portBusy() && waited < maxWaitMs) { delay(250); waited += 250 }
        return !portBusy()
    }

    /** True when [pid]'s cmdline is one of our server processes (bun running the bundle, or a proot-era leftover). */
    private fun isLeftoverServer(pid: Int): Boolean {
        val cmd = runCatching { java.io.File("/proc/$pid/cmdline").readBytes().decodeToString().replace('\u0000', ' ').trim() }.getOrDefault("")
        return cmd.contains("lib/opencode/index.js") || cmd.contains("/usr/local/bin/opencode") || cmd.contains(paths.opencodeNativeBin.path)
    }

    /**
     * Kills leftover server processes found by scanning /proc: the guest processes (and the native
     * bun) run with this app's UID, so their cmdline is readable and they can be signalled directly.
     */
    private fun killLeftoverJava() {
        runCatching {
            for (p in java.io.File("/proc").listFiles() ?: emptyArray()) {
                val pid = p.name.toIntOrNull() ?: continue
                if (pid == android.os.Process.myPid()) continue
                val cmd = runCatching { java.io.File(p, "cmdline").readBytes().decodeToString().replace('\u0000', ' ').trim() }.getOrDefault("")
                if (cmd.isEmpty()) continue
                // The bun server cmdline carries the bundle entry (lib/opencode/index.js); the legacy
                // proot-era binary carried /usr/local/bin/opencode. Nothing else of ours matches.
                if (cmd.contains("lib/opencode/index.js") || cmd.contains("/usr/local/bin/opencode") || cmd.contains(paths.opencodeNativeBin.path)) {
                    val killed = runCatching { android.os.Process.killProcess(pid) }.getOrDefault(false)
                    if (!killed || isAlive(pid)) Root.kill(pid) // a root-run server answers only to su
                    logLine("Killed leftover server process $pid: ${cmd.take(120)}")
                }
            }
        }
    }

    private fun isAlive(pid: Int): Boolean = runCatching { java.io.File("/proc/$pid").exists() }.getOrDefault(false)

    /** The user stopped the server: then we don't start it when they come back to the app. */
    @Volatile var userStopped = false

    /** The server process is still alive (after stopServer it doesn't exit right away). */
    val serverAlive get() = process != null

    /** Something is still listening on the server port (a leftover `opencode serve` from an earlier run). */
    fun portBusy(): Boolean {
        val port = prefs.serverPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return false
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestStop() {
        stopRequested = true
        process?.destroy()
        // in the background: scanning /proc and running su take a moment and this can be called from the main thread
        Thread { killLeftoverJava() }.apply { isDaemon = true }.start()
    }

    /** Stop, wait until the port is really free, start again (Restart button). */
    fun restartServer(beforeStart: () -> Unit = {}) {
        stopServer()
        Thread {
            var waited = 0
            while (waited < 10_000 && (serverAlive || portBusy())) { Thread.sleep(200); waited += 200 }
            killLeftoverJava()
            runCatching { beforeStart() }
            startServer()
        }.apply { isDaemon = true }.start()
    }


    /** Start the server through a foreground service, otherwise Android kills the process in the background. */
    fun startServer() {
        if (resetting) return
        val s = state.value
        if (s is RuntimeState.NotInstalled || s is RuntimeState.Installing || s is RuntimeState.InstallFailed) return
        userStopped = false
        ContextCompat.startForegroundService(app, Intent(app, ServerService::class.java).setAction(ServerService.ACTION_START))
    }

    fun stopServer() {
        userStopped = true
        requestStop()
        app.stopService(Intent(app, ServerService::class.java))
        if (state.value !is RuntimeState.NotInstalled) state.value = RuntimeState.Stopped
    }

    /** Run a command inside Ubuntu (installing git and so on). */
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

    /** git, ripgrep and the like: without them the agent in Ubuntu is nearly useless. */
    suspend fun installDevTools(onLine: (String) -> Unit): Int {
        val script = "apt-get update && apt-get install -y --no-install-recommends git ripgrep curl ca-certificates openssh-client unzip less nano python3 build-essential"
        return runGuest(listOf("/bin/bash", "-lc", script), onLine = onLine)
    }

    fun resetRuntime() {
        if (resetting) return
        resetting = true
        // Deleting the environment is hundreds of megabytes and thousands of files: not on the main thread.
        Thread {
            try {
                stopServer()
                var waited = 0
                while (waited < 10_000 && (serverAlive || portBusy())) { Thread.sleep(200); waited += 200 }
                killLeftoverJava()
                // The chat database is only consistent once the server has stopped writing.
                Vault.save()
                // Wipe the native runtime (bundle, termux debs, home) too; the rootfs stays for 9router.
                paths.native.deleteRecursively()
                paths.rootfs.deleteRecursively()
                paths.downloads.deleteRecursively()
                prefs.runtimeReady = false
            } finally {
                resetting = false
                state.value = RuntimeState.NotInstalled
            }
        }.apply { isDaemon = true }.start()
    }
}
