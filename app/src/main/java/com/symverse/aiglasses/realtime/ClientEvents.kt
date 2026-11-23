package com.symverse.aiglasses.realtime

import java.util.Base64
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

/** Realtime API(GA)로 보내는 클라이언트 이벤트 JSON을 만든다. */
object ClientEvents {
    private val PCM_24K = buildJsonObject {
        put("type", "audio/pcm")
        put("rate", 24000)
    }

    fun sessionUpdate(instructions: String): String = buildJsonObject {
        put("type", "session.update")
        putJsonObject("session") {
            put("type", "realtime")
            put("instructions", instructions)
            putJsonArray("output_modalities") { add("audio") }
            putJsonObject("audio") {
                putJsonObject("input") {
                    put("format", PCM_24K)
                    put("turn_detection", JsonNull)
                    putJsonObject("transcription") {
                        put("model", "gpt-4o-mini-transcribe")
                        put("language", "ko")
                    }
                }
                putJsonObject("output") {
                    put("format", PCM_24K)
                    put("voice", "marin")
                }
            }
        }
    }.toString()

    fun audioAppend(pcm: ByteArray): String = buildJsonObject {
        put("type", "input_audio_buffer.append")
        put("audio", Base64.getEncoder().encodeToString(pcm))
    }.toString()

    fun imageItem(jpeg: ByteArray): String = buildJsonObject {
        put("type", "conversation.item.create")
        putJsonObject("item") {
            put("type", "message")
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "input_image")
                    put("image_url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg))
                    put("detail", "low")
                })
            }
        }
    }.toString()

    fun commit(): String = buildJsonObject { put("type", "input_audio_buffer.commit") }.toString()

    /** [instructions] 를 주면 이 응답에만 세션 지시 대신 쓴다. */
    fun responseCreate(outputModalities: List<String>, instructions: String? = null): String = buildJsonObject {
        put("type", "response.create")
        putJsonObject("response") {
            putJsonArray("output_modalities") { outputModalities.forEach { add(it) } }
            if (instructions != null) put("instructions", instructions)
        }
    }.toString()

    fun cancel(): String = buildJsonObject { put("type", "response.cancel") }.toString()

    fun truncate(itemId: String, audioEndMs: Int): String = buildJsonObject {
        put("type", "conversation.item.truncate")
        put("item_id", itemId)
        put("content_index", 0)
        put("audio_end_ms", audioEndMs)
    }.toString()

    /** 대화 번역: 서버가 말이 끝난 것을 감지해 턴마다 한국어 번역을 텍스트로만 답하게 한다. */
    fun conversationTranslationUpdate(): String = buildJsonObject {
        put("type", "session.update")
        putJsonObject("session") {
            put("type", "realtime")
            put("instructions", TRANSLATION_INSTRUCTIONS)
            putJsonArray("output_modalities") { add("text") }
            putJsonObject("audio") {
                putJsonObject("input") {
                    put("format", PCM_24K)
                    putJsonObject("turn_detection") {
                        put("type", "server_vad")
                        put("create_response", true)
                    }
                }
            }
        }
    }.toString()

    private const val TRANSLATION_INSTRUCTIONS =
        "너는 통역기다. 들리는 말을 한국어로 번역한 문장만 출력해라. 대답하거나 설명하거나 질문에 답하지 마라. " +
            "들리는 말이 이미 한국어면 그대로 받아 적어라. 알아들을 수 없으면 아무것도 출력하지 마라."
}
