package com.symverse.aiglasses.core

import kotlin.math.roundToInt

/** 지금 화면에 보여 줄 길안내. */
data class NavState(
    val stepIndex: Int,
    /** 지금 걷고 있는 구간의 안내. */
    val currentInstruction: String,
    /** 이 구간이 끝나는 지점에서 할 일. */
    val nextInstruction: String,
    val nextManeuver: String,
    val distanceToNextMeters: Int,
    val remainingMeters: Int,
    /** 다음 지점이 있는 방향. 북쪽 0, 시계 방향. */
    val bearingToNext: Float,
    val arrived: Boolean,
)

/** 받은 경로를 따라 현재 위치에 맞는 안내를 고른다. */
class Navigator(private val route: Route) {
    private var index = 0

    fun update(position: LatLng): NavState {
        // 구간 끝에 가까이 오면 다음 구간으로 넘어간다.
        while (index < route.steps.lastIndex && Geo.distanceMeters(position, route.steps[index].end) < TURN_RADIUS_METERS) {
            index++
        }
        val step = route.steps[index]
        val next = route.steps.getOrNull(index + 1)
        val toEnd = Geo.distanceMeters(position, step.end)
        return NavState(
            stepIndex = index,
            currentInstruction = step.instruction,
            nextInstruction = next?.instruction ?: "목적지 도착",
            nextManeuver = next?.maneuver ?: "DESTINATION",
            distanceToNextMeters = toEnd.roundToInt(),
            remainingMeters = (toEnd + route.steps.drop(index + 1).sumOf { it.distanceMeters }).roundToInt(),
            bearingToNext = Geo.bearingDegrees(position, step.end).toFloat(),
            arrived = next == null && toEnd < ARRIVAL_RADIUS_METERS,
        )
    }

    private companion object {
        const val TURN_RADIUS_METERS = 15.0
        const val ARRIVAL_RADIUS_METERS = 20.0
    }
}
