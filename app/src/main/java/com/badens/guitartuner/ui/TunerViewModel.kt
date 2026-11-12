package com.badens.guitartuner.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badens.guitartuner.audio.AudioCapture
import com.badens.guitartuner.audio.AudioSourceType
import com.badens.guitartuner.tuning.GuitarString
import com.badens.guitartuner.tuning.TunerEngine
import com.badens.guitartuner.tuning.TunerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunerUiState(
    val tuner: TunerState = TunerState(),
    val lockedString: GuitarString? = null,
    val audioSource: AudioSourceType? = null,
    val sampleRate: Int? = null,
    val micError: Boolean = false,
)

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val capture = AudioCapture(application, FRAME_SIZE, HOP_SIZE)

    /** Only touched from the processing dispatcher. Kept across sessions to remember tuned strings. */
    private var engine: TunerEngine? = null

    @Volatile
    private var lockedString: GuitarString? = null

    private val _state = MutableStateFlow(TunerUiState())
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    private var listenJob: Job? = null

    /** Starts listening. Call when the screen becomes visible; safe to call repeatedly. */
    fun start() {
        if (listenJob?.isActive == true) return
        val app = getApplication<Application>()
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        _state.update { it.copy(micError = false) }
        listenJob = viewModelScope.launch {
            capture.frames()
                .map { frame ->
                    val tuner = engineFor(frame.sampleRate).process(frame.samples, frame.timeMs, lockedString)
                    Triple(tuner, frame.source, frame.sampleRate)
                }
                .flowOn(Dispatchers.Default)
                .catch { _state.update { it.copy(micError = true) } }
                .collect { (tuner, source, rate) ->
                    _state.update { it.copy(tuner = tuner, audioSource = source, sampleRate = rate) }
                }
        }
    }

    /** Stops listening and releases the mic. Tuned strings and the lock are kept. */
    fun stop() {
        listenJob?.cancel()
        listenJob = null
        _state.update {
            it.copy(tuner = TunerState(tunedStrings = it.tuner.tunedStrings, lockedString = it.lockedString))
        }
    }

    /** Tapping a string locks detection to it; tapping the locked string again unlocks. */
    fun onStringTapped(string: GuitarString) {
        val next = if (lockedString == string) null else string
        lockedString = next
        _state.update { it.copy(lockedString = next) }
    }

    private fun engineFor(sampleRate: Int): TunerEngine {
        val current = engine
        if (current != null && current.sampleRate == sampleRate) return current
        return TunerEngine(sampleRate, FRAME_SIZE, tunedStrings = current?.tunedStrings.orEmpty())
            .also { engine = it }
    }

    private companion object {
        /** ~85 ms at 48 kHz: at least two periods of the low E string. */
        const val FRAME_SIZE = 4096

        /** 50% overlap: a new reading about every 43 ms. */
        const val HOP_SIZE = 2048
    }
}
