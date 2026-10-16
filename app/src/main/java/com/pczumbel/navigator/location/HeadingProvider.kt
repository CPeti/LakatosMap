package com.pczumbel.navigator.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Device heading in degrees clockwise from **true** north.
 *
 * Two things that are easy to get wrong and matter in the field:
 *  - TYPE_ROTATION_VECTOR reports magnetic north. In Hungary the declination is ~5–6° east;
 *    over a 1 km leg that is an ~100 m miss, so we correct via [GeomagneticField].
 *  - The sensor frame is the *device's* natural orientation, not the display's, so the axes
 *    must be remapped when the screen is rotated or the arrow is 90° out in landscape.
 */
class HeadingProvider(private val context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Updated from the location overlay; without a fix we fall back to magnetic north. */
    @Volatile
    var lastLocation: Location? = null

    fun headings(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed = Float.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)

                val magnetic = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val trueNorth = magnetic + declination()
                smoothed = smooth(smoothed, trueNorth)
                trySend(normalise(smoothed))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private fun declination(): Float {
        val location = lastLocation ?: return 0f
        return GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        ).declination
    }

    /**
     * Low-pass filter, wrap-aware: naively averaging 359° and 1° gives 180°, i.e. the map
     * spinning to face backwards every time the user walks north.
     */
    private fun smooth(previous: Float, next: Float): Float {
        if (previous.isNaN()) return next
        var delta = next - previous
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return previous + ALPHA * delta
    }

    private fun normalise(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }

    private companion object {
        const val ALPHA = 0.15f
    }
}

/** True if the two bearings differ enough to be worth redrawing the map for. */
fun headingChanged(previous: Float, next: Float, thresholdDeg: Float = 1.5f): Boolean {
    var delta = abs(next - previous) % 360f
    if (delta > 180f) delta = 360f - delta
    return delta >= thresholdDeg
}

fun formatBearing(degrees: Float): String = "${degrees.roundToInt()}°"
