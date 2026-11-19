package com.celoext.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Turns picked files into prompt parts: images are scaled down and sent as data: URLs. */
object Attachments {
    private const val MAX_EDGE = 2048
    private const val RECOMPRESS_ABOVE = 1_500_000
    /** Inline data: URLs are base64 (x1.33) and live in RAM twice (source + encoded + JSON). Above this, save to disk. */
    private const val MAX_INLINE_BYTES = 8_000_000L
    /** Hard cap for inline decode/recompress work. Larger files go to disk untouched. */
    private const val MAX_DECODE_BYTES = 32_000_000L

    fun describe(ctx: Context, uri: Uri): Attachment {
        var name = uri.lastPathSegment ?: "file"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        return Attachment(uri, mime, name)
    }

    fun sizeOf(ctx: Context, uri: Uri): Long? =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()?.takeIf { it >= 0 }

    /**
     * A prompt part for an attachment. Models only take what they can (images, sometimes PDF);
     * anything big or non-inline goes onto the environment's disk and the agent is told where.
     * Never reads a whole file into RAM just to copy it to disk.
     */
    fun toPart(ctx: Context, a: Attachment, sessionDir: String? = null): JsonObject {
        val m = com.celoext.Graph.catalog.model(com.celoext.Graph.prefs.providerId, com.celoext.Graph.prefs.modelId)
        val size = sizeOf(ctx, a.uri)
        val wantInline = (a.isImage && m?.supportsImages != false) || (a.mime == "application/pdf" && m?.capabilities?.input?.pdf == true)
        // Images are always recompressed (output is small). Everything else above the inline cap goes to disk.
        val inline = wantInline && (a.isImage || (size ?: 0) <= MAX_INLINE_BYTES)
        if (inline) {
            // Sampling keeps the decode small, but a panorama or a 50 MP photo can still need more
            // heap than the phone gives us, and send() only catches Exception: an OOM there took the
            // whole app down. Fall back to the disk path so the message still goes out.
            val part = runCatching { toFilePart(ctx, a) }
            part.getOrNull()?.let { return it }
            if (com.celoext.Graph.devices.isRemote) {
                throw part.exceptionOrNull() as? Exception ?: java.io.IOException("Could not read ${a.name}")
            }
        }

        if (com.celoext.Graph.devices.isRemote) {
            if (!isTextLike(a.mime, a.name)) throw java.io.IOException("Only images and text files can be sent to a remote device")
            val text = ctx.contentResolver.openInputStream(a.uri)!!.use { readBounded(it, 256_001L) }
            if (size != null && size > 256_000) throw java.io.IOException("Only images and text files can be sent to a remote device")
            if (text.size > 256_000) throw java.io.IOException("Only images and text files can be sent to a remote device")
            return synthetic("<attached-file name=\"${a.name}\" mime=\"${a.mime}\" size=\"${size ?: text.size}\"/>\nContents of ${a.name}:\n```\n${String(text)}\n```")
        }

        val dir = com.celoext.Graph.paths.host(UPLOADS).apply { mkdirs() }
        var f = java.io.File(dir, safeName(a.name))
        var i = 2
        while (f.exists()) { f = java.io.File(dir, safeName(a.name).let { n -> n.substringBeforeLast('.', n) + "-$i" + (if ('.' in n) "." + n.substringAfterLast('.') else "") }); i++ }
        // Fail with a clear message instead of a broken half-written copy: large attachments are
        // saved next to a ~1 GB rootfs and the mirror doubles what a big file needs.
        val need = (size?.let { it + it / 4 } ?: 0L) + 8_000_000L
        if (need > 0) {
            val free = runCatching { android.os.StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (free < need) throw java.io.IOException("Not enough free space for ${a.name}: about ${human(need)} is needed, ${human(free)} is free")
        }
        val written = try { copyUriToFile(ctx, a.uri, f) } catch (e: Exception) {
            runCatching { f.delete() }
            throw java.io.IOException("Could not save ${a.name}: ${e.message ?: e.javaClass.simpleName}")
        }
        if (!f.isFile || f.length() != written) { runCatching { f.delete() }; throw java.io.IOException("Could not save ${a.name}") }
        // Root cause of ENOENT for large uploads: some agent cwd values don't resolve /root/uploads
        // the same way (proot --root-id + cwd=/root/work). Mirror into the session dir too, so a
        // relative or cwd-based open still finds it. A hard link costs no extra space (both paths
        // are inside filesDir); only small files get a real second copy.
        var mirror = ""
        if (!sessionDir.isNullOrBlank()) {
            val mirrorDir = com.celoext.Graph.paths.host("$sessionDir/uploads").apply { mkdirs() }
            val mf = java.io.File(mirrorDir, f.name)
            if (mf.absolutePath != f.absolutePath) {
                val linked = runCatching { java.nio.file.Files.createLink(mf.toPath(), f.toPath()) }.isSuccess && mf.isFile
                if (!linked && written <= 64_000_000L) runCatching { f.copyTo(mf, overwrite = true) }
                if (mf.isFile) mirror = "${sessionDir.trimEnd('/')}/uploads/${mf.name}"
            }
        }
        // The agent (native server) sees the real Android home, not /root: give it the translated path.
        val guest = com.celoext.Graph.paths.serverPath("$UPLOADS/${f.name}")
        val guestMirror = if (mirror.isNotEmpty()) com.celoext.Graph.paths.serverPath(mirror) else ""
        return synthetic(
            "<attached-file path=\"$guest\" name=\"${a.name}\" mime=\"${a.mime}\" size=\"$written\"/>\n" +
                "The user attached the file ${a.name} (${a.mime}, ${human(written)}). It is saved at $guest" +
                (if (guestMirror.isNotEmpty()) " (also at $guestMirror)" else "") +
                " — open it with your tools when needed.",
        )
    }

    const val UPLOADS = "/root/uploads"

    private fun synthetic(text: String) = buildJsonObject { put("type", "text"); put("text", text); put("synthetic", true) }

    private fun safeName(n: String) = n.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "file" }

    fun isTextLike(mime: String, name: String) = mime.startsWith("text/") || mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh", "application/yaml", "application/toml") ||
        name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "json", "kt", "java", "py", "js", "ts", "tsx", "c", "h", "cpp", "rs", "go", "sh", "yml", "yaml", "toml", "xml", "html", "css", "csv", "log", "ini", "conf", "gradle", "kts", "sql")

    fun human(b: Long) = when { b >= 1_000_000 -> "%.1f MB".format(b / 1e6); b >= 1000 -> "%.0f KB".format(b / 1e3); else -> "$b B" }

    /** Synthetic part with an attachment → (name, path); null if it isn't an attachment. */
    fun parseAttached(text: String): Pair<String, String?>? {
        if (!text.startsWith("<attached-file ")) return null
        val head = text.substringBefore("/>")
        fun attr(k: String) = Regex("$k=\"([^\"]*)\"").find(head)?.groupValues?.get(1)
        return (attr("name") ?: "file") to attr("path")
    }

    /**
     * Inline image/PDF as data: URL. Disk + streaming only: the source file is never loaded
     * whole just to base64 it. Images are decoded with sampling, then recompressed, so the
     * encoded payload stays in the low MB range instead of 4/3 of the original file.
     */
    fun toFilePart(ctx: Context, a: Attachment): JsonObject {
        val size = sizeOf(ctx, a.uri) ?: 0L
        if (size > MAX_DECODE_BYTES && !a.isImage) throw java.io.IOException("File is too large to inline (${human(size)}). Attach it as a saved file instead.")
        var mime = a.mime
        var name = a.name
        var bytes: ByteArray
        if (a.isImage && a.mime != "image/gif") {
            val bounds = decodeBoundsFromStream(ctx, a)
            val needsRecompress = size > RECOMPRESS_ABOVE || (bounds != null && maxOf(bounds.outWidth, bounds.outHeight) > MAX_EDGE)
            val recompressed = if (needsRecompress) tryRecompress(ctx, a, bounds) else null
            if (recompressed != null) {
                bytes = recompressed
                mime = "image/jpeg"
                name = a.name.substringBeforeLast('.') + ".jpg"
            } else {
                bytes = boundedRead(ctx, a, MAX_INLINE_BYTES)
            }
        } else {
            bytes = boundedRead(ctx, a, MAX_INLINE_BYTES)
        }
        return filePart(mime, name, bytes)
    }

    private fun filePart(mime: String, name: String, bytes: ByteArray): JsonObject {
        val url = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        return buildJsonObject {
            put("type", "file")
            put("mime", mime)
            put("filename", name)
            put("url", url)
        }
    }

    private fun boundedRead(ctx: Context, a: Attachment, cap: Long): ByteArray {
        ctx.contentResolver.openInputStream(a.uri)!!.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1 shl 16)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > cap) throw java.io.IOException("File is too large to inline (${human(total)}). Attach it as a saved file instead.")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    fun readBounded(input: InputStream, cap: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), (cap - total + 1).coerceAtLeast(1)).toInt())
            if (n < 0) break
            total += n
            out.write(buf, 0, n)
            if (total > cap) break
        }
        return out.toByteArray()
    }

    fun copyUriToFile(ctx: Context, uri: Uri, dest: java.io.File): Long {
        dest.parentFile?.mkdirs()
        var written = 0L
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    written += n
                }
            }
        }
        return written
    }

    /** Stream-decode bounds without loading the whole file: uses a file descriptor and inJustDecodeBounds on the stream. */
    private fun decodeBoundsFromStream(ctx: Context, a: Attachment): BitmapFactory.Options? =
        runCatching {
            ctx.contentResolver.openInputStream(a.uri)!!.use { input ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, o)
                if (o.outWidth > 0 && o.outHeight > 0) o else null
            }
        }.getOrNull()

    private fun tryRecompress(ctx: Context, a: Attachment, bounds: BitmapFactory.Options?): ByteArray? {
        if (bounds == null) return null
        if (maxOf(bounds.outWidth, bounds.outHeight) <= MAX_EDGE) return null
        // A 12 MP camera photo is 4000x3000: decoded at full size that is a 48 MB ARGB_8888 bitmap,
        // and scaling it to 2048 allocates a second one. On the phones this app targets that pair
        // was the largest single allocation in the process. Decode by sample instead, sized to a
        // budget rather than to the target edge, so a tall screenshot keeps its width.
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        var bmp = runCatching {
            ctx.contentResolver.openInputStream(a.uri)!!.use { input ->
                val o = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeStream(input, null, o)
            }
        }.getOrNull() ?: return null
        val scale = MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        val rotation = runCatching {
            ctx.contentResolver.openInputStream(a.uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }?.let {
                when (it) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) m.postRotate(rotation)
        if (!m.isIdentity) {
            val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            // Release the decode before the base64 copy of the result is made.
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        val out = ByteArrayOutputStream()
        try {
            bmp.compress(Bitmap.CompressFormat.JPEG, 86, out)
            return out.toByteArray()
        } finally {
            bmp.recycle()
        }
    }
}

/**
 * Budget for the decoded bitmap in pixels: 4 MP of ARGB_8888 is 16 MB. A plain
 * "max edge <= MAX_EDGE" rule would halve a 2400 px screenshot to 1200 and make its text
 * unreadable for the model, so the limit is on total pixels and the width is kept.
 */
private const val MAX_DECODE_PIXELS = 4_000_000L

/** Power-of-two sample for [BitmapFactory.Options.inSampleSize] that keeps the decode within budget. */
internal fun sampleSizeFor(width: Int, height: Int, maxPixels: Long = MAX_DECODE_PIXELS): Int {
    var sample = 1
    while (sample < 64 && (width.toLong() / sample) * (height.toLong() / sample) > maxPixels) sample *= 2
    return sample
}
