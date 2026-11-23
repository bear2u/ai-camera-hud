package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    // 북쪽으로 약 111m, 동쪽으로 약 91m, 다시 북쪽으로 약 111m.
    private val start = LatLng(35.0000, 139.0000)
    private val route = Route(
        distanceMeters = 313,
        durationSeconds = 240,
        steps = listOf(
            RouteStep("북쪽으로 이동", "DEPART", 111, LatLng(35.0010, 139.0000)),
            RouteStep("우회전", "TURN_RIGHT", 91, LatLng(35.0010, 139.0010)),
            RouteStep("좌회전", "TURN_LEFT", 111, LatLng(35.0020, 139.0010)),
        ),
    )
    private val navigator = Navigator(route)

    @Test
    fun shouldGuideTowardTheFirstTurnAtTheStart() {
        val state = navigator.update(start)

        assertEquals(0, state.stepIndex)
        assertEquals("북쪽으로 이동", state.currentInstruction)
        assertEquals("우회전", state.nextInstruction)
        assertEquals("TURN_RIGHT", state.nextManeuver)
        assertEquals(111.0, state.distanceToNextMeters.toDouble(), 2.0)
        assertEquals(313.0, state.remainingMeters.toDouble(), 4.0)
        assertEquals(0.0, state.bearingToNext.toDouble(), 1.0)
        assertFalse(state.arrived)
    }

    @Test
    fun shouldMoveToTheNextStepNearTheTurn() {
        navigator.update(start)

        val state = navigator.update(LatLng(35.00095, 139.0000))

        assertEquals(1, state.stepIndex)
        assertEquals("우회전", state.currentInstruction)
        assertEquals("좌회전", state.nextInstruction)
        assertEquals(91.0, state.distanceToNextMeters.toDouble(), 4.0)
        assertEquals(90.0, state.bearingToNext.toDouble(), 5.0)
    }

    @Test
    fun shouldAnnounceDestinationOnTheLastStepAndArriveNearTheEnd() {
        navigator.update(LatLng(35.00095, 139.0000))

        val last = navigator.update(LatLng(35.0010, 139.00095))

        assertEquals(2, last.stepIndex)
        assertEquals("목적지 도착", last.nextInstruction)
        assertEquals("DESTINATION", last.nextManeuver)
        assertFalse(last.arrived)

        val arrived = navigator.update(LatLng(35.00195, 139.0010))

        assertTrue(arrived.arrived)
    }
}
