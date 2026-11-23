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
    private const val SAMPLE_RATE = 24000

    fun sessionUpdate(instructions: String): String = buildJsonObject {
        put("type", "session.update")
        putJsonObject("session") {
            put("type", "realtime")
            put("instructions", instructions)
            putJsonArray("output_modalities") { add("audio") }
            putJsonObject("audio") {
                putJsonObject("input") {
                    putJsonObject("format") {
                        put("type", "audio/pcm")
                        put("rate", SAMPLE_RATE)
                    }
                    put("turn_detection", JsonNull)
                    putJsonObject("transcription") {
                        put("model", "gpt-4o-mini-transcribe")
                        put("language", "ko")
                    }
                }
                putJsonObject("output") {
                    putJsonObject("format") {
                        put("type", "audio/pcm")
                        put("rate", SAMPLE_RATE)
                    }
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

    fun responseCreate(outputModalities: List<String>): String = buildJsonObject {
        put("type", "response.create")
        putJsonObject("response") {
            putJsonArray("output_modalities") { outputModalities.forEach { add(it) } }
        }
    }.toString()

    fun cancel(): String = buildJsonObject { put("type", "response.cancel") }.toString()

    fun truncate(itemId: String, audioEndMs: Int): String = buildJsonObject {
        put("type", "conversation.item.truncate")
        put("item_id", itemId)
        put("content_index", 0)
        put("audio_end_ms", audioEndMs)
    }.toString()
}
