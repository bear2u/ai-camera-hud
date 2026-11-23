package com.symverse.aiglasses.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesApiTest {
    @Test
    fun shouldBuildWalkingRequestFromCoordinatesToDestinationText() {
        val body = Json.parseToJsonElement(RoutesApi.requestBody(LatLng(35.681236, 139.767125), "東京タワー")).jsonObject

        val origin = body.getValue("origin").jsonObject.getValue("location").jsonObject.getValue("latLng").jsonObject
        assertEquals(35.681236, origin.getValue("latitude").jsonPrimitive.double, 1e-9)
        assertEquals(139.767125, origin.getValue("longitude").jsonPrimitive.double, 1e-9)
        assertEquals("東京タワー", body.getValue("destination").jsonObject.getValue("address").jsonPrimitive.content)
        assertEquals("WALK", body.getValue("travelMode").jsonPrimitive.content)
        assertEquals("ko", body.getValue("languageCode").jsonPrimitive.content)
        assertEquals("METRIC", body.getValue("units").jsonPrimitive.content)
    }

    @Test
    fun shouldParseStepsFromRouteResponse() {
        val response = """
            {"routes":[{"distanceMeters":310,"duration":"245s","legs":[{"steps":[
              {"distanceMeters":120,"endLocation":{"latLng":{"latitude":35.6820,"longitude":139.7671}},
               "navigationInstruction":{"maneuver":"DEPART","instructions":"북쪽으로 이동"}},
              {"distanceMeters":190,"endLocation":{"latLng":{"latitude":35.6820,"longitude":139.7692}},
               "navigationInstruction":{"maneuver":"TURN_RIGHT","instructions":"우회전"}}
            ]}]}]}
        """.trimIndent()

        assertEquals(
            RouteResult.Found(
                Route(
                    distanceMeters = 310,
                    durationSeconds = 245,
                    steps = listOf(
                        RouteStep("북쪽으로 이동", "DEPART", 120, LatLng(35.6820, 139.7671)),
                        RouteStep("우회전", "TURN_RIGHT", 190, LatLng(35.6820, 139.7692)),
                    ),
                ),
            ),
            RoutesApi.parse(response),
        )
    }

    @Test
    fun shouldReportNotFoundWhenThereIsNoRoute() {
        assertEquals(RouteResult.NotFound, RoutesApi.parse("""{}"""))
        assertEquals(RouteResult.NotFound, RoutesApi.parse("""{"routes":[]}"""))
        assertEquals(RouteResult.NotFound, RoutesApi.parse("""{"routes":[{"distanceMeters":10,"duration":"5s","legs":[{}]}]}"""))
    }

    @Test
    fun shouldReportServerErrorMessage() {
        val response = """{"error":{"code":403,"message":"Routes API has not been used in project 1 before","status":"PERMISSION_DENIED"}}"""

        assertEquals(RouteResult.Failed("Routes API has not been used in project 1 before"), RoutesApi.parse(response))
        assertEquals(RouteResult.Failed("경로 응답을 읽지 못했습니다."), RoutesApi.parse("not json"))
    }
}
