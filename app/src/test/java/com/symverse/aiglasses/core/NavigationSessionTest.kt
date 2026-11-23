package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSessionTest {
    private val start = LatLng(35.0000, 139.0000)
    private val route = Route(
        distanceMeters = 202,
        durationSeconds = 160,
        steps = listOf(
            RouteStep("북쪽으로 이동", "DEPART", 111, LatLng(35.0010, 139.0000)),
            RouteStep("우회전", "TURN_RIGHT", 91, LatLng(35.0010, 139.0010)),
        ),
    )
    private var here: GeoFix? = GeoFix(start)
    private val requests = mutableListOf<Pair<LatLng, String>>()
    private val callbacks = mutableListOf<(RouteResult) -> Unit>()
    private val spoken = mutableListOf<String>()
    private val session = NavigationSession(
        routes = object : RouteFetcher {
            override fun fetch(origin: LatLng, destination: String, onResult: (RouteResult) -> Unit) {
                requests += origin to destination
                callbacks += onResult
            }
        },
        location = object : LocationSource {
            override fun current() = here
        },
        announce = { spoken += it },
    )

    @Test
    fun shouldSearchFromCurrentPositionThenGuideAndAnnounceTheFirstStep() {
        session.start("  도쿄타워 ")

        assertEquals(NavHud(phase = NavPhase.Searching, destination = "도쿄타워"), session.hud)
        assertEquals(listOf(start to "도쿄타워"), requests)

        callbacks.single()(RouteResult.Found(route))

        assertEquals(NavPhase.Guiding, session.hud.phase)
        assertEquals("우회전", session.hud.guidance?.nextInstruction)
        assertEquals(listOf("도쿄타워까지 200m. 북쪽으로 이동"), spoken)
    }

    @Test
    fun shouldFailWhenPositionIsUnknown() {
        here = null

        session.start("도쿄타워")

        assertEquals(
            NavHud(phase = NavPhase.Failed, destination = "도쿄타워", message = "현재 위치를 아직 모릅니다. 위치 권한과 GPS를 확인하세요."),
            session.hud,
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun shouldExplainWhenNoRouteOrRequestFails() {
        session.start("없는 곳")
        callbacks.last()(RouteResult.NotFound)

        assertEquals("'없는 곳'까지 가는 도보 경로를 찾지 못했습니다.", session.hud.message)
        assertEquals(NavPhase.Failed, session.hud.phase)

        session.start("도쿄타워")
        callbacks.last()(RouteResult.Failed("API key not valid"))

        assertEquals(NavHud(phase = NavPhase.Failed, destination = "도쿄타워", message = "API key not valid"), session.hud)
    }

    @Test
    fun shouldIgnoreBlankDestination() {
        session.start("   ")

        assertEquals(NavHud(), session.hud)
    }

    private fun guide() {
        session.start("도쿄타워")
        callbacks.last()(RouteResult.Found(route))
        spoken.clear()
    }

    @Test
    fun shouldUpdateGuidanceAndAnnounceEachNewStep() {
        guide()

        session.onLocation(LatLng(35.0005, 139.0000))

        assertEquals(0, session.hud.guidance?.stepIndex)
        assertTrue(spoken.isEmpty())

        session.onLocation(LatLng(35.00095, 139.0000))

        assertEquals(1, session.hud.guidance?.stepIndex)
        assertEquals(listOf("우회전"), spoken)
    }

    @Test
    fun shouldAnnounceArrivalOnceAndStopGuiding() {
        guide()
        session.onLocation(LatLng(35.00095, 139.0000))
        spoken.clear()

        session.onLocation(LatLng(35.0010, 139.00095))
        session.onLocation(LatLng(35.0010, 139.00097))

        assertEquals(NavPhase.Arrived, session.hud.phase)
        assertEquals(listOf("목적지에 도착했습니다."), spoken)
    }

    @Test
    fun shouldStopAndIgnoreLateRouteResult() {
        session.start("도쿄타워")

        session.stop()
        callbacks.last()(RouteResult.Found(route))
        session.onLocation(start)

        assertEquals(NavHud(), session.hud)
        assertTrue(spoken.isEmpty())
    }
}
