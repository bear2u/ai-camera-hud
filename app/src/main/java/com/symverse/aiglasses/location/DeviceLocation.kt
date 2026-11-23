package com.symverse.aiglasses.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.symverse.aiglasses.core.Geo
import com.symverse.aiglasses.core.GeoFix
import com.symverse.aiglasses.core.LatLng
import com.symverse.aiglasses.core.LocationSource
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2

/** 기기의 위치(GPS·네트워크), 카메라가 향한 방향(나침반), 대략의 주소를 준다. */
class DeviceLocation(private val context: Context) : LocationSource {
    private val locations = context.getSystemService(LocationManager::class.java)
    private val sensors = context.getSystemService(SensorManager::class.java)

    @Volatile
    private var position: LatLng? = null

    @Volatile
    private var heading: Float? = null

    @Volatile
    private var address: String? = null
    private var addressAt: LatLng? = null
    private var lastGpsAt = 0L
    private var declination = 0f
    private var running = false

    /** 위치가 바뀔 때마다 메인 스레드에서 불린다. */
    var onPosition: (LatLng) -> Unit = {}

    /** 카메라 방향이 눈에 띄게 바뀔 때마다 메인 스레드에서 불린다. */
    var onHeading: (Float) -> Unit = {}

    override fun current(): GeoFix? = position?.let { GeoFix(it, heading, address) }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in locations.allProviders }
            .forEach { provider ->
                runCatching {
                    locations.getLastKnownLocation(provider)?.let(::accept)
                    locations.requestLocationUpdates(provider, 1000L, 1f, locationListener, Looper.getMainLooper())
                }
            }
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        locations.removeUpdates(locationListener)
        sensors.unregisterListener(sensorListener)
    }

    private val locationListener = LocationListener { accept(it) }

    private fun accept(location: Location) {
        // GPS 가 잡히는 동안에는 덜 정확한 네트워크 위치로 덮어쓰지 않는다.
        val now = System.currentTimeMillis()
        if (location.provider == LocationManager.GPS_PROVIDER) {
            lastGpsAt = now
        } else if (now - lastGpsAt < GPS_FRESH_MS) {
            return
        }
        val fix = LatLng(location.latitude, location.longitude)
        position = fix
        declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            now,
        ).declination
        refreshAddress(fix)
        onPosition(fix)
    }

    /** 50m 넘게 움직였을 때만 주소를 다시 찾는다. */
    private fun refreshAddress(fix: LatLng) {
        val last = addressAt
        if (last != null && Geo.distanceMeters(last, fix) < ADDRESS_REFRESH_METERS) return
        addressAt = fix
        thread(name = "geocoder", isDaemon = true) {
            address = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(fix.latitude, fix.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            }.getOrNull() ?: address
        }
    }

    private val sensorListener = object : SensorEventListener {
        private val rotation = FloatArray(9)

        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            // 뒷면 카메라가 보는 방향(기기의 -Z 축)을 동쪽·북쪽 성분으로 본다. 화면 회전과 무관하다.
            val east = -rotation[2]
            val north = -rotation[5]
            val magnetic = Math.toDegrees(atan2(east, north).toDouble()).toFloat()
            val degrees = ((magnetic + declination) % 360 + 360) % 360
            val previous = heading
            if (previous == null || angleBetween(previous, degrees) >= HEADING_STEP_DEGREES) {
                heading = degrees
                onHeading(degrees)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun angleBetween(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }

    private companion object {
        const val GPS_FRESH_MS = 10_000L
        const val ADDRESS_REFRESH_METERS = 50.0
        const val HEADING_STEP_DEGREES = 3f
    }
}
