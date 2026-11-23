package com.symverse.aiglasses.core

import com.symverse.aiglasses.realtime.ServerEvent

sealed interface KeyTestResult {
    data object Testing : KeyTestResult

    data object Success : KeyTestResult

    data class Failure(val message: String) : KeyTestResult
}

/** 설정 화면에서 입력한 키와 모델로 실제 서버에 연결되는지 확인한다. */
class KeyTester(
    private val open: (AppSettings) -> RealtimeTransport,
    private val onResult: (KeyTestResult) -> Unit,
) {
    private var current: RealtimeTransport? = null

    @Synchronized
    fun test(settings: AppSettings) {
        val trimmed = settings.copy(apiKey = settings.apiKey.trim())
        if (trimmed.apiKey.isEmpty()) {
            onResult(KeyTestResult.Failure("API 키를 입력하세요."))
            return
        }
        cancel()
        onResult(KeyTestResult.Testing)
        val transport = open(trimmed)
        current = transport
        transport.connect(object : RealtimeTransport.Listener {
            override fun onOpen() = Unit

            override fun onMessage(event: String) {
                when (val received = ServerEvent.decode(event)) {
                    is ServerEvent.SessionCreated -> finish(transport, KeyTestResult.Success)
                    is ServerEvent.Error -> finish(transport, KeyTestResult.Failure(received.message))
                    else -> Unit
                }
            }

            override fun onFailure(message: String) = finish(transport, KeyTestResult.Failure(message))
        })
    }

    /** 진행 중인 확인을 버린다. 결과는 더 이상 알리지 않는다. */
    @Synchronized
    fun cancel() {
        current?.close()
        current = null
    }

    @Synchronized
    private fun finish(transport: RealtimeTransport, result: KeyTestResult) {
        if (current !== transport) return
        current = null
        transport.close()
        onResult(result)
    }
}
