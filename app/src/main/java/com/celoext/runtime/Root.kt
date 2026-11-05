package com.celoext.runtime

import java.util.concurrent.TimeUnit

/** Optional root (su, Magisk / KernelSU): lets proot see other apps' data such as the Termux home. */
object Root {
    const val TERMUX_HOME = "/data/user/0/com.termux/files/home"

    /** Result of running [cmd] through `su -c`: exit code (-1 = no su / timeout) and output. */
    fun run(cmd: String, timeoutSec: Long = 30): Pair<Int, String> = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val t = Thread { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }.apply { isDaemon = true; start() }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); -1 to "timeout" }
        else { t.join(2000); p.exitValue() to out.toString() }
    } catch (e: Exception) { -1 to (e.message ?: "su not found") }

    /** Asks for root (Magisk shows its prompt here). 60 s: the user has to tap Grant. */
    fun check(): Boolean = run("id -u", 60).let { it.first == 0 && it.second.trim().endsWith("0") }

    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** The shell line for `su -c`: loose umask (the app must still be able to use files created as root), clean env, exec. */
    fun script(env: Map<String, String>, argv: List<String>): String =
        "umask 000; exec env " + env.entries.joinToString(" ") { "${it.key}=${quote(it.value)}" } + " " + argv.joinToString(" ") { quote(it) }

    @Volatile private var termux: Boolean? = null
    /** Whether the Termux home exists (checked as root; the app itself can't see it). */
    fun termuxHomeExists(): Boolean = termux ?: (run("test -d ${quote(TERMUX_HOME)}", 15).first == 0).also { termux = it }
    fun forgetProbe() { termux = null }

    /** Kills proot processes of this app's rootfs: killing the `su` client doesn't always kill its child. */
    fun killProot(rootfs: String) { run("pkill -f -- ${quote("[-]-rootfs=$rootfs")}", 10) }

    /** Gives files created while running as root back to the app. */
    fun handBack(dir: String, uid: Int) { run("chown -R $uid:$uid ${quote(dir)}", 300) }
}
