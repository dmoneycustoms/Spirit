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
    private val endpointUrl: String? = null,
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

        val question = prompt.lineSequence()
            .firstOrNull { it.startsWith("User question:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.trim()

        val hasQ = !question.isNullOrBlank() &&
            !question.equals("No question", ignoreCase = true)

        val residual = extractFloat(p, "residual:")
        val envelope = extractFloat(p, "envelope:")
        val sigma = extractFloat(p, "sigma_sde:")
        val composite = extractFloat(p, "composite:")
        val camNoise = extractFloat(p, "camera_noise:")
        val camMotion = extractFloat(p, "camera_motion:")

        val label = when {
            p.contains("label: firewall") || p.contains("firewall: true") -> "firewall"
            p.contains("label: harmonic_break") || p.contains("harmonic_ok: false") -> "harmonic_break"
            p.contains("label: high_residual") -> "high_residual"
            p.contains("label: strong_envelope") -> "strong_envelope"
            else -> "stable"
        }

        val strength = when {
            residual > 0.6f || envelope > 0.7f -> "strong"
            residual > 0.35f || envelope > 0.45f -> "moderate"
            else -> "weak"
        }

        val optical = when {
            camMotion > 0.05f && camNoise > 0.1f -> "Camera shows motion and noise."
            camMotion > 0.05f -> "Camera motion is up."
            camNoise > 0.1f -> "Camera noise is up."
            else -> "Camera is quiet."
        }

        val field = when (label) {
            "firewall" ->
                "Firewall " + strength + ". Hard clip on the model. residual=" +
                    format(residual) + " envelope=" + format(envelope) + "."
            "harmonic_break" ->
                "Harmonic break " + strength + ". Coherence lost. sigma=" +
                    format(sigma) + " composite=" + format(composite) + "."
            "high_residual" ->
                "High residual " + strength + " (" + format(residual) +
                    "). Baseline cannot explain this window."
            "strong_envelope" ->
                "Strong envelope (" + format(envelope) +
                    "). Energy is concentrating."
            else ->
                "Stable window. residual=" + format(residual) +
                    " envelope=" + format(envelope) + "."
        }

        if (!hasQ) {
            return field + " " + optical
        }

        val q = question!!.lowercase()
        return when {
            q.contains("cause") || q.contains("causing") || q.contains("why") ||
                q.contains("what is") || q.contains("influencing") ->
                when (label) {
                    "firewall" ->
                        "Likely cause: magnetic boundary or model saturation. " + field + " " + optical
                    "harmonic_break" ->
                        "Likely cause: disrupted mag rhythm (metal, EMI, sudden shift). " + field + " " + optical
                    "high_residual" ->
                        "Likely cause: disturbance the baseline cannot absorb. " + field + " " + optical
                    "strong_envelope" ->
                        "Likely cause: localized energy concentration. " + field + " " + optical
                    else ->
                        "No strong anomaly cause right now. " + field + " " + optical
                }

            q.contains("entity") || q.contains("spirit") || q.contains("ghost") || q.contains("presence") ->
                "Symbolic only: pattern is " + label + " (" + strength + "). Not proof of an entity. " + optical

            q.contains("safe") || q.contains("danger") || q.contains("threat") ->
                if (label == "stable")
                    "No elevated anomaly. " + optical
                else
                    "Unstable interval (" + label + ", " + strength + "). Abnormal readings, not a claimed physical threat. " + optical

            q.contains("camera") || q.contains("visual") || q.contains("see") ->
                optical + " Field label is " + label + "."

            q.contains("how long") || q.contains("still") || q.contains("duration") ->
                "Per-window state. If Timeline keeps showing " + label + ", it is persisting. Strength: " + strength + "."

            else ->
                "Q \"" + question + "\" -> " + label + " (" + strength + "). " + field + " " + optical
        }
    }

    private fun extractFloat(text: String, key: String): Float {
        val idx = text.indexOf(key)
        if (idx < 0) return 0f
        val after = text.substring(idx + key.length).trim()
        val num = StringBuilder()
        for (c in after) {
            if (c.isDigit() || c == '.' || c == '-' || c == '+') {
                num.append(c)
            } else if (num.isNotEmpty()) {
                break
            }
        }
        return num.toString().toFloatOrNull() ?: 0f
    }

    private fun format(v: Float): String {
        val scaled = (v * 100f).toInt() / 100f
        return scaled.toString()
    }
}
