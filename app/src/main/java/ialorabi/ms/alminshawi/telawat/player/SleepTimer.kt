package ialorabi.ms.alminshawi.telawat.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// Lives at process level so the timer keeps running after the activity is gone
object SleepTimer {
    private const val FADE_OUT_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _selectedMinutes = MutableStateFlow<Int?>(null)
    val selectedMinutes: StateFlow<Int?> = _selectedMinutes.asStateFlow()

    // Stop when the current surah finishes instead of after a fixed time
    private val _endOfSurah = MutableStateFlow(false)
    val endOfSurah: StateFlow<Boolean> = _endOfSurah.asStateFlow()

    // Player volume, lowered during the last 30 seconds
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // PlaybackService pauses the player when this fires, then calls restoreVolume()
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun set(minutes: Int?) {
        job?.cancel()
        _endOfSurah.value = false
        _volume.value = 1f
        if (minutes == null || minutes <= 0) {
            _remainingMs.value = 0L
            _selectedMinutes.value = null
            return
        }
        _selectedMinutes.value = minutes
        val totalMs = minutes * 60 * 1000L
        _remainingMs.value = totalMs
        job = scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500L.milliseconds)
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val remaining = (totalMs - elapsed).coerceAtLeast(0L)
                _remainingMs.value = remaining
                _volume.value = (remaining.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                if (remaining <= 0L) {
                    _selectedMinutes.value = null
                    _expired.tryEmit(Unit)
                    break
                }
            }
        }
    }

    fun setEndOfSurah() {
        set(null)
        _endOfSurah.value = true
    }

    // Returns true once when the surah ends with "end of surah" selected
    fun consumeEndOfSurah(): Boolean {
        if (!_endOfSurah.value) return false
        _endOfSurah.value = false
        return true
    }

    fun restoreVolume() {
        _volume.value = 1f
    }
}
