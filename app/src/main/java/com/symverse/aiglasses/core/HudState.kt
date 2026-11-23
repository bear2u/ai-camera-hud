package com.symverse.aiglasses.core

enum class Status { Off, Connecting, Idle, Listening, Thinking, Speaking, Error }

/** HUD에 그릴 내용. */
data class HudState(
    val status: Status = Status.Connecting,
    val userText: String = "",
    val assistantText: String = "",
    val error: String? = null,
)
