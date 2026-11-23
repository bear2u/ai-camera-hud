package com.symverse.aiglasses.core

enum class Status { Off, Connecting, Idle, Listening, Thinking, Speaking, Error }

/** 화면 탭이 하는 일. */
enum class Mode { Ask, TranslateText, Conversation, Solve }

/** HUD에 그릴 내용. */
data class HudState(
    val status: Status = Status.Connecting,
    val userText: String = "",
    val assistantText: String = "",
    val translationText: String = "",
    val captions: List<String> = emptyList(),
    val solutionText: String = "",
    val error: String? = null,
)
