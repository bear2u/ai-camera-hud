package com.symverse.aiglasses.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** 한 번의 안내에 해당하는 구간. [instruction] 대로 움직여 [end] 까지 [distanceMeters] 를 간다. */
data class RouteStep(val instruction: String, val maneuver: String, val distanceMeters: Int, val end: LatLng)

data class Route(val distanceMeters: Int, val durationSeconds: Int, val steps: List<RouteStep>)

sealed interface RouteResult {
    data class Found(val route: Route) : RouteResult

    data object NotFound : RouteResult

    data class Failed(val message: String) : RouteResult
}

/** Google Routes API(computeRoutes)의 요청 본문과 응답 해석. */
object RoutesApi {
    const val URL = "https://routes.googleapis.com/directions/v2:computeRoutes"

    /** 응답에 넣을 필드. Routes API 는 이 헤더가 없으면 오류를 낸다. */
    const val FIELD_MASK =
        "routes.distanceMeters,routes.duration,routes.legs.steps.distanceMeters," +
            "routes.legs.steps.endLocation,routes.legs.steps.navigationInstruction"

    fun requestBody(origin: LatLng, destination: String): String = buildJsonObject {
        putJsonObject("origin") {
            putJsonObject("location") {
                putJsonObject("latLng") {
                    put("latitude", origin.latitude)
                    put("longitude", origin.longitude)
                }
            }
        }
        putJsonObject("destination") { put("address", destination) }
        put("travelMode", "WALK")
        put("languageCode", "ko")
        put("units", "METRIC")
    }.toString()

    fun parse(response: String): RouteResult = try {
        parseRoot(Json.parseToJsonElement(response).jsonObject)
    } catch (e: RuntimeException) {
        RouteResult.Failed("경로 응답을 읽지 못했습니다.")
    }

    private fun parseRoot(root: JsonObject): RouteResult {
        root["error"]?.jsonObject?.let { error ->
            return RouteResult.Failed(error["message"]?.jsonPrimitive?.content ?: "경로를 요청하지 못했습니다.")
        }
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return RouteResult.NotFound
        val steps = route["legs"]?.jsonArray.orEmpty().flatMap { leg ->
            leg.jsonObject["steps"]?.jsonArray.orEmpty().map { step -> step.jsonObject.toStep() }
        }
        if (steps.isEmpty()) return RouteResult.NotFound
        return RouteResult.Found(
            Route(
                distanceMeters = route["distanceMeters"]?.jsonPrimitive?.int ?: 0,
                durationSeconds = route["duration"]?.jsonPrimitive?.content?.removeSuffix("s")?.toDoubleOrNull()?.toInt() ?: 0,
                steps = steps,
            ),
        )
    }

    private fun JsonObject.toStep(): RouteStep {
        val instruction = this["navigationInstruction"]?.jsonObject
        val end = getValue("endLocation").jsonObject.getValue("latLng").jsonObject
        return RouteStep(
            instruction = instruction?.get("instructions")?.jsonPrimitive?.content.orEmpty(),
            maneuver = instruction?.get("maneuver")?.jsonPrimitive?.content.orEmpty(),
            distanceMeters = this["distanceMeters"]?.jsonPrimitive?.int ?: 0,
            end = LatLng(end.getValue("latitude").jsonPrimitive.double, end.getValue("longitude").jsonPrimitive.double),
        )
    }
}
