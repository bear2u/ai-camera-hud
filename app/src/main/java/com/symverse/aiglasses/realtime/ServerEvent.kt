package com.symverse.aiglasses.realtime

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Realtime API(GA)가 보내는 서버 이벤트 중 앱이 쓰는 것. */
sealed interface ServerEvent {
    class AudioDelta(val itemId: String, val pcm: ByteArray) : ServerEvent

    data class AudioTranscriptDelta(val text: String) : ServerEvent

    data class TextDelta(val text: String) : ServerEvent

    data class InputTranscript(val text: String) : ServerEvent

    data object ResponseDone : ServerEvent

    data class Error(val message: String) : ServerEvent

    data object Unknown : ServerEvent

    data object SessionCreated : ServerEvent

    companion object {
        fun decode(json: String): ServerEvent {
            val event = Json.parseToJsonElement(json).jsonObject
            return when (event.str("type")) {
                "response.output_audio.delta" ->
                    AudioDelta(event.str("item_id"), Base64.getDecoder().decode(event.str("delta")))
                "response.output_audio_transcript.delta" -> AudioTranscriptDelta(event.str("delta"))
                "response.output_text.delta" -> TextDelta(event.str("delta"))
                "conversation.item.input_audio_transcription.completed" -> InputTranscript(event.str("transcript"))
                "response.done" -> ResponseDone
                "error" -> Error(event["error"]?.jsonObject?.str("message").orEmpty())
                "session.created" -> SessionCreated
                else -> Unknown
            }
        }

        private fun JsonObject.str(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()
    }
}
