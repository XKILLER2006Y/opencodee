package com.celoext.data

import com.celoext.Graph
import com.celoext.api.OcJson
import com.celoext.api.Session
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * The session list from the last successful refresh, so a cold start shows the chats that already
 * existed instead of an empty screen while proot and the local server boot. The first refresh
 * replaces it; a wipe or a device switch clears it.
 */
object SessionCache {
    private const val FILE = "sessions.json"
    private val file get() = File(Graph.app.filesDir, FILE)

    @Synchronized
    fun load(): List<Session> = runCatching {
        val f = file
        if (f.isFile) decodeSessions(f.readText()) else emptyList()
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(list: List<Session>) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encodeSessions(list))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }
}

/**
 * The cache is only a display hint: anything unreadable is treated as no cache, never as a crash
 * and never as a partial list that would put ghost chats on screen.
 */
internal fun encodeSessions(list: List<Session>): String =
    OcJson.encodeToString(ListSerializer(Session.serializer()), list)

internal fun decodeSessions(text: String): List<Session> =
    runCatching { OcJson.decodeFromString(ListSerializer(Session.serializer()), text) }.getOrDefault(emptyList())
