package com.fos.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Reads the accelerometer and exposes a smoothed tilt in -1..1 for both axes.
 * This drives the Solo Tilt style depth / perspective wallpaper and icon parallax.
 */
class TiltSensor(ctx: Context, private val s: Settings) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    private var fx = 0f
    private var fy = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var ready = false

    fun start() {
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        ready = false
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    /** Make the current phone angle the neutral position. */
    fun recenter() {
        baseX = fx
        baseY = fy
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ax = (e.values[0] / 9.81f).coerceIn(-1f, 1f)
        val ay = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
        if (!ready) {
            fx = ax; fy = ay; baseX = ax; baseY = ay
            ready = true
        }
        val a = s.tiltSmooth.coerceIn(0f, 0.97f)
        fx = fx * a + ax * (1f - a)
        fy = fy * a + ay * (1f - a)
        if (s.autoCenter) {
            baseX += (fx - baseX) * 0.004f
            baseY += (fy - baseY) * 0.004f
        }
        val sx = if (s.invX) -1f else 1f
        val sy = if (s.invY) -1f else 1f
        x = ((fx - baseX) * s.tiltSens).coerceIn(-1f, 1f) * sx
        y = ((fy - baseY) * s.tiltSens).coerceIn(-1f, 1f) * sy
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
