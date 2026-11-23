package com.symverse.aiglasses.core

/** 24kHz 16-bit mono PCM 조각을 내보내는 마이크. */
interface MicSource {
    fun start(onChunk: (ByteArray) -> Unit)

    fun stop()
}

/** 사용자가 지금 보고 있는 장면을 JPEG 한 장으로 준다. 실패하면 null. */
interface FrameSource {
    fun capture(onFrame: (ByteArray?) -> Unit)
}

/** 24kHz 16-bit mono PCM을 이어서 재생한다. */
interface AudioSink {
    fun play(pcm: ByteArray)

    /** 재생을 즉시 멈춘다. 아직 재생 중이었다면 그때까지 재생한 길이(ms), 아니면 null. */
    fun stop(): Int?
}
