package com.symverse.aiglasses.core

import com.symverse.aiglasses.realtime.ClientEvents
import com.symverse.aiglasses.realtime.ServerEvent

/** 탭과 서버 이벤트를 받아 AI 안경의 대화 흐름을 진행한다. */
class GlassesSession(
    private val apiKey: String,
    private val transport: RealtimeTransport,
    private val mic: MicSource,
    private val frames: FrameSource,
    private val sink: AudioSink,
    private val onHudChanged: (HudState) -> Unit = {},
) : RealtimeTransport.Listener {
    var hud = HudState()
        private set(value) {
            field = value
            onHudChanged(value)
        }

    /** 화면 탭이 하는 일. 다음 탭부터 적용된다. */
    @Volatile
    var mode = Mode.Ask

    /** 대화 번역 중이면 true. 서버가 턴을 나누고 번역을 텍스트로 준다. */
    private var conversing = false
    private var captionOpen = false

    /** 사진으로 요청한 일이 문제 풀이면 true, 글자 번역이면 false. */
    private var solving = false

    private var playingItemId: String? = null

    /** 끊은 응답의 늦게 도착한 음성을 버린다. */
    private var playbackMuted = false

    @Synchronized
    fun start() {
        if (apiKey.isBlank()) {
            hud = HudState(status = Status.Error, error = MISSING_KEY)
            return
        }
        transport.close()
        hud = HudState(status = Status.Connecting)
        transport.connect(this)
    }

    /** AI를 끈다. 다시 켜려면 [start]. */
    @Synchronized
    fun stop() {
        conversing = false
        mic.stop()
        sink.stop()
        transport.close()
        playingItemId = null
        hud = HudState(status = Status.Off)
    }

    @Synchronized
    fun tap() {
        when (hud.status) {
            Status.Idle -> if (!stopPlayback()) {
                when (mode) {
                    Mode.Ask -> startListening()
                    Mode.TranslateText -> startPhotoTask(solve = false)
                    Mode.Solve -> startPhotoTask(solve = true)
                    Mode.Conversation -> startConversation()
                }
            }
            Status.Listening -> if (conversing) stopConversation() else finishListening()
            Status.Speaking -> interrupt()
            Status.Error -> start()
            else -> Unit
        }
    }

    private fun startListening() {
        hud = HudState(status = Status.Listening)
        mic.start(::onMicChunk)
    }

    @Synchronized
    private fun onMicChunk(pcm: ByteArray) {
        if (hud.status == Status.Listening) transport.send(ClientEvents.audioAppend(pcm))
    }

    private fun interrupt() {
        transport.send(ClientEvents.cancel())
        playbackMuted = true
        stopPlayback()
        hud = hud.copy(status = Status.Idle)
    }

    /** 재생 중이던 음성을 끊고, 서버 대화 기록에도 들은 데까지만 남긴다. 끊은 것이 있으면 true. */
    private fun stopPlayback(): Boolean {
        val playedMs = sink.stop() ?: return false
        playingItemId?.let { transport.send(ClientEvents.truncate(it, playedMs)) }
        playingItemId = null
        return true
    }

    private fun startConversation() {
        transport.send(ClientEvents.conversationTranslationUpdate())
        conversing = true
        captionOpen = false
        startListening()
    }

    private fun stopConversation() {
        mic.stop()
        conversing = false
        transport.send(ClientEvents.clearInputAudio())
        transport.send(ClientEvents.sessionUpdate(INSTRUCTIONS))
        hud = hud.copy(status = Status.Idle)
    }

    /** 번역된 말 한 턴을 자막 한 줄로 쌓는다. */
    private fun appendCaption(text: String) {
        val lines = hud.captions
        val updated = if (captionOpen) lines.dropLast(1) + (lines.last() + text) else lines + text
        hud = hud.copy(captions = updated.takeLast(MAX_CAPTIONS))
        captionOpen = true
    }

    /** 사진 한 장만 보내 글자로 답을 받는다: 글자 번역 또는 문제 풀이. */
    private fun startPhotoTask(solve: Boolean) {
        solving = solve
        hud = HudState(status = Status.Thinking)
        frames.capture(::onPhotoTaskFrame)
    }

    @Synchronized
    private fun onPhotoTaskFrame(jpeg: ByteArray?) {
        if (hud.status != Status.Thinking) return
        if (jpeg == null) {
            hud = hud.copy(status = Status.Idle, error = "카메라 장면을 가져오지 못했습니다.")
            return
        }
        transport.send(ClientEvents.imageItem(jpeg, detail = "high"))
        val instructions = if (solving) SOLVE_INSTRUCTIONS else TRANSLATE_INSTRUCTIONS
        transport.send(ClientEvents.responseCreate(listOf("text"), instructions))
    }

    private fun finishListening() {
        mic.stop()
        hud = hud.copy(status = Status.Thinking)
        frames.capture(::onFrame)
    }

    @Synchronized
    private fun onFrame(jpeg: ByteArray?) {
        if (hud.status != Status.Thinking) return
        if (jpeg != null) transport.send(ClientEvents.imageItem(jpeg))
        playbackMuted = false
        transport.send(ClientEvents.commit())
        transport.send(ClientEvents.responseCreate(listOf("audio")))
    }

    @Synchronized
    override fun onOpen() {
        if (hud.status == Status.Off) return
        transport.send(ClientEvents.sessionUpdate(INSTRUCTIONS))
        hud = hud.copy(status = Status.Idle)
    }

    @Synchronized
    override fun onMessage(event: String) {
        if (hud.status == Status.Off) return
        when (val received = ServerEvent.decode(event)) {
            is ServerEvent.AudioTranscriptDelta ->
                hud = hud.copy(assistantText = hud.assistantText + received.text)
            is ServerEvent.TextDelta ->
                if (conversing) {
                    appendCaption(received.text)
                } else if (hud.status == Status.Thinking) {
                    hud = if (solving) {
                        hud.copy(solutionText = hud.solutionText + received.text)
                    } else {
                        hud.copy(translationText = hud.translationText + received.text)
                    }
                }
            is ServerEvent.InputTranscript -> hud = hud.copy(userText = received.text)
            is ServerEvent.ResponseDone -> if (conversing) captionOpen = false else hud = hud.copy(status = Status.Idle)
            // 질문 중이 아닐 때 오는 오류(잘못된 키 등)는 연결 자체의 문제다.
            is ServerEvent.Error -> {
                mic.stop()
                conversing = false
                hud = hud.copy(
                    status = if (hud.status == Status.Idle) Status.Error else Status.Idle,
                    error = received.message,
                )
            }
            is ServerEvent.AudioDelta -> if (!playbackMuted) {
                playingItemId = received.itemId
                sink.play(received.pcm)
                hud = hud.copy(status = Status.Speaking)
            }
            else -> Unit
        }
    }

    @Synchronized
    override fun onFailure(message: String) {
        if (hud.status == Status.Off) return
        conversing = false
        mic.stop()
        sink.stop()
        hud = hud.copy(status = Status.Error, error = message)
    }

    private companion object {
        const val MAX_CAPTIONS = 3
        const val MISSING_KEY = "API 키가 없습니다. 오른쪽 위 설정에서 입력하세요."
        const val SOLVE_INSTRUCTIONS =
            "방금 받은 사진에 있는 문제를 풀어라. 먼저 '답: ...'을 한 줄로 쓰고, 이어서 '풀이:' 아래에 핵심 단계를 " +
                "번호를 붙여 짧게 써라. 한국어로 쓰고, 수식은 LaTeX나 마크다운 없이 일반 문자(예: x^2, √, ×, ÷)로 써라. " +
                "문제가 여러 개면 번호별로 답하고, 문제가 보이지 않거나 글씨를 읽을 수 없으면 그렇게만 말해라."
        const val TRANSLATE_INSTRUCTIONS =
            "방금 받은 사진에 보이는 글자(간판, 메뉴, 표지판 등)를 읽고 한국어로 번역해라. " +
                "'원문 → 번역' 형식으로 한 줄에 하나씩만 써라. 글자가 없으면 '보이는 글자가 없습니다'라고만 답해라."
        const val INSTRUCTIONS =
            "너는 AI 안경에 들어 있는 음성 비서다. 사용자의 질문과 함께 사용자가 지금 보고 있는 장면의 사진이 온다. " +
                "사진을 근거로 한국어로 한두 문장으로 짧고 자연스럽게 답해라. 사진과 무관한 질문에는 그냥 답해라."
    }
}
