package com.spirit.scan.entity

import com.spirit.scan.ml.SdeResult

data class SdeState(val result: SdeResult) {
    val latent: FloatArray get() = result.toLatent()
    val isSystemOk: Boolean get() = result.isSystemOk
    val volatility: Float get() = result.sigmaSde
    val persistence: Float get() = result.sComposite
    val focus: Float get() = result.temporalScore
    val signature: String
        get() = when {
            !result.isSystemOk -> "stressed"
            result.sComposite > 0.7f -> "coherent"
            result.harmonicScore < 0.3f -> "broken_harmony"
            else -> "nominal"
        }
}
