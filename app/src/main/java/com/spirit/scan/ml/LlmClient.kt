package com.spirit.scan.ml

import android.content.Context
import android.util.Log
import com.spirit.scan.SpiritApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlmClient(private val context: Context) {
    private val remoteUrl: String? = null

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        localStub(prompt)
    }

    private fun localStub(prompt: String): String {
        return when {
            prompt.contains("firewall", ignoreCase = true) ->
                "The field has hit a hard boundary. Something is actively rejecting the baseline."
            prompt.contains("harmonic_break", ignoreCase = true) ->
                "Harmonic structure collapsed. The oscillation is no longer coherent."
            prompt.contains("high_residual", ignoreCase = true) ->
                "Large residual detected. The model cannot fully account for the current state."
            prompt.contains("strong_envelope", ignoreCase = true) ->
                "A strong envelope is forming. Energy is concentrating."
            else ->
                "The channel is quiet. Field remains within normal bounds."
        }
    }
}
