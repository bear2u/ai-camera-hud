package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {
    @Test
    fun shouldMeasureOneDegreeOfLatitudeAsAbout111Kilometers() {
        assertEquals(111_195.0, Geo.distanceMeters(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 100.0)
    }

    @Test
    fun shouldGiveBearingClockwiseFromNorth() {
        val origin = LatLng(35.0, 139.0)

        assertEquals(0.0, Geo.bearingDegrees(origin, LatLng(35.1, 139.0)), 0.5)
        assertEquals(90.0, Geo.bearingDegrees(origin, LatLng(35.0, 139.1)), 0.5)
        assertEquals(180.0, Geo.bearingDegrees(origin, LatLng(34.9, 139.0)), 0.5)
        assertEquals(270.0, Geo.bearingDegrees(origin, LatLng(35.0, 138.9)), 0.5)
    }

    @Test
    fun shouldNameCompassDirectionInKorean() {
        assertEquals("북", Geo.compassName(0f))
        assertEquals("북동", Geo.compassName(47f))
        assertEquals("동", Geo.compassName(90f))
        assertEquals("남서", Geo.compassName(225f))
        assertEquals("북", Geo.compassName(359f))
    }

    @Test
    fun shouldDescribeCoordinatesAddressAndHeadingForTheAi() {
        val text = GeoFix(LatLng(35.681236, 139.767125), headingDegrees = 47f, address = "東京都千代田区丸の内1丁目").describe()

        assertEquals(
            "[현재 위치 정보] 위도 35.68124, 경도 139.76713. 주소: 東京都千代田区丸の内1丁目. 카메라가 향한 방향: 북동(47도). " +
                "이 정보는 보이는 건물이나 장소가 무엇인지 알아내는 데 써라. 묻지 않으면 좌표는 말하지 마라.",
            text,
        )
    }

    @Test
    fun shouldLeaveOutAddressAndHeadingWhenUnknown() {
        val text = GeoFix(LatLng(35.0, 139.0)).describe()

        assertEquals(
            "[현재 위치 정보] 위도 35.00000, 경도 139.00000. " +
                "이 정보는 보이는 건물이나 장소가 무엇인지 알아내는 데 써라. 묻지 않으면 좌표는 말하지 마라.",
            text,
        )
        assertNull(GeoFix(LatLng(35.0, 139.0)).headingDegrees)
    }
}
