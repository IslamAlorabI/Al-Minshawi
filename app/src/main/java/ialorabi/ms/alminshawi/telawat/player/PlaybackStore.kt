package ialorabi.ms.alminshawi.telawat.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// State that PlaybackService publishes and the UI observes
object PlaybackStore {
    enum class Event { DOWNLOAD_FAILED, QUEUE_LIMIT_REACHED, PLAYBACK_ERROR }

    // surahId -> progress in 0..1 (0 while waiting in the queue)
    private val _downloads = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloads: StateFlow<Map<Int, Float>> = _downloads.asStateFlow()

    // The surah being downloaded so it can play right after
    private val _playDownloadSurahId = MutableStateFlow<Int?>(null)
    val playDownloadSurahId: StateFlow<Int?> = _playDownloadSurahId.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    // Position a surah resumed from, so the UI can offer "start over"
    private val _resumedAt = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val resumedAt: SharedFlow<Long> = _resumedAt.asSharedFlow()

    @Volatile
    var isUserSeeking = false

    internal fun setDownloadProgress(surahId: Int, progress: Float) {
        _downloads.update { it + (surahId to progress) }
    }

    internal fun removeDownload(surahId: Int) {
        _downloads.update { it - surahId }
    }

    internal fun setPlayDownload(surahId: Int?) {
        _playDownloadSurahId.value = surahId
    }

    internal fun emitResumed(positionMs: Long) {
        _resumedAt.tryEmit(positionMs)
    }

    internal fun emit(event: Event) {
        _events.tryEmit(event)
    }

    // Service is going away, so nothing is downloading any more
    internal fun reset() {
        _downloads.value = emptyMap()
        _playDownloadSurahId.value = null
    }
}
