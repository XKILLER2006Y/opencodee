package com.celoext

import com.celoext.api.AgentInfo
import com.celoext.api.OcJson
import com.celoext.api.ProvidersResponse
import com.celoext.api.Session
import com.celoext.api.obj
import com.celoext.api.parseMessages
import com.celoext.api.parseSession
import com.celoext.api.str
import com.celoext.runtime.TarExtractor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** Models parse real OpenCode 1.19.4 responses captured from a live server. */
class ApiModelsTest {
    private fun res(n: String) = javaClass.getResource("/fx/$n")!!.readText()

    @Test fun sessions() {
        val l = OcJson.decodeFromString(ListSerializer(Session.serializer()), res("sessions.json"))
        assertEquals(1, l.size)
        assertTrue(l[0].id.startsWith("ses_"))
        assertTrue(l[0].directory.endsWith("dirA"))
    }

    @Test fun messagesWithParts() {
        val m = parseMessages(OcJson.parseToJsonElement(res("messages.json")))
        assertEquals(2, m.size)
        assertTrue(m[0].info.isUser)
        assertEquals("text", m[0].parts[0].type)
        assertEquals("big-pickle", m[1].info.modelName)
    }

    @Test fun v2SessionParsing() {
        val json = """
            {
              "id": "ses_019a4b2c3d4e",
              "title": "Fix crash",
              "projectID": "prj_root",
              "cost": 0.0042,
              "tokens": { "input": 100, "output": 50, "reasoning": 20, "cache": { "read": 10, "write": 5 } },
              "time": { "created": 1700000000000, "updated": 1700000001000 },
              "location": { "directory": "/home/user/project" },
              "model": { "id": "claude-3-5-sonnet", "providerID": "anthropic" }
            }
        """.trimIndent()
        val s = parseSession(OcJson.parseToJsonElement(json) as JsonObject)
        assertEquals("ses_019a4b2c3d4e", s.id)
        assertEquals("Fix crash", s.title)
        assertEquals("/home/user/project", s.directory)
        assertEquals(0.0042, s.cost!!, 0.0001)
        assertEquals(185L, s.tokens!!.sum)
        assertEquals("claude-3-5-sonnet", s.model!!.model)
        assertEquals("anthropic", s.model!!.providerID)
    }

    @Test fun v2MessagesParsing() {
        val json = """
            {
              "data": [
                {
                  "type": "user",
                  "id": "msg_01",
                  "text": "run tests",
                  "time": { "created": 1700000000000 }
                },
                {
                  "type": "assistant",
                  "id": "msg_02",
                  "agent": "build",
                  "model": { "id": "claude-3-5-sonnet", "providerID": "anthropic" },
                  "content": [
                    { "type": "text", "text": "Running tests..." },
                    {
                      "type": "tool",
                      "id": "tool_01",
                      "name": "shell",
                      "state": {
                        "status": "completed",
                        "input": { "command": "./gradlew test" },
                        "content": [ { "type": "text", "text": "BUILD SUCCESSFUL" } ]
                      }
                    }
                  ],
                  "cost": 0.001,
                  "time": { "created": 1700000001000, "completed": 1700000005000 }
                }
              ]
            }
        """.trimIndent()
        val msgs = parseMessages(OcJson.parseToJsonElement(json), defaultSessionID = "ses_test")
        assertEquals(2, msgs.size)
        assertTrue(msgs[0].info.isUser)
        assertEquals("run tests", msgs[0].parts[0].text)

        val a = msgs[1]
        assertTrue(!a.info.isUser)
        assertEquals("claude-3-5-sonnet", a.info.modelName)
        assertEquals(2, a.parts.size)
        assertEquals("text", a.parts[0].type)
        assertEquals("Running tests...", a.parts[0].text)
        assertEquals("tool", a.parts[1].type)
        assertEquals("bash", a.parts[1].raw.str("tool"))
        assertEquals("completed", a.parts[1].raw.obj("state")?.str("status"))
        assertEquals("./gradlew test", a.parts[1].raw.obj("state")?.obj("input")?.str("command"))
        assertEquals("BUILD SUCCESSFUL", a.parts[1].raw.obj("state")?.str("output"))
    }

    @Test fun providers() {
        val r = OcJson.decodeFromString(ProvidersResponse.serializer(), res("providers.json"))
        val oc = r.providers.first { it.id == "opencode" }
        assertTrue(oc.models.containsKey("big-pickle"))
        assertTrue(oc.models["mimo-v2.6-flash-free"]!!.supportsImages)
        assertEquals("big-pickle", r.default["opencode"])
    }

    @Test fun agents() {
        val a = OcJson.decodeFromString(ListSerializer(AgentInfo.serializer()), res("agents.json"))
        assertTrue(a.any { it.name == "build" && !it.hidden })
        assertTrue(a.first { it.name == "title" }.hidden)
    }

    @Test fun eventEnvelope() {
        val lines = res("events.txt").lines().filter { it.startsWith("data:") }
        assertTrue(lines.isNotEmpty())
        for (l in lines) {
            val o = OcJson.parseToJsonElement(l.removePrefix("data: ")) as JsonObject
            val payload = o.obj("payload")!!
            assertTrue(payload.str("type")!!.contains('.'))
        }
    }

    @Test fun tarExtractsRegularFilesAndDirs() {
        fun hdr(name: String, size: Int, type: Char): ByteArray {
            val h = ByteArray(512)
            name.toByteArray().copyInto(h)
            "0000644".toByteArray().copyInto(h, 100)
            "%011o".format(size).toByteArray().copyInto(h, 124)
            h[156] = type.code.toByte()
            "ustar".toByteArray().copyInto(h, 257)
            return h
        }
        val raw = ByteArrayOutputStream()
        raw.write(hdr("d/", 0, '5'))
        val body = "hello".toByteArray()
        raw.write(hdr("d/a.txt", body.size, '0')); raw.write(body); raw.write(ByteArray(512 - body.size))
        raw.write(ByteArray(1024))
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(raw.toByteArray()) } }
        val dir = File(System.getProperty("java.io.tmpdir"), "tar-test-${System.nanoTime()}").apply { mkdirs() }
        val tgz = File(dir, "t.tar.gz").apply { writeBytes(gz.toByteArray()) }
        val out = File(dir, "out")
        val r = TarExtractor.extract(tgz, out)
        assertEquals(1, r.files)
        assertEquals("hello", File(out, "d/a.txt").readText())
        dir.deleteRecursively()
    }
}
