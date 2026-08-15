package com.spirit.scan.ml

import android.util.Log
import com.spirit.scan.SpiritApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LlmClient(
    /** Optional. Example: "https://your-api.example.com/v1/generate" */
    private val endpointUrl: String? = null,
    /** Optional bearer token / API key */
    private val apiKey: String? = null
) {

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        if (!endpointUrl.isNullOrBlank()) {
            try {
                val remote = callRemote(prompt)
                if (remote.isNotBlank()) return@withContext remote
            } catch (e: Exception) {
                Log.w(SpiritApp.TAG, "LLM HTTP failed, using offline", e)
            }
        }
        offlineReply(prompt)
    }

    private fun callRemote(prompt: String): String {
        val url = URL(endpointUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        val body = JSONObject()
            .put("prompt", prompt)
            .put("max_tokens", 180)
            .toString()

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        conn.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("LLM HTTP $code: $text")
        }

        // Try common response shapes
        return try {
            val json = JSONObject(text)
            when {
                json.has("text") -> json.getString("text")
                json.has("response") -> json.getString("response")
                json.has("content") -> json.getString("content")
                json.has("choices") -> {
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val c0 = choices.getJSONObject(0)
                        when {
                            c0.has("text") -> c0.getString("text")
                            c0.has("message") -> c0.getJSONObject("message").optString("content", text)
                            else -> text
                        }
                    } else text
                }
                else -> text
            }.trim()
        } catch (_: Exception) {
            text.trim()
        }
    }

    private fun offlineReply(prompt: String): String {
        val p = prompt.lowercase()

        // Prefer answering a user question if present
        val questionLine = prompt.lineSequence()
            .firstOrNull { it.startsWith("User question:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.removeSurrounding("\"")

        val hasQuestion = !questionLine.isNullOrBlank() &&
            !questionLine.equals("No question", ignoreCase = true)

        val field = when {
            p.contains("label: firewall") || p.contains("firewall: true") ->
                "The field is pressing against a hard boundary. Readings look actively constrained rather than random noise."
            p.contains("label: harmonic_break") || p.contains("harmonic_ok: false") ->
                "The harmonic structure is breaking down. Oscillation is losing coherence."
            p.contains("label: high_residual") ->
                "A large residual remains after the baseline fit. Something in the field is not explained by the simple model."
            p.contains("label: strong_envelope") ->
                "A strong envelope is forming. Energy appears concentrated rather than diffuse."
            p.contains("signature: stressed") || p.contains("system_ok: false") ->
                "Dynamics report stress. Stability is low; treat this as an unstable interval."
            else ->
                "The channel is comparatively quiet. Variations stay near baseline."
        }

        return if (hasQuestion) {
            "Regarding \"$questionLine\": $field This is a symbolic reading from sensors, not evidence of a specific entity."
        } else {
            field
        }
    }
}
