package com.celoext

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test build only, not part of the PR: writes the result of an install or a server start to the
 * phone's Downloads folder, where the environment that runs this app can read it and verify the
 * runtime without asking the user to copy logs by hand.
 */
object DeviceProbe {
    fun write(ctx: Context, base: String, text: String) {
        val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        val name = "$base-$stamp.txt"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            runCatching {
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        } else {
            runCatching { File("/sdcard/Download/$name").writeText(text) }
        }
    }
}
