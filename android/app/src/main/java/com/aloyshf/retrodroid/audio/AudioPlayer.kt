package com.aloyshf.retrodroid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.aloyshf.retrodroid.core.NativeCore

/**
 * Streams the frontend's audio ring buffer into an [AudioTrack] on its own
 * thread. Samples are produced by the emulation thread, so the track pulls at a
 * steady rate and writes silence when the ring runs dry (pause, load).
 */
class AudioPlayer {
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    fun start(sampleRate: Int) {
        stop()
        if (sampleRate <= 0) return
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return

        val bufferSize = maxOf(minBuffer, sampleRate / 4 * 4)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        audio.play()
        track = audio
        running = true
        thread = Thread({ pump(audio) }, "retro-audio").apply { isDaemon = true; start() }
    }

    private fun pump(audio: AudioTrack) {
        val scratch = ShortArray(2048)
        while (running) {
            val frames = NativeCore.nativeReadAudio(scratch, scratch.size / 2)
            if (!running) break
            if (frames > 0) {
                runCatching { audio.write(scratch, 0, frames * 2) }
            } else {
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { thread?.join(300) }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        thread = null
        track = null
    }
}
