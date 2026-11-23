package com.symverse.aiglasses.core

/** Realtime 서버와 JSON 이벤트를 주고받는 연결. */
interface RealtimeTransport {
    fun connect(listener: Listener)

    fun send(event: String)

    fun close()

    interface Listener {
        fun onOpen()

        fun onMessage(event: String)

        fun onFailure(message: String)
    }
}
