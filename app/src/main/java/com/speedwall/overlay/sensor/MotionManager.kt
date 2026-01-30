package com.speedwall.overlay.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2

/**
 * Provides device roll correction using the gravity sensor.
 *
 * Android equivalent of iOS CMMotionManager-based MotionManager.
 * Uses TYPE_GRAVITY sensor with a low-pass filter to compute a smoothed
 * roll angle. The correction is exposed in degrees via [rollCorrectionDegrees].
 */
class MotionManager(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gravitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _rollCorrectionDegrees = MutableStateFlow(0f)
    val rollCorrectionDegrees: StateFlow<Float> = _rollCorrectionDegrees.asStateFlow()

    private var smoothedRoll = 0.0
    private val smoothing = 0.15
    private var isRunning = false

    fun start() {
        if (isRunning || gravitySensor == null) return
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        isRunning = true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isRunning = false
        smoothedRoll = 0.0
        _rollCorrectionDegrees.value = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GRAVITY) return
        val gx = event.values[0].toDouble()
        val gy = event.values[1].toDouble()
        // atan2(gravity.x, -gravity.y) — same as iOS
        val raw = atan2(gx, -gy)
        smoothedRoll += (raw - smoothedRoll) * smoothing
        _rollCorrectionDegrees.value = Math.toDegrees(-smoothedRoll).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
