package com.symverse.aiglasses.location

import com.symverse.aiglasses.core.LatLng
import com.symverse.aiglasses.core.RouteFetcher
import com.symverse.aiglasses.core.RouteResult
import com.symverse.aiglasses.core.RoutesApi
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Google Routes API 로 도보 경로를 받아 온다. [apiKey] 는 요청할 때마다 설정에서 읽는다. */
class GoogleRouteFetcher(private val apiKey: () -> String) : RouteFetcher {
    private val client = OkHttpClient()

    override fun fetch(origin: LatLng, destination: String, onResult: (RouteResult) -> Unit) {
        val key = apiKey()
        if (key.isBlank()) {
            onResult(RouteResult.Failed("설정에서 Google Maps API 키를 입력하세요."))
            return
        }
        val request = Request.Builder()
            .url(RoutesApi.URL)
            .header("X-Goog-Api-Key", key)
            .header("X-Goog-FieldMask", RoutesApi.FIELD_MASK)
            .post(RoutesApi.requestBody(origin, destination).toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(RouteResult.Failed("경로 서버에 연결하지 못했습니다."))
            }

            override fun onResponse(call: Call, response: Response) {
                // 오류일 때도 본문에 사유가 들어 있어 같은 해석기로 읽는다.
                onResult(RoutesApi.parse(response.use { it.body.string() }))
            }
        })
    }
}
