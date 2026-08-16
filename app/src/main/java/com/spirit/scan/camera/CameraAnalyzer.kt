package com.spirit.scan.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.spirit.scan.SpiritApp
import kotlin.math.abs

/**
 * Lightweight analyzer: estimates brightness + crude motion proxy from luma.
 * Does not claim ghost detection — only disturbance features for the pipeline.
 */
class CameraAnalyzer(
    private val onFeatures: (CameraFeatures) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAvg = -1f

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: run {
                image.close()
                return
            }
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Sample every Nth byte for speed
            var sum = 0L
            var count = 0
            var i = 0
            while (i < bytes.size) {
                sum += (bytes[i].toInt() and 0xFF)
                count++
                i += 16
            }
            val avg = if (count > 0) sum.toFloat() / count else 0f
            val brightness = (avg / 255f).coerceIn(0f, 1f)

            val motion = if (lastAvg >= 0f) abs(avg - lastAvg) / 255f else 0f
            lastAvg = avg

            // crude noise proxy: high-frequency-ish spread on samples
            var spread = 0f
            if (count > 1) {
                var j = 0
                var acc = 0f
                var n = 0
                while (j < bytes.size) {
                    val v = (bytes[j].toInt() and 0xFF) / 255f
                    acc += abs(v - brightness)
                    n++
                    j += 32
                }
                spread = if (n > 0) acc / n else 0f
            }

            onFeatures(
                CameraFeatures(
                    noiseVariance = spread,
                    motionMagnitude = motion.coerceIn(0f, 1f),
                    brightness = brightness
                )
            )
        } catch (e: Exception) {
            Log.w(SpiritApp.TAG, "Camera analyze failed", e)
        } finally {
            image.close()
        }
    }
}
