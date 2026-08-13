package com.spirit.scan.sensor

import kotlin.math.sqrt

class CircularBuffer(private val capacity: Int = 64) {

    private data class Sample(
        val ts: Long,
        val mag: FloatArray,
        val accel: FloatArray
    )

    private val samples = ArrayDeque<Sample>(capacity)
    private var lastMag: FloatArray? = null
    private var lastAccel: FloatArray? = null

    fun addMag(timestamp: Long, values: FloatArray) {
        lastMag = values.copyOf(3)
        tryEmit(timestamp)
    }

    fun addAccel(timestamp: Long, values: FloatArray) {
        lastAccel = values.copyOf(3)
        tryEmit(timestamp)
    }

    private fun tryEmit(timestamp: Long) {
        val m = lastMag ?: return
        val a = lastAccel ?: return
        samples.addLast(Sample(timestamp, m, a))
        while (samples.size > capacity) samples.removeFirst()
    }

    fun ready(): Boolean = samples.size >= capacity

    fun clear() {
        samples.clear()
        lastMag = null
        lastAccel = null
    }

    fun toJonesFeatures(): Map<String, Float> {
        require(ready())
        val mags = samples.map { it.mag }
        val accs = samples.map { it.accel }

        val mx = mags.map { it[0] }.average().toFloat()
        val my = mags.map { it[1] }.average().toFloat()
        val mz = mags.map { it[2] }.average().toFloat()
        val magMean = sqrt(mx*mx + my*my + mz*mz)

        val magMags = mags.map { sqrt(it[0]*it[0] + it[1]*it[1] + it[2]*it[2]) }
        val magStd = std(magMags)

        val last = mags.last()
        val profileDev = sqrt(
            (last[0]-mx)*(last[0]-mx) +
            (last[1]-my)*(last[1]-my) +
            (last[2]-mz)*(last[2]-mz)
        )

        val period = estimatePeriod(mags.map { it[0] })

        val lap = if (mags.size >= 3) {
            val a = mags[mags.size-3]
            val b = mags[mags.size-2]
            val c = mags[mags.size-1]
            (a[0] - 2*b[0] + c[0]) + (a[1] - 2*b[1] + c[1]) + (a[2] - 2*b[2] + c[2])
        } else 0f

        val lastAcc = accs.last()
        val load = sqrt(lastAcc[0]*lastAcc[0] + lastAcc[1]*lastAcc[1] + lastAcc[2]*lastAcc[2])

        return mapOf(
            "x" to (mx / (magMean + 1e-6f)),
            "y" to (my / (magMean + 1e-6f)),
            "lam" to period.coerceIn(0.1f, 10f),
            "sigma" to magStd.coerceIn(0.001f, 5f),
            "w_period" to period.coerceIn(0.1f, 10f),
            "lap_psi" to lap,
            "fw_threshold" to 0.55f,
            "profile_deviation" to profileDev,
            "dev_tol" to 0.25f,
            "load_norm" to (load / 15f).coerceIn(0f, 2f)
        )
    }

    fun toSdeFeatures(jones: com.spirit.scan.ml.JonesResult? = null): FloatArray {
        require(ready())
        val mags = samples.map { it.mag }
        val accs = samples.map { it.accel }

        val magMags = mags.map { sqrt(it[0]*it[0] + it[1]*it[1] + it[2]*it[2]) }
        val accMags = accs.map { sqrt(it[0]*it[0] + it[1]*it[1] + it[2]*it[2]) }

        val features = FloatArray(14)
        features[0] = magMags.average().toFloat()
        features[1] = std(magMags)
        features[2] = accMags.average().toFloat()
        features[3] = std(accMags)
        features[4] = estimatePeriod(mags.map { it[0] })
        features[5] = estimatePeriod(mags.map { it[1] })
        features[6] = (magMags.last() - magMags.first()) / (magMags.size.coerceAtLeast(1))
        features[7] = jones?.residual ?: 0f
        features[8] = jones?.envelope ?: 0f
        features[9] = jones?.routeWeight ?: 0f
        features[10] = if (jones?.inFirewall == true) 1f else 0f
        features[11] = if (jones?.harmonicOk == true) 1f else 0f
        features[12] = jones?.forcing ?: 0f
        features[13] = jones?.invariantLocal ?: 0f
        return features
    }

    private fun std(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        val varSum = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(varSum / values.size).toFloat()
    }

    private fun estimatePeriod(series: List<Float>): Float {
        if (series.size < 8) return 1f
        val mean = series.average()
        var crossings = 0
        for (i in 1 until series.size) {
            if ((series[i-1] - mean) * (series[i] - mean) < 0) crossings++
        }
        return if (crossings > 1) series.size.toFloat() / (crossings / 2f) else 1f
    }
}
