package com.spirit.scan.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.spirit.scan.SpiritApp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural spirit-box layer:
 * - continuous soft static
 * - intensity follows anomaly
 * - short gated bursts when activity is high
 */
class SpiritBoxAudio {

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var intensity = 0.15f
    @Volatile private var gateOpen = false
    @Volatile private var burstFrames = 0

    private val sampleRate = 22050
    private val random = Random(System.currentTimeMillis())

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = minBuf.coerceAtLeast(sampleRate / 4)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
                AudioTrack.MODE_STREAM
            )
        }

        running = true
        track?.play()
        thread = Thread({ loop(bufSize) }, "SpiritBoxAudio").also { it.start() }
        Log.i(SpiritApp.TAG, "SpiritBoxAudio started")
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (_: Exception) {
        }
        thread = null
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
        Log.i(SpiritApp.TAG, "SpiritBoxAudio stopped")
    }

    /** 0 = quiet static, 1 = full activity */
    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
    }

    /** Open the gate briefly (spirit-box style break) */
    fun pulseBurst() {
        gateOpen = true
        burstFrames = (sampleRate * 0.35f).toInt()
    }

    private fun loop(chunkBytes: Int) {
        val samples = chunkBytes / 2
        val buf = ShortArray(samples)
        var phase = 0.0

        while (running) {
            val level = intensity
            val baseAmp = (800 + 9000 * level).toInt()

            for (i in 0 until samples) {
                var n = (random.nextFloat() * 2f - 1f)

                // soft band emphasis so it is not pure white hash
                phase += 0.05 + level * 0.12
                val tone = sin(phase).toFloat() * 0.25f
                n = (n * 0.85f + tone * 0.15f)

                var amp = baseAmp
                if (gateOpen && burstFrames > 0) {
                    // gated burst: louder, more "broken" noise
                    amp = (18000 + random.nextInt(8000))
                    n = (random.nextFloat() * 2f - 1f)
                    if (random.nextFloat() < 0.04f) {
                        // sparse click/phoneme-like spike
                        n = if (random.nextBoolean()) 1f else -1f
                    }
                    burstFrames--
                    if (burstFrames <= 0) gateOpen = false
                }

                val sample = (n * amp).toInt().coerceIn(-32767, 32767)
                buf[i] = sample.toShort()
            }

            try {
                track?.write(buf, 0, samples)
            } catch (e: Exception) {
                Log.w(SpiritApp.TAG, "Audio write failed", e)
                break
            }
        }
    }
}
