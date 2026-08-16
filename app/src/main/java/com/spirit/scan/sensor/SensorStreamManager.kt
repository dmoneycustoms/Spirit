package com.spirit.scan.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.spirit.scan.SpiritApp

class SensorStreamManager(
    context: Context,
    private val onWindowReady: () -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mag = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val buffer = CircularBuffer(64)
    private var running = false
    private var lastEmitMs = 0L
    private val emitIntervalMs = 1500L

    fun start() {
        if (running) return
        if (mag == null || accel == null) {
            Log.e(SpiritApp.TAG, "Required sensors missing")
            return
        }
        manager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
        manager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        running = true
        Log.i(SpiritApp.TAG, "SensorStreamManager started")
    }

    fun stop() {
        if (!running) return
        manager.unregisterListener(this)
        buffer.clear()
        running = false
        Log.i(SpiritApp.TAG, "SensorStreamManager stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> buffer.addMag(event.timestamp, event.values)
            Sensor.TYPE_ACCELEROMETER -> buffer.addAccel(event.timestamp, event.values)
        }
        if (buffer.ready()) {
            val now = System.currentTimeMillis()
            if (now - lastEmitMs >= emitIntervalMs) {
                lastEmitMs = now
                onWindowReady()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
