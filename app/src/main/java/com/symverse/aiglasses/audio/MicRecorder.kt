package com.symverse.aiglasses.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.symverse.aiglasses.core.MicSource
import kotlin.concurrent.thread

/** 기기 마이크를 24kHz 16-bit mono PCM으로 읽는다. 24kHz가 안 되는 기기는 48kHz를 절반으로 줄인다. */
class MicRecorder : MicSource {
    @Volatile
    private var active: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override fun start(onChunk: (ByteArray) -> Unit) {
        stop()
        val halve = !supports(TARGET_RATE)
        val rate = if (halve) TARGET_RATE * 2 else TARGET_RATE
        val chunkBytes = rate * 2 / 10
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), chunkBytes * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        active = record
        record.startRecording()
        thread(name = "mic", isDaemon = true) {
            val buffer = ByteArray(chunkBytes)
            try {
                while (active === record) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    onChunk(if (halve) halveRate(buffer, read) else buffer.copyOf(read))
                }
            } finally {
                record.release()
            }
        }
    }

    override fun stop() {
        val record = active ?: return
        active = null
        runCatching { record.stop() }
    }

    private fun supports(rate: Int): Boolean =
        AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0

    /** 16-bit little-endian 샘플을 두 개씩 평균해 샘플레이트를 절반으로 만든다. */
    private fun halveRate(pcm: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length / 4 * 2)
        for (i in 0 until out.size / 2) {
            val a = sampleAt(pcm, i * 4)
            val b = sampleAt(pcm, i * 4 + 2)
            val mean = (a + b) / 2
            out[i * 2] = mean.toByte()
            out[i * 2 + 1] = (mean shr 8).toByte()
        }
        return out
    }

    private fun sampleAt(pcm: ByteArray, index: Int): Int =
        (pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)

    private companion object {
        const val TARGET_RATE = 24000
    }
}
