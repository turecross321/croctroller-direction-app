import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.croctrollercompass.CroctrollerWebSocketClient
import kotlinx.coroutines.delay
import java.net.URI

class CompassWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams), SensorEventListener {

    private var webSocket: CroctrollerWebSocketClient? = null
    private val delayMillis: Long = 500

    private var sensorManager: SensorManager? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var calibrationOffset: Float? = null
    private var isCalibrated = false // Flag to track calibration status

    override suspend fun doWork(): Result {
        val serverUrl = inputData.getString("SERVER_URL")
        val uri: URI = URI(serverUrl)
        webSocket = CroctrollerWebSocketClient(uri) { }
        webSocket!!.connect()
        Log.d("TAG", "CroctrollerClient initialized and connected")

        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magneticField = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI)

        // Start a loop to send azimuth data
        while (!isStopped) {
            delay(delayMillis)

            // Check if the sensors have reported data
            if (hasGravity && hasGeomagnetic) {
                // Only calibrate if it hasn't been done yet
                if (!isCalibrated) {
                    val isCalibrateRequested = inputData.getBoolean("CALIBRATE", false)
                    if (isCalibrateRequested) {
                        calibrate()  // Call the calibrate function
                        isCalibrated = true // Set the flag to indicate calibration has been done
                    }
                }

                // Continue processing azimuth data
                processAzimuthData()
            }
        }

        // Clean up resources
        sensorManager?.unregisterListener(this)
        webSocket?.closeConnection(1000, "")
        return Result.success()
    }

    private fun processAzimuthData() {
        if (hasGravity && hasGeomagnetic) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)

                // Calculate azimuth (compass data)
                val azimuthInRadians = orientation[0]
                var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

                // Adjust azimuth based on calibration offset
                azimuthInDegrees = adjustForCalibration(azimuthInDegrees)

                // Process the azimuth (send it to the server)
                processCompassData(azimuthInDegrees)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, gravity, 0, it.values.size)
                    hasGravity = true
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, geomagnetic, 0, it.values.size)
                    hasGeomagnetic = true
                }
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Not used
    }

    private fun processCompassData(azimuth: Float) {
        Log.d("TAG", "Sending azimuth: $azimuth")
        webSocket?.sendMessage("{\"azimuth\":$azimuth}")
    }

    private fun adjustForCalibration(azimuth: Float): Float {
        return if (calibrationOffset != null) {
            var adjustedAzimuth = azimuth - calibrationOffset!!
            if (adjustedAzimuth < 0) adjustedAzimuth += 360 // Normalize to 0-360 range
            adjustedAzimuth
        } else {
            azimuth // No calibration applied
        }
    }

    private fun calibrate() {
        calibrationOffset = getCurrentAzimuth()
        Log.d("TAG", "Calibration set. Offset: $calibrationOffset")
    }

    private fun getCurrentAzimuth(): Float? {
        return if (hasGravity && hasGeomagnetic) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)

                // Calculate current azimuth in degrees
                val azimuthInRadians = orientation[0]
                Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            } else {
                null
            }
        } else {
            null
        }
    }
}
