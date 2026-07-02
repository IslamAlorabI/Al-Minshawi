package ialorabi.ms.alminshawi.telawat.player

import android.content.ComponentName
import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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

    private val _downloadingSurahs = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingSurahs: StateFlow<Set<Int>> = _downloadingSurahs.asStateFlow()

    private val _cachedSurahIds = MutableStateFlow<Set<Int>>(emptySet())
    val cachedSurahIds: StateFlow<Set<Int>> = _cachedSurahIds.asStateFlow()

    private val _downloadingProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadingProgress: StateFlow<Map<Int, Float>> = _downloadingProgress.asStateFlow()

    private var progressJob: Job? = null

    private val _repeatMode = MutableStateFlow(prefs.getBoolean("repeat_mode", false))
    val repeatMode: StateFlow<Boolean> = _repeatMode.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(prefs.getBoolean("auto_play_next", false))
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _autoPlayReversed = MutableStateFlow(prefs.getBoolean("auto_play_reversed", false))
    val autoPlayReversed: StateFlow<Boolean> = _autoPlayReversed.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    private val _sleepTimerSelectedMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerSelectedMinutes: StateFlow<Int?> = _sleepTimerSelectedMinutes.asStateFlow()


    private val _favoriteSurahIds = MutableStateFlow<Set<Int>>(emptySet())
    val favoriteSurahIds: StateFlow<Set<Int>> = _favoriteSurahIds.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var bufferingJob: Job? = null
    private var isTransitioning = false
    private var isSeeking = false
    private var isSkipping = false
    private val _pendingDownloadSurahId = MutableStateFlow<Int?>(null)
    val pendingDownloadSurahId: StateFlow<Int?> = _pendingDownloadSurahId.asStateFlow()

    private val _downloadLimitReached = MutableSharedFlow<Unit>()
    val downloadLimitReached: SharedFlow<Unit> = _downloadLimitReached.asSharedFlow()

    private val _downloadQueueLimitReached = MutableSharedFlow<Unit>()
    val downloadQueueLimitReached: SharedFlow<Unit> = _downloadQueueLimitReached.asSharedFlow()

    private val _downloadFailed = MutableSharedFlow<Unit>()
    val downloadFailed: SharedFlow<Unit> = _downloadFailed.asSharedFlow()

    private val _playbackError = MutableSharedFlow<Unit>()
    val playbackError: SharedFlow<Unit> = _playbackError.asSharedFlow()


    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "repeat_mode" -> _repeatMode.value = prefs.getBoolean("repeat_mode", false)
            "auto_play_next" -> _autoPlayNext.value = prefs.getBoolean("auto_play_next", false)
            "auto_play_reversed" -> _autoPlayReversed.value = prefs.getBoolean("auto_play_reversed", false)
        }
    }

    init {
        refreshCachedSurahs()
        loadFavorites()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        sleepTimerJob?.cancel()
    }
    
    fun refreshCachedSurahs() {
        _cachedSurahIds.value = PlaybackService.getCachedSurahs().map { it.id }.toSet()
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
        if (_downloadingSurahs.value.contains(surah.id)) return
        PlaybackService.instance?.downloadSurahInBackground(surah)
    }

    fun cancelDownload(surahId: Int) {
        if (surahId == _pendingDownloadSurahId.value) {
            PlaybackService.instance?.cancelPlayDownload()
            _pendingDownloadSurahId.value = null
        } else {
            PlaybackService.instance?.cancelManualDownload(surahId)
        }
        _downloadingSurahs.value -= surahId
        _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply { remove(surahId) }
        _cachedSurahIds.value -= surahId
        viewModelScope.launch {
            delay(1000.milliseconds)
            refreshCachedSurahs()
        }
    }

    fun cancelAllDownloads() {
        val downloading = _downloadingSurahs.value
        PlaybackService.instance?.cancelAllDownloads()
        _pendingDownloadSurahId.value = null
        _downloadingSurahs.value = emptySet()
        _downloadingProgress.value = emptyMap()
        _cachedSurahIds.value -= downloading
        viewModelScope.launch {
            delay(1000.milliseconds)
            refreshCachedSurahs()
        }
    }



    fun initializeController(context: Context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener(
            {
                player = mediaControllerFuture?.get()
                setupPlayerListeners()
                restoreLastState()
                PlaybackService.instance?.onWidgetDownloadStateChanged = { surahId, downloading, progress ->
                    if (downloading) {
                        _pendingDownloadSurahId.value = surahId
                        _downloadingSurahs.value += surahId
                        _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                            put(surahId, progress)
                        }
                    } else {
                        _pendingDownloadSurahId.value = null
                        _downloadingSurahs.value -= surahId
                        _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                            remove(surahId)
                        }
                        refreshCachedSurahs()
                    }
                }
                PlaybackService.instance?.onManualDownloadStateChanged = { surahId, downloading, progress ->
                    if (downloading) {
                        _downloadingSurahs.value += surahId
                        _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                            put(surahId, progress)
                        }
                    } else {
                        _downloadingSurahs.value -= surahId
                        _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                            remove(surahId)
                        }
                        refreshCachedSurahs()
                    }
                }
                PlaybackService.instance?.onDownloadFailed = {
                    viewModelScope.launch {
                        _pendingDownloadSurahId.value = null
                        _downloadFailed.emit(Unit)
                    }
                }
                PlaybackService.instance?.onDownloadQueueLimitReached = {
                    viewModelScope.launch {
                        _downloadQueueLimitReached.emit(Unit)
                    }
                }
                PlaybackService.instance?.onPlaybackError = {
                    viewModelScope.launch {
                        _playbackError.emit(Unit)
                        refreshCachedSurahs()
                    }
                }
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
                    
                    exo.setMediaItem(mediaItem, lastPos)
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

    private fun startTrackingProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tickCount = 0
            if (!isSeeking) {
                _currentPosition.value = player?.currentPosition ?: 0L
            }
            while (isActive) {
                delay(100L.milliseconds)
                if (!isSeeking) {
                    _currentPosition.value = player?.currentPosition ?: 0L
                }
                tickCount++
                if (tickCount % 20 == 0) saveCurrentState()
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
        }
    }

    private fun stopTrackingProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playSurah(surah: Surah, autoPlay: Boolean = false) {
        if (surah.id in _cachedSurahIds.value) {
            playFromCache(surah)
        } else if (autoPlay) {
            val exoPlayer = player ?: run { isTransitioning = false; isSkipping = false; return }
            PlaybackService.instance?.downloadAndPlay(exoPlayer, surah)
        } else {
            isTransitioning = false
            isSkipping = false
        }
    }

    private fun playFromCache(surah: Surah) {
        _pendingDownloadSurahId.value = null
        player?.let { exoPlayer ->
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

            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isSkipping = false
        }
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
        PlaybackService.isUserSeeking = true
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
            PlaybackService.isUserSeeking = false
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
        _repeatMode.value = newState
        prefs.edit { putBoolean("repeat_mode", newState) }
        if (newState) {
            _autoPlayNext.value = false
            prefs.edit { putBoolean("auto_play_next", false) }
        }
        PlaybackService.instance?.refreshCustomLayout()
    }

    fun toggleAutoPlayNext() {
        val newState = !_autoPlayNext.value
        _autoPlayNext.value = newState
        prefs.edit { putBoolean("auto_play_next", newState) }
        if (newState) {
            _repeatMode.value = false
            prefs.edit { putBoolean("repeat_mode", false) }
        }
        PlaybackService.instance?.refreshCustomLayout()
    }



    fun toggleAutoPlayReversed() {
        val newState = !_autoPlayReversed.value
        _autoPlayReversed.value = newState
        prefs.edit { putBoolean("auto_play_reversed", newState) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingMs.value = 0L
            _sleepTimerSelectedMinutes.value = null
            return
        }
        _sleepTimerSelectedMinutes.value = minutes
        val totalMs = minutes * 60 * 1000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                delay(500L.milliseconds)
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (totalMs - elapsed).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = remaining
                if (remaining <= 0L) {
                    player?.pause()
                    _sleepTimerRemainingMs.value = 0L
                    _sleepTimerSelectedMinutes.value = null
                    break
                }
            }
        }
    }

    fun releaseController() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }
}
