package com.playlists.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight monophonic-per-note piano synthesis via [AudioTrack].
 */
object PianoVolume {
    const val DEFAULT_GAIN = 0.35f
    const val MIN_GAIN = 0.1f
    const val MAX_GAIN = 1f
    const val STEP = 0.05f

    fun clamp(gain: Float): Float = gain.coerceIn(MIN_GAIN, MAX_GAIN)

    fun stepDown(gain: Float): Float = clamp(gain - STEP)

    fun stepUp(gain: Float): Float = clamp(gain + STEP)
}

class PianoSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeNotes = ConcurrentHashMap<Int, NoteSession>()
    private val gain = AtomicReference(PianoVolume.DEFAULT_GAIN)

    fun setGain(value: Float) {
        gain.set(PianoVolume.clamp(value))
    }

    fun noteOn(midi: Int) {
        if (midi !in PianoLayout.MIN_MIDI..PianoLayout.MAX_MIDI) return
        activeNotes[midi]?.cancel()
        val session = NoteSession()
        activeNotes[midi] = session
        session.job = scope.launch {
            try {
                playNote(midi, session)
            } finally {
                activeNotes.remove(midi, session)
            }
        }
    }

    fun noteOff(midi: Int) {
        activeNotes[midi]?.requestRelease()
    }

    fun release() {
        scope.cancel()
        activeNotes.values.forEach { it.cancel() }
        activeNotes.clear()
    }

    private class NoteSession {
        val releaseRequested = AtomicBoolean(false)
        @Volatile var job: Job? = null

        fun requestRelease() {
            releaseRequested.set(true)
        }

        fun cancel() {
            job?.cancel()
        }
    }

    private suspend fun playNote(midi: Int, session: NoteSession) {
        withContext(Dispatchers.IO) {
            val sampleRate = 44_100
            val frequency = PianoLayout.midiToHz(midi)
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val chunkSamples = minBufferSize.coerceAtLeast(512)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(chunkSamples * Short.SIZE_BYTES * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                var t = 0f
                var releaseGain = 1f
                val maxDurationSec = 3f
                val naturalDecaySec = 2.5f
                val releaseDecayPerSample = 6.5f / sampleRate

                while (t < maxDurationSec) {
                    val noteGain = gain.get()
                    val buf = ShortArray(chunkSamples)
                    var wrote = 0
                    for (i in buf.indices) {
                        if (session.releaseRequested.get()) {
                            releaseGain *= exp(-releaseDecayPerSample)
                        }
                        val sampleEnvelope = exp(-3.2f * t / naturalDecaySec) * releaseGain
                        if (sampleEnvelope < 0.001f && session.releaseRequested.get()) {
                            wrote = i
                            break
                        }
                        var sample = 0.0
                        sample += sin(2.0 * PI * frequency * t) * 0.55
                        sample += sin(2.0 * PI * frequency * 2.0 * t) * 0.25
                        sample += sin(2.0 * PI * frequency * 3.0 * t) * 0.12
                        sample += sin(2.0 * PI * frequency * 4.0 * t) * 0.06
                        buf[i] = (sample * sampleEnvelope * Short.MAX_VALUE * noteGain).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                        t += 1f / sampleRate
                        wrote = i + 1
                    }
                    if (wrote == 0) break
                    track.write(buf, 0, wrote)
                    if (wrote < buf.size) break
                }
            } finally {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                }
                track.release()
            }
        }
    }
}

@Composable
fun rememberPianoSoundEngine(): PianoSoundEngine {
    val engine = remember { PianoSoundEngine() }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return engine
}
