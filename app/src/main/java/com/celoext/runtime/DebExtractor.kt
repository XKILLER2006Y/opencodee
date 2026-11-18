package com.celoext.runtime

import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.InputStream

/**
 * Extracts Termux .deb packages without external tools: .deb is an ar archive whose
 * `data.tar.xz` member holds the package files (the whole Termux prefix layout).
 */
object DebExtractor {

    fun extract(deb: File, dest: File, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }): TarExtractor.Result {
        deb.inputStream().buffered(1 shl 16).use { input ->
            val magic = ByteArray(8)
            if (!readFully(input, magic) || String(magic) != "!<arch>\n") error("Not a .deb archive: ${deb.name}")
            var dataTar: ByteArray? = null
            while (true) {
                val header = ByteArray(60)
                if (!readFully(input, header)) break
                val name = String(header, 0, 16).trim('\u0000', ' ')
                val size = String(header, 48, 10).trim().toLongOrNull() ?: error("Corrupt .deb member header in ${deb.name}")
                if (name == "data.tar.xz") {
                    dataTar = ByteArray(size.toInt())
                    if (!readFully(input, dataTar)) error("Truncated data.tar.xz in ${deb.name}")
                } else {
                    skip(input, size)
                }
                if (size % 2 == 1L) skip(input, 1)
            }
            val data = dataTar ?: error("${deb.name} has no data.tar.xz member")
            // Termux debs are double compressed today; accept a plain .gz or .tar too, cheap insurance.
            val tar: InputStream = when {
                data.size > 6 && data[0] == 0xFD.toByte() && data[1] == '7'.code.toByte() && data[2] == 'z'.code.toByte() -> XZInputStream(data.inputStream())
                data.size > 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte() -> java.util.zip.GZIPInputStream(data.inputStream())
                else -> data.inputStream()
            }
            return TarExtractor.extract(tar, data.size.toLong(), dest, onProgress)
        }
    }

    private fun readFully(i: InputStream, b: ByteArray): Boolean {
        var off = 0
        while (off < b.size) {
            val r = i.read(b, off, b.size - off)
            if (r < 0) return off != 0 && error("truncated archive")
            off += r
        }
        return true
    }

    private fun skip(i: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(1 shl 14)
        while (left > 0) { val r = i.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) return; left -= r }
    }
}
