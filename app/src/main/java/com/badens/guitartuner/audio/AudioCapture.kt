package com.badens.guitartuner.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

/** One analysis window of mono samples in -1..1. */
class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
    /** Capture time of the last sample, counted from the start of the session. */
    val timeMs: Long,
    val source: AudioSourceType,
)

enum class AudioSourceType(val label: String, val androidSource: Int) {
    /** Raw mic signal: no noise suppression, echo cancellation, or auto-gain. Best for pitch. */
    UNPROCESSED("UNPROCESSED", MediaRecorder.AudioSource.UNPROCESSED),

    /** Tuned for speech recognition: also skips most processing. The usual fallback. */
    VOICE_RECOGNITION("VOICE_RECOGNITION", MediaRecorder.AudioSource.VOICE_RECOGNITION),

    /** Last resort; may apply processing that distorts pitch. */
    MIC("MIC", MediaRecorder.AudioSource.MIC),
}

/**
 * Reads the microphone with [AudioRecord] and emits overlapping frames: [frameSize] samples,
 * one every [hopSize] new samples.
 */
class AudioCapture(
    private val context: Context,
    private val frameSize: Int = 4096,
    private val hopSize: Int = 2048,
) {
    /**
     * Cold flow of frames. Collecting it opens the mic; cancelling the collector releases it.
     * Throws [IOException] if no audio source can be opened.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(): Flow<AudioFrame> = flow {
        val (record, source) = open() ?: throw IOException("Couldn't open any microphone source")
        try {
            record.startRecording()
            val rate = record.sampleRate
            val window = FloatArray(frameSize)
            val hop = FloatArray(hopSize)
            var filled = 0
            var samplesRead = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                var got = 0
                while (got < hopSize) {
                    val n = record.read(hop, got, hopSize - got, AudioRecord.READ_BLOCKING)
                    if (n < 0) throw IOException("AudioRecord.read failed with code $n")
                    got += n
                }
                samplesRead += hopSize
                System.arraycopy(window, hopSize, window, 0, frameSize - hopSize)
                System.arraycopy(hop, 0, window, frameSize - hopSize, hopSize)
                filled = minOf(frameSize, filled + hopSize)
                if (filled == frameSize) {
                    emit(AudioFrame(window.copyOf(), rate, samplesRead * 1000 / rate, source))
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun open(): Pair<AudioRecord, AudioSourceType>? {
        val sources = buildList {
            if (supportsUnprocessed()) add(AudioSourceType.UNPROCESSED)
            add(AudioSourceType.VOICE_RECOGNITION)
            add(AudioSourceType.MIC)
        }
        for (source in sources) {
            for (rate in SAMPLE_RATES) {
                tryOpen(source, rate)?.let { return it to source }
            }
        }
        return null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun tryOpen(source: AudioSourceType, sampleRate: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return null
        val bufferBytes = maxOf(minBuffer, hopSize * Float.SIZE_BYTES * 4)
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(source.androidSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (_: UnsupportedOperationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    private fun supportsUnprocessed(): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }

    private companion object {
        val SAMPLE_RATES = intArrayOf(48_000, 44_100)
    }
}
