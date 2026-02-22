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
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    var player: Player? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPlayingSurahId = MutableStateFlow<Int?>(null)
    val currentPlayingSurahId: StateFlow<Int?> = _currentPlayingSurahId.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _downloadingSurahs = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingSurahs: StateFlow<Set<Int>> = _downloadingSurahs.asStateFlow()

    private val _cachedSurahIds = MutableStateFlow<Set<Int>>(emptySet())
    val cachedSurahIds: StateFlow<Set<Int>> = _cachedSurahIds.asStateFlow()

    private val _downloadingProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadingProgress: StateFlow<Map<Int, Float>> = _downloadingProgress.asStateFlow()

    private var progressJob: Job? = null

    private val _repeatMode = MutableStateFlow(false)
    val repeatMode: StateFlow<Boolean> = _repeatMode.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(true)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _autoPlayReversed = MutableStateFlow(false)
    val autoPlayReversed: StateFlow<Boolean> = _autoPlayReversed.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var isTransitioning = false
    
    init {
        refreshCachedSurahs()
    }
    
    fun refreshCachedSurahs() {
        _cachedSurahIds.value = PlaybackService.getCachedSurahs().map { it.id }.toSet()
    }

    fun downloadSurah(surah: Surah) {
        val cache = PlaybackService.cache ?: return
        viewModelScope.launch {
            _downloadingSurahs.value += surah.id
            withContext(Dispatchers.IO) {
                try {
                    _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                        put(surah.id, 0.001f) // Immediately show a tiny bar
                    }
                    val dataSource = CacheDataSource.Factory()
                        .setCache(cache)
                        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                        .createDataSource()
                    val dataSpec = DataSpec(Uri.parse(surah.url))
                    
                    val progressListener = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                        if (requestLength > 0) {
                            val progress = bytesCached.toFloat() / requestLength.toFloat()
                            _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                                put(surah.id, progress)
                            }
                        }
                    }

                    val writer = CacheWriter(dataSource, dataSpec, null, progressListener)
                    writer.cache()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _downloadingSurahs.value -= surah.id
                    _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                        remove(surah.id)
                    }
                    refreshCachedSurahs()
                }
            }
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
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun restoreLastState() {
        val exo = player ?: return
        if (exo.mediaItemCount == 0) {
            val lastSurahId = prefs.getInt("last_surah_id", -1)
            val lastPos = prefs.getLong("last_pos", 0L)
            
            if (lastSurahId != -1) {
                val surahs = SurahRepository.surahs
                val surah = surahs.find { it.id == lastSurahId }
                if (surah != null) {
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(surah.id.toString())
                        .setUri(surah.url)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(surah.name)
                                .setArtist("الشيخ محمد صديق المنشاوي")
                                .build()
                        )
                        .build()
                    
                    exo.setMediaItem(mediaItem, lastPos)
                    exo.prepare()
                    
                    _currentPlayingSurahId.value = lastSurahId
                    _currentPosition.value = lastPos
                }
            }
        } else {
             _currentPlayingSurahId.value = exo.currentMediaItem?.mediaId?.toIntOrNull()
             _currentPosition.value = exo.currentPosition
             _duration.value = exo.duration.coerceAtLeast(0L)
             _isPlaying.value = exo.isPlaying
             _isBuffering.value = exo.playbackState == Player.STATE_BUFFERING
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
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    isTransitioning = false
                }
                if (playbackState == Player.STATE_ENDED && !isTransitioning) {
                    if (_repeatMode.value) {
                        player?.seekTo(0)
                        player?.play()
                    } else if (_autoPlayNext.value) {
                        isTransitioning = true
                        if (_autoPlayReversed.value) playPreviousSurah() else playNextSurah()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val surahId = mediaItem?.mediaId?.toIntOrNull()
                _currentPlayingSurahId.value = surahId
            }
        })
    }

    private fun startTrackingProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                _currentPosition.value = player?.currentPosition ?: 0L
                tickCount++
                if (tickCount % 4 == 0) saveCurrentState()
                delay(250L)
            }
        }
    }

    private fun saveCurrentState() {
        val surahId = _currentPlayingSurahId.value
        val pos = player?.currentPosition ?: 0L
        if (surahId != null && pos > 0) {
            prefs.edit()
                .putInt("last_surah_id", surahId)
                .putLong("last_pos", pos)
                .apply()
        }
    }

    private fun stopTrackingProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playSurah(surah: Surah) {
        player?.let { exoPlayer ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(surah.id.toString())
                .setUri(surah.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(surah.name)
                        .setArtist("\u0627\u0644\u0634\u064A\u062E \u0645\u062D\u0645\u062F \u0635\u062F\u064A\u0642 \u0627\u0644\u0645\u0646\u0634\u0627\u0648\u064A")
                        .build()
                )
                .build()

            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun seekForward() {
        player?.let {
            val nextPosition = (it.currentPosition + 30_000).coerceAtMost(it.duration)
            seekTo(nextPosition)
        }
    }

    fun seekBackward() {
        player?.let {
            val previousPosition = (it.currentPosition - 10_000).coerceAtLeast(0L)
            seekTo(previousPosition)
        }
    }

    fun playNextSurah() {
        val currentId = _currentPlayingSurahId.value ?: return
        val surahs = SurahRepository.surahs
        if (currentId < surahs.size) {
            playSurah(surahs[currentId])
        } else {
            isTransitioning = false
        }
    }

    fun playPreviousSurah() {
        val currentId = _currentPlayingSurahId.value ?: return
        val surahs = SurahRepository.surahs
        if (currentId > 1) {
            playSurah(surahs[currentId - 2])
        } else {
            isTransitioning = false
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = !_repeatMode.value
        if (_repeatMode.value) _autoPlayNext.value = false
    }

    fun toggleAutoPlayNext() {
        _autoPlayNext.value = !_autoPlayNext.value
        if (_autoPlayNext.value) _repeatMode.value = false
    }

    fun toggleAutoPlayReversed() {
        _autoPlayReversed.value = !_autoPlayReversed.value
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingMs.value = 0L
            return
        }
        val totalMs = minutes * 60 * 1000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (totalMs - elapsed).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = remaining
                if (remaining <= 0L) {
                    player?.pause()
                    _sleepTimerRemainingMs.value = 0L
                    break
                }
                delay(1000L)
            }
        }
    }

    fun releaseController() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }
}
