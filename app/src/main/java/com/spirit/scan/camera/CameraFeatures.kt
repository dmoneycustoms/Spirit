package com.spirit.scan.camera

data class CameraFeatures(
    val noiseVariance: Float = 0f,
    val motionMagnitude: Float = 0f,
    val brightness: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = noiseVariance > 0f || motionMagnitude > 0f
}
