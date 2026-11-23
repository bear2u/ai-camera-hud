package com.symverse.aiglasses.core

class FakeTransport : RealtimeTransport {
    lateinit var listener: RealtimeTransport.Listener
    val sent = mutableListOf<String>()
    var connectCount = 0

    override fun connect(listener: RealtimeTransport.Listener) {
        this.listener = listener
        connectCount++
    }

    override fun send(event: String) {
        sent += event
    }

    var closeCount = 0

    override fun close() {
        closeCount++
    }
}

class FakeMic : MicSource {
    var running = false
    private var onChunk: ((ByteArray) -> Unit)? = null

    override fun start(onChunk: (ByteArray) -> Unit) {
        this.onChunk = onChunk
        running = true
    }

    override fun stop() {
        running = false
    }

    fun emit(pcm: ByteArray) = onChunk?.invoke(pcm)
}

class FakeFrames : FrameSource {
    var jpeg: ByteArray? = byteArrayOf(9, 9)

    override fun capture(onFrame: (ByteArray?) -> Unit) = onFrame(jpeg)
}

class FakeSink : AudioSink {
    val played = mutableListOf<ByteArray>()
    var stopCount = 0
    var playedMs: Int? = null

    override fun stop(): Int? {
        stopCount++
        return playedMs.also { playedMs = null }
    }

    override fun play(pcm: ByteArray) {
        played += pcm
    }
}
