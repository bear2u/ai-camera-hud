package com.symverse.aiglasses.realtime

import com.symverse.aiglasses.core.RealtimeTransport
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 OpenAI 서버에 연결해 프로토콜을 확인한다. 요금이 들기 때문에
 * 환경 변수 OPENAI_LIVE=1 일 때만 돈다. 키는 local.properties 에서 읽는다.
 */
class RealtimeLiveSmokeTest {
    private val messages = LinkedBlockingQueue<String>()
    private val log = StringBuilder()

    @Test
    fun shouldAnswerAboutImageWithAudioThenTranscribeSpokenInput() {
        assumeTrue(System.getenv("OPENAI_LIVE") == "1")
        val key = Properties().apply { File("../local.properties").inputStream().use(::load) }
            .getProperty("OPENAI_API_KEY").orEmpty()
        assumeTrue(key.isNotBlank())

        val transport = OkHttpRealtimeTransport(key)
        val opened = CountDownLatch(1)
        transport.connect(object : RealtimeTransport.Listener {
            override fun onOpen() = opened.countDown()

            override fun onMessage(event: String) {
                messages.put(event)
            }

            override fun onFailure(message: String) {
                messages.put("""{"type":"error","error":{"message":"$message"}}""")
                opened.countDown()
            }
        })
        try {
            assertTrue("WebSocket 이 열리지 않음", opened.await(15, TimeUnit.SECONDS))

            transport.send(ClientEvents.sessionUpdate("너는 AI 안경의 음성 비서다. 한국어로 짧게 답해라."))
            transport.send(ClientEvents.imageItem(redJpeg()))
            transport.send(
                """{"type":"conversation.item.create","item":{"type":"message","role":"user",""" +
                    """"content":[{"type":"input_text","text":"이 이미지는 무슨 색이야? 한 문장으로 답해."}]}}""",
            )
            transport.send(ClientEvents.responseCreate(listOf("audio")))
            val first = collectUntilResponseDone("1차(이미지+텍스트 → 음성)")

            assertTrue("오디오 델타 없음", first.audio.size() > 0)
            assertTrue("자막 없음", first.transcript.isNotBlank())

            // 모델이 낸 음성을 그대로 마이크 입력처럼 다시 보낸다.
            first.audio.toByteArray().asList().chunked(4800).forEach {
                transport.send(ClientEvents.audioAppend(it.toByteArray()))
            }
            transport.send(ClientEvents.commit())
            transport.send(ClientEvents.responseCreate(listOf("audio")))
            val second = collectUntilResponseDone("2차(음성 입력 → 음성)")

            assertTrue("2차 오디오 델타 없음", second.audio.size() > 0)
            // 입력 전사는 프로젝트에 전사 모델 권한이 있어야 온다. 없으면 로그에만 남긴다.
        } finally {
            transport.close()
            File("build/live-smoke.txt").apply { parentFile?.mkdirs() }.writeText(log.toString())
        }
    }

    private class Turn {
        val audio = ByteArrayOutputStream()
        var transcript = ""
        var inputTranscript = ""
    }

    private fun collectUntilResponseDone(label: String): Turn {
        val turn = Turn()
        val types = linkedMapOf<String, Int>()
        val deadline = System.currentTimeMillis() + 60_000
        var done = false
        // response.done 뒤에 늦게 오는 입력 전사를 잠깐 더 기다린다.
        var graceUntil = Long.MAX_VALUE
        while (System.currentTimeMillis() < minOf(deadline, graceUntil)) {
            val raw = messages.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val type = Json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content.orEmpty()
            types[type] = (types[type] ?: 0) + 1
            if (type == "error" || type == "session.updated" || type.endsWith(".failed")) {
                log.appendLine("[$label] RAW $type: ${raw.take(1500)}")
            }
            when (val event = ServerEvent.decode(raw)) {
                is ServerEvent.AudioDelta -> turn.audio.write(event.pcm)
                is ServerEvent.AudioTranscriptDelta -> turn.transcript += event.text
                is ServerEvent.InputTranscript -> turn.inputTranscript = event.text
                is ServerEvent.ResponseDone -> {
                    done = true
                    log.appendLine("[$label] RAW response.done: ${raw.take(600)}")
                    graceUntil = System.currentTimeMillis() + 4_000
                }
                else -> Unit
            }
            if (done && turn.inputTranscript.isNotBlank()) break
        }
        log.appendLine("[$label] 이벤트: $types")
        log.appendLine("[$label] 오디오 ${turn.audio.size()} bytes, 자막='${turn.transcript}', 입력전사='${turn.inputTranscript}'")
        assertTrue("[$label] response.done 을 받지 못함", done)
        return turn
    }

    private fun redJpeg(): ByteArray {
        val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.RED
            fillRect(0, 0, 128, 128)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }
}
