package com.celoext.data

import com.celoext.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible gateways that the user connects with an API key (shown in Settings → Providers).
 * The provider block (base URL + model list) goes into the global opencode.jsonc through the server, and the key goes
 * into OpenCode's own auth store, so it ends up in the same place as the key of any other provider
 * (and Vault already backs that up).
 */
object CustomProviders {
    class Preset(val id: String, val name: String, val baseUrl: String, val exampleModel: String)

    val KAMIHUB = Preset("kamihub", "Kamihub", "https://kamihub.us.ci/v1", "alicn/deepseek-v4.1-flash")

    /** Returns how many models were registered. Throws with a readable message when the key is refused or the gateway is unreachable. */
    suspend fun connect(p: Preset, rawKey: String): Int = withContext(Dispatchers.IO) {
        val key = rawKey.trim()
        require(key.isNotEmpty() && key.none { it.isWhitespace() || it.isISOControl() }) { "The API key has spaces or line breaks in it" }
        val models = fetchModels(p, key)
        val block = JSONObject()
            .put("name", p.name)
            .put("npm", "@ai-sdk/openai-compatible")
            .put("options", JSONObject().put("baseURL", p.baseUrl))
            .put("models", models)
        FreeModels.apply(p.id, block)
        Graph.api.setApiKey(p.id, key)
        runCatching { Graph.api.disposeAll() }
        runCatching { Graph.catalog.refresh() }
        Vault.save()
        models.length()
    }

    private fun modelEntry(id: String, context: Int, output: Int) = JSONObject()
        .put("name", id).put("tool_call", true).put("reasoning", false).put("attachment", false)
        .put("limit", JSONObject().put("context", context).put("output", output))

    /** GET {baseUrl}/models. A refused key stops here; a gateway without that endpoint falls back to the example model. */
    private fun fetchModels(p: Preset, key: String): JSONObject {
        val fallback = JSONObject().put(p.exampleModel, modelEntry(p.exampleModel, 128000, 8192))
        val c = URL(p.baseUrl.trimEnd('/') + "/models").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code == 401 || code == 403) error("${p.name} refused the API key (HTTP $code)")
            if (code == 404 || code == 405) return fallback
            if (code !in 200..299) error("${p.name} answered HTTP $code")
            val data = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return fallback
            val out = JSONObject()
            for (i in 0 until data.length()) {
                val m = data.optJSONObject(i) ?: continue
                val id = m.optString("id").takeIf { it.isNotBlank() } ?: continue
                val ctx = m.optInt("context_length", m.optInt("context_window", 128000)).takeIf { it > 0 } ?: 128000
                out.put(id, modelEntry(id, ctx, minOf(8192, ctx)))
            }
            return if (out.length() == 0) fallback else out
        } finally {
            c.disconnect()
        }
    }
}
