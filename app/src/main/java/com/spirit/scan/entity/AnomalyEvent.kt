package com.spirit.scan.entity

import com.spirit.scan.ml.JonesResult

data class LocationContext(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class AnomalyEvent(
    val jones: JonesResult,
    val location: LocationContext,
    val timestamp: Long = System.currentTimeMillis()
) {
    val label: String get() = jones.label
    val confidence: Float get() = jones.confidence
}
