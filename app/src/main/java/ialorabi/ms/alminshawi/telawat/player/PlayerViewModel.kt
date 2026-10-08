package ialorabi.ms.alminshawi.telawat.player

import android.content.ComponentName
import android.content.Context
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import androidx.core.content.edit
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration.Companion.milliseconds

@androidx.media3.common.util.UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    private fun getLocalizedTitle(surah: Surah): String {
        val context = getApplication<Application>()
        val localizedNames = context.resources.getStringArray(R.array.surah_names)
        val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
        val prefix = context.getString(R.string.surah_prefix)
        return "$prefix $name (${surah.id})"
    }

    private fun getLocalizedArtist(): String {
        val context = getApplication<Application>()
        return context.getString(R.string.sheikh_name)
    }

    private fun getArtworkUri(): android.net.Uri? {
        return ArtworkHelper.getArtworkUri(getApplication())
    }


    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    var player: Player? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPlayingSurahId = MutableStateFlow(
        prefs.getInt("last_surah_id", -1).takeIf { it != -1 }
    )
    val currentPlayingSurahId: StateFlow<Int?> = _currentPlayingSurahId.asStateFlow()

    private val _currentPosition = MutableStateFlow(prefs.getLong("last_pos", 0L))
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(prefs.getLong("last_duration", 0L))
    val duration: StateFlow<Long> = _duration.asStateFlow()

    val downloadingProgress: StateFlow<Map<Int, Float>> = PlaybackStore.downloads

    val downloadingSurahs: StateFlow<Set<Int>> = PlaybackStore.downloads
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val cachedSurahIds: StateFlow<Set<Int>> = AudioCache.downloadedIds

    private var progressJob: Job? = null

    private val _repeatMode = MutableStateFlow(prefs.getBoolean("repeat_mode", false))
    val repeatMode: StateFlow<Boolean> = _repeatMode.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(prefs.getBoolean("auto_play_next", false))
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _autoPlayReversed = MutableStateFlow(prefs.getBoolean("auto_play_reversed", false))
    val autoPlayReversed: StateFlow<Boolean> = _autoPlayReversed.asStateFlow()

    val sleepTimerRemainingMs: StateFlow<Long> = SleepTimer.remainingMs

    val sleepTimerSelectedMinutes: StateFlow<Int?> = SleepTimer.selectedMinutes

    val sleepTimerEndOfSurah: StateFlow<Boolean> = SleepTimer.endOfSurah


    private val _favoriteSurahIds = MutableStateFlow<Set<Int>>(emptySet())
    val favoriteSurahIds: StateFlow<Set<Int>> = _favoriteSurahIds.asStateFlow()

    private var bufferingJob: Job? = null
    private var isTransitioning = false
    private var isSeeking = false
    private var isSkipping = false
    val pendingDownloadSurahId: StateFlow<Int?> = PlaybackStore.playDownloadSurahId

    private val _downloadLimitReached = MutableSharedFlow<Unit>()
    val downloadLimitReached: SharedFlow<Unit> = _downloadLimitReached.asSharedFlow()

    private val _downloadQueueLimitReached = MutableSharedFlow<Unit>()
    val downloadQueueLimitReached: SharedFlow<Unit> = _downloadQueueLimitReached.asSharedFlow()

    private val _downloadFailed = MutableSharedFlow<Unit>()
    val downloadFailed: SharedFlow<Unit> = _downloadFailed.asSharedFlow()

    private val _playbackError = MutableSharedFlow<Unit>()
    val playbackError: SharedFlow<Unit> = _playbackError.asSharedFlow()

    val resumedAt: SharedFlow<Long> = PlaybackStore.resumedAt


    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "repeat_mode" -> _repeatMode.value = prefs.getBoolean("repeat_mode", false)
            "auto_play_next" -> _autoPlayNext.value = prefs.getBoolean("auto_play_next", false)
            "auto_play_reversed" -> _autoPlayReversed.value = prefs.getBoolean("auto_play_reversed", false)
        }
    }

    init {
        loadFavorites()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        viewModelScope.launch {
            PlaybackStore.events.collect { event ->
                when (event) {
                    PlaybackStore.Event.DOWNLOAD_FAILED -> {
                        isTransitioning = false
                        isSkipping = false
                        _downloadFailed.emit(Unit)
                    }
                    PlaybackStore.Event.QUEUE_LIMIT_REACHED -> _downloadQueueLimitReached.emit(Unit)
                    PlaybackStore.Event.PLAYBACK_ERROR -> _playbackError.emit(Unit)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
    
    private fun sendCommand(action: String, surahId: Int? = null, juz: Int? = null) {
        val args = Bundle().apply {
            surahId?.let { putInt(PlaybackService.EXTRA_SURAH_ID, it) }
            juz?.let { putInt(PlaybackService.EXTRA_JUZ, it) }
        }
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    // Re-applies localized titles to the notification after a language change
    fun refreshMetadata() {
        sendCommand(PlaybackService.ACTION_REFRESH_METADATA)
    }

    private fun loadFavorites() {
        val ids = prefs.getStringSet("favorite_surahs", emptySet()) ?: emptySet()
        _favoriteSurahIds.value = ids.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun toggleFavorite(surahId: Int) {
        val current = _favoriteSurahIds.value.toMutableSet()
        if (current.contains(surahId)) current.remove(surahId) else current.add(surahId)
        _favoriteSurahIds.value = current
        prefs.edit { putStringSet("favorite_surahs", current.map { it.toString() }.toSet()) }
    }

    fun downloadSurah(surah: Surah) {
        if (surah.id in downloadingSurahs.value) return
        sendCommand(PlaybackService.ACTION_DOWNLOAD_SURAH, surah.id)
    }

    fun surahsMissingInJuz(juz: Int): List<Surah> =
        SurahRepository.surahs.filter { juz in SurahRepository.juzRange(it) && it.id !in cachedSurahIds.value }

    suspend fun downloadSizeBytes(surahs: List<Surah>): Long? = RemoteSizes.totalBytes(surahs)

    fun freeSpaceBytes(): Long = AudioCache.freeSpaceBytes()

    fun downloadJuz(juz: Int) {
        sendCommand(PlaybackService.ACTION_DOWNLOAD_JUZ, juz = juz)
    }

    fun cancelDownload(surahId: Int) {
        if (surahId == pendingDownloadSurahId.value) {
            isTransitioning = false
            isSkipping = false
        }
        sendCommand(PlaybackService.ACTION_CANCEL_DOWNLOAD, surahId)
    }

    fun cancelAllDownloads() {
        isTransitioning = false
        isSkipping = false
        sendCommand(PlaybackService.ACTION_CANCEL_ALL_DOWNLOADS)
    }

    fun initializeController(context: Context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener(
            {
                controller = mediaControllerFuture?.get()
                player = controller
                setupPlayerListeners()
                restoreLastState()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun restoreLastState() {
        val exo = player ?: return
        if (exo.mediaItemCount == 0) {
            val lastSurahId = prefs.getInt("last_surah_id", -1)
            val lastPos = prefs.getLong("last_pos", 0L)
            val lastDuration = prefs.getLong("last_duration", 0L)
            
            if (lastSurahId != -1) {
                val surahs = SurahRepository.surahs
                val surah = surahs.find { it.id == lastSurahId }
                if (surah != null) {
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(surah.id.toString())
                        .setUri(surah.url)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(getLocalizedTitle(surah))
                                .setArtist(getLocalizedArtist())
                                .apply { getArtworkUri()?.let { setArtworkUri(it) } }
                                .build()
                        )
                        .build()
                    
                    if (exo.mediaItemCount > 0) {
                        exo.replaceMediaItem(exo.currentMediaItemIndex, mediaItem)
                        exo.seekTo(lastPos)
                    } else {
                        exo.setMediaItem(mediaItem, lastPos)
                    }
                    exo.playWhenReady = false
                    exo.prepare()
                    
                    _currentPlayingSurahId.value = lastSurahId
                    _currentPosition.value = lastPos
                    _duration.value = lastDuration
                }
            }
        } else {
            syncPlayerState()
        }
    }

    fun syncPlayerState() {
        val exo = player ?: return
        _currentPlayingSurahId.value = exo.currentMediaItem?.mediaId?.toIntOrNull()
        _currentPosition.value = exo.currentPosition
        _duration.value = exo.duration.coerceAtLeast(0L)
        _isPlaying.value = exo.isPlaying
        _isBuffering.value = exo.playbackState == Player.STATE_BUFFERING
        if (exo.isPlaying) {
            startTrackingProgress()
        } else {
            stopTrackingProgress()
        }
    }

    private fun setupPlayerListeners() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startTrackingProgress()
                } else {
                    stopTrackingProgress()
                    saveCurrentState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                bufferingJob?.cancel()
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingJob = viewModelScope.launch {
                        delay(500L.milliseconds)
                        _isBuffering.value = true
                    }
                } else {
                    _isBuffering.value = false
                }
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    isTransitioning = false
                    isSkipping = false
                }
                if (playbackState == Player.STATE_ENDED && !isTransitioning && !isSeeking) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    _currentPosition.value = _duration.value
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val surahId = mediaItem?.mediaId?.toIntOrNull()
                if (_currentPlayingSurahId.value != surahId) {
                    _currentPosition.value = 0L
                }
                _currentPlayingSurahId.value = surahId
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    if (!isSeeking) {
                        _currentPosition.value = player?.currentPosition ?: 0L
                    }
                    saveCurrentState()
                }
            }
        })
    }

    private var isUiVisible = true

    fun setUiVisible(visible: Boolean) {
        isUiVisible = visible
        if (visible && player?.isPlaying == true) startTrackingProgress()
    }

    private fun startTrackingProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var lastSaveAt = SystemClock.elapsedRealtime()
            while (isActive) {
                if (isUiVisible && !isSeeking) {
                    _currentPosition.value = player?.currentPosition ?: 0L
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastSaveAt >= 2000L) {
                    saveCurrentState()
                    lastSaveAt = now
                }
                // While hidden, only wake up to save the resume position
                delay(if (isUiVisible) 100L.milliseconds else 2000L.milliseconds)
            }
        }
    }

    private fun saveCurrentState() {
        val surahId = _currentPlayingSurahId.value
        val isEnded = player?.playbackState == Player.STATE_ENDED
        val pos = if (isEnded) 0L else (player?.currentPosition ?: 0L)
        val dur = player?.duration?.coerceAtLeast(0L) ?: 0L
        if (surahId != null) {
            prefs.edit {
                putInt("last_surah_id", surahId)
                putLong("last_pos", pos)
                putLong("last_duration", dur)
            }
            if (!isEnded) ResumePositions.save(prefs, surahId, pos, dur)
        }
    }

    private fun stopTrackingProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    // The service starts cached surahs right away (resuming where the listener stopped)
    // and downloads the rest first when autoPlay is set
    fun playSurah(surah: Surah, autoPlay: Boolean = false) {
        val canPlay = surah.id in cachedSurahIds.value || (autoPlay && surah.id !in downloadingSurahs.value)
        if (canPlay && controller != null) {
            sendCommand(PlaybackService.ACTION_PLAY_SURAH, surah.id)
        } else {
            isTransitioning = false
            isSkipping = false
        }
    }

    fun restartCurrentSurah() {
        player?.seekTo(0)
        _currentPosition.value = 0L
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                if (it.playbackState == Player.STATE_ENDED) {
                    it.seekTo(0)
                    _currentPosition.value = 0L
                }
                it.play()
            }
        }
    }

    private var wasPlayingBeforeSeek = false

    fun beginSeek() {
        isSeeking = true
        PlaybackStore.isUserSeeking = true
        wasPlayingBeforeSeek = player?.isPlaying == true
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        isSeeking = true
        _currentPosition.value = positionMs
    }

    fun finishSeek() {
        player?.seekTo(_currentPosition.value)
        if (wasPlayingBeforeSeek) {
            player?.play()
        }
        viewModelScope.launch {
            delay(300.milliseconds)
            isSeeking = false
            PlaybackStore.isUserSeeking = false
        }
    }

    fun seekForward() {
        player?.let {
            val nextPosition = (it.currentPosition + 30_000).coerceAtMost(it.duration)
            it.seekTo(nextPosition)
            _currentPosition.value = nextPosition
        }
    }

    fun seekBackward() {
        player?.let {
            val previousPosition = (it.currentPosition - 10_000).coerceAtLeast(0L)
            it.seekTo(previousPosition)
            _currentPosition.value = previousPosition
        }
    }

    fun playNextSurah(autoPlay: Boolean = false) {
        if (isSkipping) return
        isSkipping = true
        val currentId = _currentPlayingSurahId.value ?: run { isSkipping = false; return }
        val surahs = SurahRepository.surahs
        if (currentId < surahs.size) {
            playSurah(surahs[currentId], autoPlay)
        } else {
            isTransitioning = false
            isSkipping = false
        }
    }

    fun playPreviousSurah(autoPlay: Boolean = false) {
        if (isSkipping) return
        isSkipping = true
        val currentId = _currentPlayingSurahId.value ?: run { isSkipping = false; return }
        val surahs = SurahRepository.surahs
        if (currentId > 1) {
            playSurah(surahs[currentId - 2], autoPlay)
        } else {
            isTransitioning = false
            isSkipping = false
        }
    }

    fun toggleRepeat() {
        val newState = !_repeatMode.value
        prefs.edit {
            putBoolean("repeat_mode", newState)
            if (newState) {
                putBoolean("auto_play_next", false)
                putBoolean("auto_play_reversed", false)
            }
        }
    }

    // Same order as the notification button: off -> next -> reverse -> off
    fun cycleAutoPlay() {
        val autoNextOn = _autoPlayNext.value
        val autoReversed = _autoPlayReversed.value
        prefs.edit {
            when {
                !autoNextOn -> {
                    putBoolean("auto_play_next", true)
                    putBoolean("auto_play_reversed", false)
                    putBoolean("repeat_mode", false)
                }
                !autoReversed -> putBoolean("auto_play_reversed", true)
                else -> {
                    putBoolean("auto_play_next", false)
                    putBoolean("auto_play_reversed", false)
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        SleepTimer.set(minutes)
    }

    fun setSleepTimerEndOfSurah() {
        SleepTimer.setEndOfSurah()
    }

    fun releaseController() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        player = null
    }
}
