package org.pulse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.pulse.core.YawSample

class AndroidYawSource(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var onYaw: ((YawSample) -> Unit)? = null

    fun start(onYaw: (YawSample) -> Unit) {
        this.onYaw = onYaw
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onYaw = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val yaw = orientation[0].toDouble()
        onYaw?.invoke(YawSample(timestampMs = System.currentTimeMillis(), yawRad = yaw))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
