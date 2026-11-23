package com.symverse.aiglasses.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.symverse.aiglasses.core.AudioSink
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** 24kHz 16-bit mono PCM 조각을 받아 스피커로 이어서 재생한다. */
class PcmPlayer : AudioSink {
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
        )
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                SAMPLE_RATE * 2 / 5,
            ),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val queue = LinkedBlockingQueue<Chunk>()
    private val writtenFrames = AtomicLong()

    /** stop() 때마다 올라간다. 이전 세대의 조각은 쓰지 않고 버린다. */
    @Volatile
    private var generation = 0

    private class Chunk(val generation: Int, val pcm: ByteArray)

    init {
        thread(name = "pcm-player", isDaemon = true) {
            while (true) {
                val chunk = queue.take()
                var offset = 0
                // 끊었을 때 남는 소리가 짧도록 잘게 나눠 쓴다.
                while (offset < chunk.pcm.size && chunk.generation == generation) {
                    val length = minOf(SLICE_BYTES, chunk.pcm.size - offset)
                    val written = track.write(chunk.pcm, offset, length)
                    if (written <= 0) break
                    if (chunk.generation == generation) writtenFrames.addAndGet(written / 2L)
                    offset += written
                }
            }
        }
    }

    @Synchronized
    override fun play(pcm: ByteArray) {
        queue.put(Chunk(generation, pcm))
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
    }

    @Synchronized
    override fun stop(): Int? {
        val playedFrames = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val pending = queue.isNotEmpty() || playedFrames < writtenFrames.get()
        generation++
        queue.clear()
        track.pause()
        track.flush()
        writtenFrames.set(0)
        return if (pending) (playedFrames * 1000 / SAMPLE_RATE).toInt() else null
    }

    private companion object {
        const val SAMPLE_RATE = 24000
        const val SLICE_BYTES = 2400
    }
}
