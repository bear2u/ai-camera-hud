package com.symverse.aiglasses.core

import com.symverse.aiglasses.realtime.ClientEvents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesSessionTest {
    private val transport = FakeTransport()
    private val mic = FakeMic()
    private val frames = FakeFrames()
    private val sink = FakeSink()
    private val session = GlassesSession("test-key", transport, mic, frames, sink)

    private fun connect() {
        session.start()
        transport.listener.onOpen()
    }

    private fun ask() {
        connect()
        session.tap()
        mic.emit(byteArrayOf(1))
        session.tap()
    }

    private fun receive(json: String) = transport.listener.onMessage(json)

    private fun sentTypes(): List<String> =
        transport.sent.map { Json.parseToJsonElement(it).jsonObject.getValue("type").jsonPrimitive.content }

    @Test
    fun shouldConnectAndSendSessionUpdateOnStart() {
        session.start()

        assertEquals(Status.Connecting, session.hud.status)
        assertEquals(1, transport.connectCount)

        transport.listener.onOpen()

        assertEquals(listOf("session.update"), sentTypes())
        assertEquals(Status.Idle, session.hud.status)
    }

    @Test
    fun shouldStreamMicAudioWhileListening() {
        connect()

        session.tap()

        assertEquals(Status.Listening, session.hud.status)
        assertTrue(mic.running)

        mic.emit(byteArrayOf(1, 2, 3))

        assertEquals(ClientEvents.audioAppend(byteArrayOf(1, 2, 3)), transport.sent.last())
    }

    @Test
    fun shouldSendFrameThenCommitAndRequestResponseOnSecondTap() {
        ask()

        assertFalse(mic.running)
        assertEquals(Status.Thinking, session.hud.status)
        assertEquals(
            listOf(
                "session.update",
                "input_audio_buffer.append",
                "conversation.item.create",
                "input_audio_buffer.commit",
                "response.create",
            ),
            sentTypes(),
        )
        assertEquals(ClientEvents.imageItem(byteArrayOf(9, 9)), transport.sent[2])
        assertEquals(ClientEvents.responseCreate(listOf("audio")), transport.sent.last())
    }

    @Test
    fun shouldShowAssistantTranscriptOnHudAsDeltasArrive() {
        ask()

        receive("""{"type":"response.output_audio_transcript.delta","delta":"안녕"}""")
        receive("""{"type":"response.output_audio_transcript.delta","delta":"하세요"}""")

        assertEquals("안녕하세요", session.hud.assistantText)
    }

    @Test
    fun shouldPlayAudioDeltasToSink() {
        ask()

        receive("""{"type":"response.output_audio.delta","item_id":"item_9","delta":"AQID"}""")

        assertArrayEquals(byteArrayOf(1, 2, 3), sink.played.single())
        assertEquals(Status.Speaking, session.hud.status)
    }

    @Test
    fun shouldShowUserTranscriptOnHud() {
        ask()

        receive("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"이게 뭐야?"}""")

        assertEquals("이게 뭐야?", session.hud.userText)
    }

    @Test
    fun shouldReturnToIdleOnResponseDone() {
        ask()
        receive("""{"type":"response.output_audio_transcript.delta","delta":"컵이에요"}""")
        receive("""{"type":"response.output_audio.delta","item_id":"item_9","delta":"AQID"}""")

        receive("""{"type":"response.done"}""")

        assertEquals(Status.Idle, session.hud.status)
        assertEquals("컵이에요", session.hud.assistantText)
    }

    @Test
    fun shouldCancelTruncateAndStopPlaybackWhenTappedWhileSpeaking() {
        ask()
        receive("""{"type":"response.output_audio.delta","item_id":"item_9","delta":"AQID"}""")
        sink.playedMs = 1200
        val stopsBefore = sink.stopCount

        session.tap()

        assertEquals(stopsBefore + 1, sink.stopCount)
        assertEquals(
            listOf(ClientEvents.cancel(), ClientEvents.truncate("item_9", 1200)),
            transport.sent.takeLast(2),
        )
        assertEquals(Status.Idle, session.hud.status)
        assertFalse(mic.running)

        receive("""{"type":"response.output_audio.delta","item_id":"item_9","delta":"AQID"}""")

        assertEquals(1, sink.played.size)
        assertEquals(Status.Idle, session.hud.status)
    }

    @Test
    fun shouldShowErrorOnHudWhenTransportFails() {
        connect()
        session.tap()

        transport.listener.onFailure("연결 실패: HTTP 401")

        assertEquals(Status.Error, session.hud.status)
        assertEquals("연결 실패: HTTP 401", session.hud.error)
        assertFalse(mic.running)
    }

    @Test
    fun shouldShowErrorWhenApiKeyMissing() {
        val keyless = GlassesSession("", transport, mic, frames, sink)

        keyless.start()

        assertEquals(Status.Error, keyless.hud.status)
        assertEquals("API 키가 없습니다. local.properties 의 OPENAI_API_KEY 를 채우세요.", keyless.hud.error)
        assertEquals(0, transport.connectCount)
    }

    @Test
    fun shouldStopLeftoverPlaybackInsteadOfListeningWhenTappedAfterResponseDone() {
        ask()
        receive("""{"type":"response.output_audio.delta","item_id":"item_9","delta":"AQID"}""")
        receive("""{"type":"response.done"}""")
        sink.playedMs = 800

        session.tap()

        assertEquals(ClientEvents.truncate("item_9", 800), transport.sent.last())
        assertEquals(Status.Idle, session.hud.status)
        assertFalse(mic.running)

        session.tap()

        assertEquals(Status.Listening, session.hud.status)
    }

    @Test
    fun shouldReconnectWhenTappedAfterError() {
        connect()
        transport.listener.onFailure("연결이 닫혔습니다 (1006)")

        session.tap()

        assertEquals(2, transport.connectCount)
        assertEquals(Status.Connecting, session.hud.status)
        assertNull(session.hud.error)
    }

    @Test
    fun shouldShowApiErrorAndReturnToIdle() {
        ask()

        receive("""{"type":"error","error":{"message":"buffer too small"}}""")

        assertEquals(Status.Idle, session.hud.status)
        assertEquals("buffer too small", session.hud.error)
    }

    @Test
    fun shouldClearPreviousTextsWhenListeningStarts() {
        ask()
        receive("""{"type":"response.output_audio_transcript.delta","delta":"컵이에요"}""")
        receive("""{"type":"error","error":{"message":"oops"}}""")

        session.tap()

        assertEquals(HudState(status = Status.Listening), session.hud)
    }
}
