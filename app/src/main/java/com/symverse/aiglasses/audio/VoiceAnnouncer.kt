package com.symverse.aiglasses.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 길안내 문장을 기기의 음성 합성으로 읽어 준다. */
class VoiceAnnouncer(context: Context) {
    @Volatile
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }

    fun speak(text: String) {
        if (!ready) return
        tts.language = Locale.KOREAN
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "nav-${System.nanoTime()}")
    }

    fun shutdown() {
        tts.shutdown()
    }
}
