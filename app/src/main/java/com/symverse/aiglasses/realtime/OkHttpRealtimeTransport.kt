package com.symverse.aiglasses.realtime

import com.symverse.aiglasses.core.RealtimeTransport
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** OkHttp WebSocket으로 OpenAI Realtime API에 연결한다. */
class OkHttpRealtimeTransport(
    private val apiKey: String,
    private val url: String = "wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1",
) : RealtimeTransport {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var socket: WebSocket? = null

    override fun connect(listener: RealtimeTransport.Listener) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            // 이미 닫았거나 새 연결로 바뀐 소켓의 늦은 콜백은 버린다.
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (socket === webSocket) listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket === webSocket) listener.onMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) listener.onFailure("연결이 닫혔습니다 ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                val detail = response?.let { "HTTP ${it.code}" } ?: (t.message ?: t.javaClass.simpleName)
                listener.onFailure("연결 실패: $detail")
            }
        })
    }

    override fun send(event: String) {
        socket?.send(event)
    }

    override fun close() {
        val closing = socket
        socket = null
        closing?.close(1000, null)
    }
}
