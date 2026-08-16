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

        val label = when {
            p.contains("label: firewall") || p.contains("firewall: true") -> "firewall"
            p.contains("label: harmonic_break") || p.contains("harmonic_ok: false") -> "harmonic_break"
            p.contains("label: high_residual") -> "high_residual"
            p.contains("label: strong_envelope") -> "strong_envelope"
            else -> "stable"
        }

        val camHot = p.contains("camera_motion: 0.") && !p.contains("camera_motion: 0.0")
        val camNoise = p.contains("camera_noise: 0.") && !p.contains("camera_noise: 0.0")

        val field = when (label) {
            "firewall" ->
                "Sensors report a hard boundary. Values are being clipped instead of drifting freely."
            "harmonic_break" ->
                "Magnetometer rhythm lost coherence. The oscillation pattern no longer lines up with the baseline."
            "high_residual" ->
                "Large leftover error after the baseline fit. The simple field model cannot account for this interval."
            "strong_envelope" ->
                "Energy is concentrating. Envelope strength is elevated versus the recent window."
            else ->
                "Field is near baseline. No strong anomaly signature in this window."
        }

        val camNote = when {
            camHot && camNoise -> " Camera also shows motion and noise spikes."
            camHot -> " Camera motion is elevated."
            camNoise -> " Camera noise is elevated."
            else -> ""
        }

        if (!hasQ) {
            return field + camNote
        }

        val q = question!!.lowercase()
        val answer = when {
            q.contains("what") && (q.contains("cause") || q.contains("causing") || q.contains("why")) ->
                when (label) {
                    "firewall" ->
                        "Most likely a strong local magnetic boundary or active rejection of the baseline model, not a random glitch."
                    "harmonic_break" ->
                        "Most likely a disruption in the magnetic oscillation — nearby metal, device interference, or a sudden field shift."
                    "high_residual" ->
                        "Most likely an unmodeled disturbance in the sensor stream that the baseline cannot absorb."
                    "strong_envelope" ->
                        "Most likely a concentrated energy pocket in the current window rather than broad noise."
                    else ->
                        "No strong causal anomaly is present; this may be ordinary environmental variance."
                }

            q.contains("entity") || q.contains("spirit") || q.contains("ghost") || q.contains("presence") ->
                "Symbolic reading only: the sensors show a $label pattern$camNote. That is not proof of an entity."

            q.contains("safe") || q.contains("danger") || q.contains("threat") ->
                when (label) {
                    "firewall", "harmonic_break", "high_residual" ->
                        "Treat as an unstable interval. No physical danger is claimed; readings are simply abnormal."
                    else ->
                        "No elevated anomaly. Field looks ordinary for now."
                }

            q.contains("camera") || q.contains("visual") || q.contains("see") ->
                if (camHot || camNoise)
                    "Camera features are active (motion/noise). Optical disturbance lines up with the $label field state."
                else
                    "Camera features are quiet. The $label state is coming from magnetometer/accelerometer, not the camera."

            q.contains("where") || q.contains("direction") || q.contains("location") ->
                "Location is only a context tag here. The anomaly is in the sensor field state ($label), not a mapped place marker."

            q.contains("how long") || q.contains("duration") || q.contains("still") ->
                "This app reports window-by-window state. If $label persists across several timeline entries, the condition is ongoing."

            else ->
                "On \"$question\": current field state is $label. $field$camNote"
        }

        return answer
    }
}
