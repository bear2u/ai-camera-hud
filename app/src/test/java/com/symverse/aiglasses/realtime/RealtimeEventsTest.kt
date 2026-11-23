package com.symverse.aiglasses.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeEventsTest {
    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    @Test
    fun shouldEncodeSessionUpdateWithPcm24kAndNoTurnDetection() {
        val event = parse(ClientEvents.sessionUpdate(instructions = "짧게 답해라"))

        assertEquals("session.update", event.str("type"))
        val session = event.obj("session")
        assertEquals("realtime", session.str("type"))
        assertEquals("짧게 답해라", session.str("instructions"))
        assertEquals(listOf("audio"), session.getValue("output_modalities").jsonArray.map { it.jsonPrimitive.content })
        val input = session.obj("audio").obj("input")
        assertEquals("audio/pcm", input.obj("format").str("type"))
        assertEquals(24000, input.obj("format").getValue("rate").jsonPrimitive.int)
        assertEquals(JsonNull, input.getValue("turn_detection"))
        val output = session.obj("audio").obj("output")
        assertEquals("audio/pcm", output.obj("format").str("type"))
        assertEquals(24000, output.obj("format").getValue("rate").jsonPrimitive.int)
        assertEquals("marin", output.str("voice"))
    }

    @Test
    fun shouldEncodeInputTranscriptionInSessionUpdate() {
        val input = parse(ClientEvents.sessionUpdate(instructions = "")).obj("session").obj("audio").obj("input")

        assertEquals("gpt-4o-mini-transcribe", input.obj("transcription").str("model"))
        assertEquals("ko", input.obj("transcription").str("language"))
    }

    @Test
    fun shouldEncodeAudioAppendAsBase64() {
        val event = parse(ClientEvents.audioAppend(byteArrayOf(1, 2, 3)))

        assertEquals("input_audio_buffer.append", event.str("type"))
        assertEquals("AQID", event.str("audio"))
    }

    @Test
    fun shouldEncodeImageItemAsJpegDataUrl() {
        val event = parse(ClientEvents.imageItem(byteArrayOf(1, 2, 3)))

        assertEquals("conversation.item.create", event.str("type"))
        val item = event.obj("item")
        assertEquals("message", item.str("type"))
        assertEquals("user", item.str("role"))
        val content = item.getValue("content").jsonArray.single().jsonObject
        assertEquals("input_image", content.str("type"))
        assertEquals("data:image/jpeg;base64,AQID", content.str("image_url"))
        assertEquals("low", content.str("detail"))
    }

    @Test
    fun shouldEncodeCommitAndResponseCreateWithModalities() {
        assertEquals("input_audio_buffer.commit", parse(ClientEvents.commit()).str("type"))

        val event = parse(ClientEvents.responseCreate(outputModalities = listOf("text")))
        assertEquals("response.create", event.str("type"))
        assertEquals(
            listOf("text"),
            event.obj("response").getValue("output_modalities").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun shouldEncodeCancelAndTruncate() {
        assertEquals("response.cancel", parse(ClientEvents.cancel()).str("type"))

        val event = parse(ClientEvents.truncate(itemId = "item_1", audioEndMs = 1500))
        assertEquals("conversation.item.truncate", event.str("type"))
        assertEquals("item_1", event.str("item_id"))
        assertEquals(0, event.getValue("content_index").jsonPrimitive.int)
        assertEquals(1500, event.getValue("audio_end_ms").jsonPrimitive.int)
    }

    @Test
    fun shouldDecodeOutputAudioDelta() {
        val event = ServerEvent.decode("""{"type":"response.output_audio.delta","item_id":"item_9","delta":"AQID"}""")

        event as ServerEvent.AudioDelta
        assertEquals("item_9", event.itemId)
        assertArrayEquals(byteArrayOf(1, 2, 3), event.pcm)
    }

    @Test
    fun shouldDecodeOutputAudioTranscriptDelta() {
        val event = ServerEvent.decode("""{"type":"response.output_audio_transcript.delta","delta":"안녕"}""")

        assertEquals(ServerEvent.AudioTranscriptDelta("안녕"), event)
    }

    @Test
    fun shouldDecodeOutputTextDelta() {
        val event = ServerEvent.decode("""{"type":"response.output_text.delta","delta":"출구"}""")

        assertEquals(ServerEvent.TextDelta("출구"), event)
    }

    @Test
    fun shouldDecodeInputTranscriptionCompleted() {
        val event = ServerEvent.decode(
            """{"type":"conversation.item.input_audio_transcription.completed","item_id":"item_1","transcript":"이게 뭐야?"}""",
        )

        assertEquals(ServerEvent.InputTranscript("이게 뭐야?"), event)
    }

    @Test
    fun shouldDecodeResponseDone() {
        val event = ServerEvent.decode("""{"type":"response.done","response":{"status":"completed"}}""")

        assertEquals(ServerEvent.ResponseDone, event)
    }

    @Test
    fun shouldDecodeErrorEvent() {
        val event = ServerEvent.decode("""{"type":"error","error":{"type":"invalid_request_error","message":"buffer too small"}}""")

        assertEquals(ServerEvent.Error("buffer too small"), event)
    }

    @Test
    fun shouldIgnoreUnknownEventType() {
        assertEquals(ServerEvent.Unknown, ServerEvent.decode("""{"type":"rate_limits.updated","rate_limits":[]}"""))
    }

    @Test
    fun shouldEncodeConversationTranslationUpdateWithServerVadAndTextOutput() {
        val session = parse(ClientEvents.conversationTranslationUpdate()).obj("session")

        assertEquals("realtime", session.str("type"))
        assertEquals(listOf("text"), session.getValue("output_modalities").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("server_vad", session.obj("audio").obj("input").obj("turn_detection").str("type"))
        assertEquals(true, session.obj("audio").obj("input").obj("turn_detection").getValue("create_response").jsonPrimitive.content.toBoolean())
        assertEquals(true, session.str("instructions").contains("한국어로 번역"))
    }

    @Test
    fun shouldEncodeImageItemWithHighDetailWhenRequested() {
        val content = parse(ClientEvents.imageItem(byteArrayOf(1, 2, 3), detail = "high"))
            .obj("item").getValue("content").jsonArray.single().jsonObject

        assertEquals("high", content.str("detail"))
    }

    @Test
    fun shouldDecodeSessionCreated() {
        assertEquals(ServerEvent.SessionCreated, ServerEvent.decode("""{"type":"session.created","session":{"id":"sess_1"}}"""))
    }
}
