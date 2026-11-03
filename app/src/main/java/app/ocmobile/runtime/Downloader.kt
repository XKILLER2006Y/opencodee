package app.ocmobile.runtime

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Download with progress and a sha256 check. Writes to .part and renames when done. */
object Downloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetch(url: String, dest: File, sha256: String?, onProgress: (done: Long, total: Long) -> Unit) {
        dest.parentFile?.mkdirs()
        // A cached file is reused only when its checksum is known and matches (without one it may be stale or partial).
        if (dest.exists() && sha256 != null && sha256Of(dest) == sha256) {
            onProgress(dest.length(), dest.length()); return
        }
        val part = File(dest.path + ".part")
        client.newCall(Request.Builder().url(url).header("User-Agent", "opencode-mobile").build()).execute().use { r ->
            if (!r.isSuccessful) error("Download $url: HTTP ${r.code}")
            val body = r.body
            val total = body.contentLength()
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var last = 0L
            part.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                val src = body.byteStream()
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    if (done - last > 128 * 1024) { last = done; onProgress(done, total) }
                }
            }
            if (total > 0 && done != total) {
                part.delete()
                error("Download of ${dest.name} was cut short ($done of $total bytes)")
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (sha256 != null && !got.equals(sha256, ignoreCase = true)) {
                part.delete()
                error("Checksum mismatch for ${dest.name}: $got")
            }
            dest.delete()
            if (!part.renameTo(dest)) error("Could not rename ${part.name}")
            onProgress(done, total.takeIf { it > 0 } ?: done)
        }
    }

    fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().buffered(1 shl 16).use { i ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * sha256 of a GitHub release asset, taken from the API's `digest` field ("sha256:<hex>").
     * Returns null when GitHub doesn't report one.
     */
    fun githubAssetSha256(releaseApiUrl: String, assetName: String): String? {
        val o = org.json.JSONObject(getText(releaseApiUrl))
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name") == assetName) {
                val d = a.optString("digest")
                return if (d.startsWith("sha256:")) d.removePrefix("sha256:").lowercase() else null
            }
        }
        return null
    }

    fun getText(url: String): String =
        client.newCall(Request.Builder().url(url).header("User-Agent", "opencode-mobile").build()).execute().use {
            if (!it.isSuccessful) error("GET $url: HTTP ${it.code}")
            it.body.string()
        }
}
