package com.symverse.aiglasses.core

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double)

/** 지구 위 두 점 사이의 거리와 방향 계산. */
object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    /** [from] 에서 [to] 를 바라보는 방향. 북쪽이 0, 시계 방향으로 0 이상 360 미만. */
    fun bearingDegrees(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** 100m 미만은 그대로, 1km 미만은 10m 단위, 그 이상은 km 로 적는다. */
    fun formatDistance(meters: Int): String = when {
        meters < 100 -> "${meters}m"
        meters < 1000 -> "${meters / 10 * 10}m"
        else -> "%.1fkm".format(Locale.US, meters / 1000.0)
    }

    fun compassName(degrees: Float): String {
        val names = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
        return names[(((degrees % 360 + 360) % 360 + 22.5f) / 45f).toInt() % 8]
    }
}

/** 기기의 현재 위치. [headingDegrees] 는 카메라가 향한 방향(북쪽 0, 시계 방향). */
data class GeoFix(val position: LatLng, val headingDegrees: Float? = null, val address: String? = null) {
    /** AI가 보이는 장소를 알아내도록 질문과 함께 보내는 문장. */
    fun describe(): String = buildString {
        append("[현재 위치 정보] 위도 %.5f, 경도 %.5f. ".format(Locale.US, position.latitude, position.longitude))
        if (!address.isNullOrBlank()) append("주소: $address. ")
        if (headingDegrees != null) {
            append("카메라가 향한 방향: ${Geo.compassName(headingDegrees)}(${headingDegrees.roundToInt()}도). ")
        }
        append("이 정보는 보이는 건물이나 장소가 무엇인지 알아내는 데 써라. 묻지 않으면 좌표는 말하지 마라.")
    }
}

/** 기기의 현재 위치를 준다. 아직 모르면 null. */
interface LocationSource {
    fun current(): GeoFix?
}
