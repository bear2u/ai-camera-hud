package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTesterTest {
    private val opened = mutableListOf<Pair<AppSettings, FakeTransport>>()
    private val results = mutableListOf<KeyTestResult>()
    private val tester = KeyTester(
        open = { settings -> FakeTransport().also { opened += settings to it } },
        onResult = { results += it },
    )

    @Test
    fun shouldFailWithoutConnectingWhenKeyIsBlank() {
        tester.test(AppSettings(apiKey = "  "))

        assertEquals(listOf<KeyTestResult>(KeyTestResult.Failure("API 키를 입력하세요.")), results)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun shouldReportTestingThenSuccessWhenSessionIsCreated() {
        tester.test(AppSettings(apiKey = " my-key ", model = "gpt-realtime-2.1-mini"))

        assertEquals(listOf<KeyTestResult>(KeyTestResult.Testing), results)
        val (settings, transport) = opened.single()
        assertEquals(AppSettings(apiKey = "my-key", model = "gpt-realtime-2.1-mini"), settings)
        assertEquals(1, transport.connectCount)

        transport.listener.onOpen()
        transport.listener.onMessage("""{"type":"session.created","session":{}}""")

        assertEquals(KeyTestResult.Success, results.last())
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun shouldReportServerErrorMessage() {
        tester.test(AppSettings(apiKey = "bad-key"))
        val transport = opened.single().second

        transport.listener.onMessage("""{"type":"error","error":{"message":"Incorrect API key provided"}}""")

        assertEquals(KeyTestResult.Failure("Incorrect API key provided"), results.last())
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun shouldReportConnectionFailure() {
        tester.test(AppSettings(apiKey = "my-key"))

        opened.single().second.listener.onFailure("연결 실패: HTTP 401")

        assertEquals(KeyTestResult.Failure("연결 실패: HTTP 401"), results.last())
    }

    @Test
    fun shouldIgnoreResultsOfSupersededOrFinishedTest() {
        tester.test(AppSettings(apiKey = "first"))
        tester.test(AppSettings(apiKey = "second"))
        val (first, second) = opened.map { it.second }

        assertEquals(1, first.closeCount)

        first.listener.onMessage("""{"type":"session.created","session":{}}""")

        assertEquals(KeyTestResult.Testing, results.last())

        second.listener.onMessage("""{"type":"session.created","session":{}}""")
        second.listener.onFailure("연결이 닫혔습니다 (1000)")

        assertEquals(KeyTestResult.Success, results.last())
    }

    @Test
    fun shouldStopReportingAfterCancel() {
        tester.test(AppSettings(apiKey = "my-key"))
        val transport = opened.single().second

        tester.cancel()
        transport.listener.onMessage("""{"type":"session.created","session":{}}""")

        assertEquals(listOf<KeyTestResult>(KeyTestResult.Testing), results)
        assertEquals(1, transport.closeCount)
    }
}
