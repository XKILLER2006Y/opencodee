package com.celoext.runtime

import android.content.Context
import java.io.File

/** Where things live: proot from jniLibs (executable), rootfs and temp files in filesDir. */
class RuntimePaths(ctx: Context) {
    val nativeDir: String = ctx.applicationInfo.nativeLibraryDir
    val proot = File(nativeDir, "libproot.so")
    val prootLoader = File(nativeDir, "libproot-loader.so")

    private val base: File = ctx.filesDir
    val rootfs = File(base, "rootfs")
    /** A copy of libtalloc.so named libtalloc.so.2: jniLibs can't hold such names. */
    val libDir = File(base, "lib")
    val tmp = File(base, "tmp")
    val fake = File(base, "fake")
    val downloads = File(base, "dl")

    // Native (opencode-termux) runtime: no proot, the bionic binaries run directly from app storage.
    val native = File(base, "native")
    /** Full Termux deb layout lands here: <this>/data/data/com.termux/files/usr/{bin,lib}. */
    val nativeUsr = File(native, "termux")
    /** The bin/lib inside the extracted Termux layout: the env PATH/LD_LIBRARY_PATH point here. */
    val nativeBin = File(nativeUsr, "data/data/com.termux/files/usr/bin")
    val nativeLib = File(nativeUsr, "data/data/com.termux/files/usr/lib")
    /** The bundle from the opencode-termux release: bin/opencode (launcher) + lib/opencode (JS + native libs). */
    val bundleDir = File(native, "opencode")
    val opencodeBundle = File(bundleDir, "lib/opencode")
    val opencodeNativeBin = File(bundleDir, "bin/opencode")
    /** HOME / XDG base for the native server: /root inside the old layout maps onto this. */
    val nativeHome = File(native, "home")
    val globalAgentsMd = File(host("/root/.config/opencode"), "AGENTS.md")
    val rootfsReady get() = File(rootfs, "etc/os-release").exists() && File(rootfs, ".oc-configured").exists()
    val nativeReady get() = opencodeNativeBin.isFile && File(nativeBin, "bun").isFile

    /**
     * The file that backs a guest path. With the native runtime /root lives in nativeHome (the
     * guest binds it back in as /root for the shell tools), everything else stays in the rootfs.
     */
    fun host(guestPath: String): File {
        val p = guestPath.trimStart('/')
        if (p == "root") return nativeHome
        if (p.startsWith("root/")) return File(nativeHome, p.removePrefix("root/"))
        return File(rootfs, p)
    }

    // ---------- path translation: the app speaks the old /root layout, the native server sees real Android paths ----------

    /** /root/x (what the app stores in prefs/sessions) → the path the native server understands. */
    fun serverPath(guestPath: String): String {
        val p = guestPath.trimEnd('/')
        if (p == "/root") return nativeHome.path
        if (p.startsWith("/root/")) return File(nativeHome, p.removePrefix("/root/")).path
        return p
    }

    /** The reverse: a server-side path (native home or remote device) back to the /root form. */
    fun clientPath(serverPath: String): String {
        val home = nativeHome.path.trimEnd('/')
        val p = serverPath.trimEnd('/')
        if (p == home) return "/root"
        if (p.startsWith("$home/")) return "/root" + p.substring(home.length)
        return p
    }
}
