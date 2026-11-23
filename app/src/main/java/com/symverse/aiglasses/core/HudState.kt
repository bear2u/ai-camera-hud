package com.symverse.aiglasses.core

enum class Status { Off, Connecting, Idle, Listening, Thinking, Speaking, Error }

/** 화면 탭이 하는 일. */
enum class Mode { Ask, TranslateText, Conversation, Solve }

enum class Speaker { User, Assistant, Translation, Caption, Solution }

/** 채팅창의 말풍선 하나. */
data class LogEntry(val speaker: Speaker, val text: String)

/** HUD에 그릴 내용. */
data class HudState(
    val status: Status = Status.Connecting,
    val userText: String = "",
    val assistantText: String = "",
    val translationText: String = "",
    val captions: List<String> = emptyList(),
    val solutionText: String = "",
    val error: String? = null,
    /** 끝난 턴들. 지금 턴은 위의 글자 필드들에 있다. */
    val history: List<LogEntry> = emptyList(),
) {
    /** 지난 대화와 지금 턴을 시간순으로 늘어놓은 것. */
    val entries: List<LogEntry>
        get() = history + listOf(
            LogEntry(Speaker.User, userText),
            LogEntry(Speaker.Assistant, assistantText),
            LogEntry(Speaker.Translation, translationText),
            LogEntry(Speaker.Caption, captions.joinToString("\n")),
            LogEntry(Speaker.Solution, solutionText),
        ).filter { it.text.isNotBlank() }
}
