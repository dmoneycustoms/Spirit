package com.spirit.scan.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.spirit.scan.SpiritApp
import java.nio.FloatBuffer

data class SdeResult(
    val sigmaSde: Float,
    val sComposite: Float,
    val temporalScore: Float,
    val harmonicScore: Float,
    val jonesScore: Float,
    val sdeScore: Float,
    val sAmp: Float,
    val sParity: Float,
    val sDiff: Float,
    val systemOk: Float
) {
    val isSystemOk: Boolean get() = systemOk > 0.5f

    fun toLatent(): FloatArray = floatArrayOf(
        sigmaSde, sComposite, temporalScore, harmonicScore,
        jonesScore, sdeScore, sAmp, sParity, sDiff, systemOk
    )
}

class SdeRunner(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val bytes = context.assets.open("models/sde.onnx").use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(bytes, opts)
        Log.i(SpiritApp.TAG, "SdeRunner loaded")
    }

    fun run(features: FloatArray): SdeResult {
        require(features.size == 14)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(14))
        session.run(mapOf("features" to tensor)).use { outputs ->
            fun f(name: String): Float {
                val v = outputs.get(name).get().value
                return when (v) {
                    is FloatArray -> v[0]
                    is Float -> v
                    else -> 0f
                }
            }
            return SdeResult(
                sigmaSde = f("sigma_sde"),
                sComposite = f("s_composite"),
                temporalScore = f("temporal_score"),
                harmonicScore = f("harmonic_score"),
                jonesScore = f("jones_score"),
                sdeScore = f("sde_score"),
                sAmp = f("s_amp"),
                sParity = f("s_parity"),
                sDiff = f("s_diff"),
                systemOk = f("system_ok")
            )
        }
    }

    override fun close() {
        session.close()
    }
}
