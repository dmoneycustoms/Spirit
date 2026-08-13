package com.spirit.scan.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.spirit.scan.SpiritApp
import java.nio.FloatBuffer

data class JonesResult(
    val r: Float,
    val envelope: Float,
    val wMod: Float,
    val forcing: Float,
    val residual: Float,
    val invariantLocal: Float,
    val inFirewall: Boolean,
    val harmonicOk: Boolean,
    val routeWeight: Float
) {
    val label: String
        get() = when {
            inFirewall          -> "firewall"
            !harmonicOk         -> "harmonic_break"
            residual > 0.35f    -> "high_residual"
            envelope > 0.6f     -> "strong_envelope"
            else                -> "stable"
        }

    val confidence: Float
        get() = when (label) {
            "firewall"       -> 0.95f
            "harmonic_break" -> 0.85f
            "high_residual"  -> residual.coerceIn(0.4f, 0.9f)
            "strong_envelope"-> envelope.coerceIn(0.5f, 0.95f)
            else             -> (1f - residual).coerceIn(0.3f, 0.8f)
        }
}

class JonesRunner(context: Context) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val bytes = context.assets.open("models/jones.onnx").use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(bytes, opts)
        Log.i(SpiritApp.TAG, "JonesRunner (NSCB HV v3.17) loaded")
    }

    fun run(features: Map<String, Float>): JonesResult {
        fun t(name: String, default: Float = 0f): OnnxTensor {
            val v = features[name] ?: default
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(v)), longArrayOf())
        }

        val inputs = mapOf(
            "x"                 to t("x"),
            "y"                 to t("y"),
            "lam"               to t("lam", 1f),
            "sigma"             to t("sigma", 0.1f),
            "w_period"          to t("w_period", 1f),
            "lap_psi"           to t("lap_psi"),
            "fw_threshold"      to t("fw_threshold", 0.5f),
            "profile_deviation" to t("profile_deviation"),
            "dev_tol"           to t("dev_tol", 0.2f),
            "load_norm"         to t("load_norm", 1f)
        )

        session.run(inputs).use { outputs ->
            fun f(name: String): Float {
                val v = outputs.get(name).get().value
                return when (v) {
                    is FloatArray -> v[0]
                    is Float -> v
                    else -> 0f
                }
            }
            fun b(name: String): Boolean {
                val v = outputs.get(name).get().value
                return when (v) {
                    is BooleanArray -> v[0]
                    is Boolean -> v
                    is LongArray -> v[0] != 0L
                    is ByteArray -> v[0] != 0.toByte()
                    else -> false
                }
            }

            return JonesResult(
                r               = f("r"),
                envelope        = f("envelope"),
                wMod            = f("w_mod"),
                forcing         = f("forcing"),
                residual        = f("residual"),
                invariantLocal  = f("invariant_local"),
                inFirewall      = b("in_firewall"),
                harmonicOk      = b("harmonic_ok"),
                routeWeight     = f("route_weight")
            )
        }
    }

    override fun close() {
        session.close()
    }
}
