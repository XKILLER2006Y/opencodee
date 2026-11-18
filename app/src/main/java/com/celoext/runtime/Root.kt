package com.celoext.runtime

import android.system.Os
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root fallback (KernelSU / Magisk). File operations that fail with permission denied are retried
 * once after a `su` chmod/chown opens the path for this app, so with root granted any folder on the
 * phone becomes usable (project dirs, storage areas blocked by OEM rules, and so on).
 *
 * The su probe runs once and is cached; without root everything here is a no-op and callers keep
 * the normal failure, exactly like before.
 */
object Root {
    private val probed = AtomicBoolean(false)
    @Volatile private var hasSu = false

    /** User switch: root fallback can be turned off in Settings (Compatibility section). */
    private val enabled: Boolean get() = runCatching { com.celoext.Graph.prefs.rootFallback }.getOrDefault(true)

    /** True when `su` exists, the user allows root fallback and it answers with uid=0 (KernelSU, Magisk or APatch). */
    fun available(): Boolean {
        if (!enabled) return false
        if (probed.get()) return hasSu
        synchronized(this) {
            if (probed.get()) return hasSu
            hasSu = runCatching {
                val out = execCapture(5) { ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start() }
                out == "0" || out.endsWith(" 0") || out.contains("uid=0")
            }.getOrDefault(false)
            probed.set(true)
            return hasSu
        }
    }

    /** Runs the process, drains its output on a thread, and force-stops it after [timeoutSec] so a hung su can never block. */
    private fun execCapture(timeoutSec: Long, start: () -> Process): String {
        val p = start()
        val text = StringBuilder()
        val reader = Thread {
            runCatching { p.inputStream.bufferedReader().forEachLine { text.appendLine(it) } }
        }.apply { isDaemon = true; start() }
        val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!done) p.destroyForcibly()
        runCatching { reader.join(2_000) }
        return text.toString().trim()
    }

    /** Runs one shell line through su. Returns the exit code, -1 when su is missing or hung. */
    fun run(cmd: String, timeoutSec: Long = 30): Int = runCatching {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = execCapture(timeoutSec) { p }
        if (out.isNotBlank()) android.util.Log.w("Root", out.take(400))
        if (p.isAlive) -1 else p.exitValue()
    }.getOrDefault(-1)

    /**
     * Runs [op] and, when it throws a permission-denied [FileSystemException] and su is available,
     * opens the path for this app (chmod a+rwX on the file and each parent, plus the parents are
     * made searchable) and retries the operation once.
     */
    fun <T> withRetry(file: File?, op: () -> T): T {
        try {
            return op()
        } catch (e: java.io.FileSystemException) {
            if (!e.message.orEmpty().contains("Permission denied", ignoreCase = true) || file == null || !available()) throw e
            val opened = openForApp(file)
            if (!opened) throw e
            android.util.Log.w("Root", "Retried ${file.path} after chmod")
            return op()
        }
    }

    /** chmod a+rwX the path (or mkdir it) plus every missing/denied parent, through su. */
    fun openForApp(file: File): Boolean {
        if (!available()) return false
        val path = file.absolutePath
        // 'test -e' first: chmod on a missing file would fail, but creating it needs the parent open.
        val chain = StringBuilder().apply {
            var d = file
            if (d.isDirectory) append("chmod -R a+rwX '$path'; ")
            else append("[ -e '$path' ] && chmod a+rwX '$path'; ")
            while (true) {
                d = d.parentFile ?: break
                append("chmod a+rwx '${d.absolutePath}'; ")
                if (d.absolutePath == "/" ) break
            }
        }.toString()
        val rc = run("sh -c ${shellQuote(chain)}", timeoutSec = 20)
        if (rc != 0) return false
        return runCatching { Os.access(path, android.system.OsConstants.W_OK) }.getOrDefault(false) || file.canWrite()
    }

    /** Kills a process this app may not own (root-run server, for example). */
    fun kill(pid: Int): Boolean {
        if (!available()) return false
        return run("kill -9 $pid", timeoutSec = 10) == 0
    }

    /** pidof through su, for processes invisible to this app's /proc scan. */
    fun pidsOf(pattern: String): List<Int> = runCatching {
        val out = execCapture(10) { ProcessBuilder("su", "-c", "pidof '$pattern'").redirectErrorStream(true).start() }
        out.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    }.getOrDefault(emptyList())

    /** Spawns [argv] as root, carrying [env] into the root shell (su resets the environment). */
    fun spawn(argv: List<String>, env: Map<String, String>, cwd: String?): Process {
        val exports = env.entries.joinToString(" ") { (k, v) -> "export ${k}=${shellQuote(v)}; " }
        val cd = if (cwd.isNullOrBlank()) "" else "cd ${shellQuote(cwd)} && "
        val inner = "$cd${exports}exec ${argv.joinToString(" ") { shellQuote(it) }}"
        val pb = ProcessBuilder("su", "-c", "/system/bin/sh -c ${shellQuote(inner)}")
        return pb.start()
    }

    fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Runs [argv] normally; when Android refuses (EACCES / SELinux exec denial) and su exists, retries as root. */
    fun spawnWithFallback(argv: List<String>, env: Map<String, String>, cwd: String?, envSetter: (ProcessBuilder) -> Unit): Pair<Process, Boolean> {
        val pb = ProcessBuilder(argv)
        envSetter(pb)
        if (cwd != null) pb.directory(File(cwd))
        val direct = runCatching { pb.start() }
        if (direct.isSuccess) return direct.get() to false
        val why = direct.exceptionOrNull()?.message ?: direct.exceptionOrNull()?.javaClass?.simpleName ?: ""
        if (!available()) throw direct.exceptionOrNull() ?: java.io.IOException("Could not start ${argv.firstOrNull()}")
        android.util.Log.w("Root", "Direct start failed ($why), retrying through su")
        return spawn(argv, env, cwd) to true
    }
}
