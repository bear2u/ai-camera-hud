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
    private val location: LocationSource? = null,
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
        set(value) {
            field = value
            textTarget = if (value) Speaker.Solution else Speaker.Translation
        }

    /** 글자로 오는 답을 채팅창의 어느 종류로 쌓을지. */
    private var textTarget = Speaker.Translation

    /** 이번 듣기에서 서버로 보낸 소리가 있으면 true. */
    private var audioSent = false

    private var typedQuestion = ""

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
                    // 길안내 중에도 보이는 것에 대해 물을 수 있다. 목적지는 글자로 따로 받는다.
                    Mode.Ask, Mode.Navigate -> {
                        solving = false
                        startListening()
                    }
                    Mode.TranslateText -> startTextTranslation()
                    Mode.Solve -> {
                        solving = true
                        startListening()
                    }
                    Mode.Conversation -> startConversation()
                }
            }
            Status.Listening -> when {
                conversing -> stopConversation()
                solving -> finishSolveListening()
                else -> finishListening()
            }
            Status.Speaking -> interrupt()
            Status.Error -> start()
            else -> Unit
        }
    }

    private fun startListening() {
        audioSent = false
        hud = nextTurn(Status.Listening)
        mic.start(::onMicChunk)
    }

    @Synchronized
    private fun onMicChunk(pcm: ByteArray) {
        if (hud.status == Status.Listening) {
            audioSent = true
            transport.send(ClientEvents.audioAppend(pcm))
        }
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

    /** 지금 턴을 기록으로 넘기고 새 턴을 시작한다. */
    private fun nextTurn(status: Status): HudState =
        HudState(status = status, history = hud.entries.takeLast(MAX_HISTORY))

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

    private fun finishSolveListening() {
        mic.stop()
        hud = hud.copy(status = Status.Thinking, userText = if (audioSent) VOICE_QUESTION else "(보이는 문제 풀기)")
        frames.capture(::onSolveFrame)
    }

    @Synchronized
    private fun onSolveFrame(jpeg: ByteArray?) {
        if (hud.status != Status.Thinking) return
        if (jpeg == null) {
            transport.send(ClientEvents.clearInputAudio())
            hud = hud.copy(status = Status.Idle, error = "카메라 장면을 가져오지 못했습니다.")
            return
        }
        transport.send(ClientEvents.imageItem(jpeg, detail = "high"))
        // 녹음된 소리가 없을 때 commit 하면 서버가 오류를 낸다.
        if (audioSent) transport.send(ClientEvents.commit())
        transport.send(ClientEvents.responseCreate(listOf("text"), SOLVE_INSTRUCTIONS))
    }

    /** 사진 한 장만 보내 번역을 글자로 받는다. */
    private fun startTextTranslation() {
        solving = false
        hud = nextTurn(Status.Thinking)
        frames.capture(::onTranslationFrame)
    }

    @Synchronized
    private fun onTranslationFrame(jpeg: ByteArray?) {
        if (hud.status != Status.Thinking) return
        if (jpeg == null) {
            hud = hud.copy(status = Status.Idle, error = "카메라 장면을 가져오지 못했습니다.")
            return
        }
        transport.send(ClientEvents.imageItem(jpeg, detail = "high"))
        transport.send(ClientEvents.responseCreate(listOf("text"), TRANSLATE_INSTRUCTIONS))
    }

    /** 채팅창에 첨부한 사진을 글과 함께 보내 분석을 글자로 받는다. */
    @Synchronized
    fun sendPhoto(photo: Photo, text: String, translate: Boolean) {
        if (hud.status != Status.Idle) return
        stopPlayback()
        val request = text.trim()
        solving = false
        textTarget = if (translate) Speaker.Translation else Speaker.Assistant
        val label = request.ifEmpty { if (translate) "번역" else "분석" }
        hud = nextTurn(Status.Thinking).copy(userText = "[사진] $label")
        transport.send(ClientEvents.imageItem(photo.jpeg, detail = "high"))
        if (request.isNotEmpty()) transport.send(ClientEvents.textItem(request))
        transport.send(
            ClientEvents.responseCreate(listOf("text"), if (translate) TRANSLATE_INSTRUCTIONS else ANALYZE_INSTRUCTIONS),
        )
    }

    /** 채팅창에 글자로 입력한 질문을 지금 보이는 장면과 함께 보낸다. */
    @Synchronized
    fun sendText(text: String) {
        if (text.isBlank() || hud.status != Status.Idle) return
        stopPlayback()
        typedQuestion = text.trim()
        solving = mode == Mode.Solve
        hud = nextTurn(Status.Thinking).copy(userText = typedQuestion)
        frames.capture(::onTypedQuestionFrame)
    }

    @Synchronized
    private fun onTypedQuestionFrame(jpeg: ByteArray?) {
        if (hud.status != Status.Thinking) return
        if (jpeg != null) transport.send(ClientEvents.imageItem(jpeg, detail = if (solving) "high" else "low"))
        if (!solving) sendLocation()
        transport.send(ClientEvents.textItem(typedQuestion))
        playbackMuted = false
        transport.send(
            if (solving) ClientEvents.responseCreate(listOf("text"), SOLVE_INSTRUCTIONS)
            else ClientEvents.responseCreate(listOf("audio")),
        )
    }

    /** 어디서 어느 쪽을 보고 있는지 알려 주면 AI가 보이는 건물과 장소를 더 정확히 맞힌다. */
    private fun sendLocation() {
        location?.current()?.let { transport.send(ClientEvents.textItem(it.describe())) }
    }

    private fun finishListening() {
        mic.stop()
        // 받아 적은 글이 오면 바뀐다. 받아쓰기 권한이 없는 계정에서는 이대로 남는다.
        hud = hud.copy(status = Status.Thinking, userText = VOICE_QUESTION)
        frames.capture(::onFrame)
    }

    @Synchronized
    private fun onFrame(jpeg: ByteArray?) {
        if (hud.status != Status.Thinking) return
        if (jpeg != null) transport.send(ClientEvents.imageItem(jpeg))
        sendLocation()
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
                    hud = when (textTarget) {
                        Speaker.Solution -> hud.copy(solutionText = hud.solutionText + received.text)
                        Speaker.Assistant -> hud.copy(assistantText = hud.assistantText + received.text)
                        else -> hud.copy(translationText = hud.translationText + received.text)
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
        const val VOICE_QUESTION = "(음성 질문)"
        const val MAX_CAPTIONS = 3
        const val MAX_HISTORY = 60
        const val MISSING_KEY = "API 키가 없습니다. 오른쪽 위 설정에서 입력하세요."
        const val SOLVE_INSTRUCTIONS =
            "방금 받은 사진에 있는 문제를 풀어라. 사용자가 방금 말이나 글로 요청한 것이 있으면 그 요청대로 답하고" +
                "(예: 특정 번호만, 힌트만, 개념 설명, 앞선 풀이에 대한 질문), 말이 없거나 알아들을 수 없으면 보이는 문제를 풀어라. " +
                "문제를 풀 때는 먼저 '답: ...'을 한 줄로 쓰고, 이어서 '풀이:' 아래에 핵심 단계를 " +
                "번호를 붙여 짧게 써라. 한국어로 쓰고, 수식은 LaTeX나 마크다운 없이 일반 문자(예: x^2, √, ×, ÷)로 써라. " +
                "문제가 여러 개면 번호별로 답하고, 문제가 보이지 않거나 글씨를 읽을 수 없으면 그렇게만 말해라."
        const val ANALYZE_INSTRUCTIONS =
            "방금 받은 사진을 분석해 한국어로 설명해라. 사용자가 글로 요청한 것이 있으면 그 요청에 맞게 답해라. " +
                "무엇이 찍혔는지, 눈에 띄는 글자나 숫자가 있으면 그 내용을 알려 주고, 마크다운 없이 짧은 문단으로 써라."
        const val TRANSLATE_INSTRUCTIONS =
            "방금 받은 사진에 보이는 글자(간판, 메뉴, 표지판 등)를 읽고 한국어로 번역해라. " +
                "'원문 → 번역' 형식으로 한 줄에 하나씩만 써라. 글자가 없으면 '보이는 글자가 없습니다'라고만 답해라."
        const val INSTRUCTIONS =
            "너는 AI 안경에 들어 있는 음성 비서다. 사용자의 질문과 함께 사용자가 지금 보고 있는 장면의 사진이 온다. " +
                "사진을 근거로 한국어로 한두 문장으로 짧고 자연스럽게 답해라. 사진과 무관한 질문에는 그냥 답해라."
    }
}
