package ialorabi.ms.alminshawi.telawat.player

import android.content.ComponentName
import android.content.Context
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import ialorabi.ms.alminshawi.telawat.R
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
import java.io.ByteArrayOutputStream

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

    private var _artworkData: ByteArray? = null

    private fun getArtworkData(): ByteArray? {
        _artworkData?.let { return it }
        val context = getApplication<Application>()
        val isDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val primaryContainer = if (isDark) {
            context.getColor(android.R.color.system_accent1_700)
        } else {
            context.getColor(android.R.color.system_accent1_100)
        }
        val primary = if (isDark) {
            context.getColor(android.R.color.system_accent1_200)
        } else {
            context.getColor(android.R.color.system_accent1_600)
        }
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(primaryContainer)
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.player_logo)
        val logoSize = (size * 0.65f).toInt()
        val scaled = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(primary, PorterDuff.Mode.SRC_IN)
        val left = (size - logoSize) / 2f
        val top = (size - logoSize) / 2f
        canvas.drawBitmap(scaled, left, top, paint)
        logoBitmap.recycle()
        scaled.recycle()
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        _artworkData = stream.toByteArray()
        return _artworkData
    }

    fun refreshArtwork() {
        _artworkData = null
    }

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

    private val _repeatMode = MutableStateFlow(prefs.getBoolean("repeat_mode", false))
    val repeatMode: StateFlow<Boolean> = _repeatMode.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(prefs.getBoolean("auto_play_next", true))
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
    
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "repeat_mode" -> _repeatMode.value = prefs.getBoolean("repeat_mode", false)
            "auto_play_next" -> _autoPlayNext.value = prefs.getBoolean("auto_play_next", true)
            "auto_play_reversed" -> _autoPlayReversed.value = prefs.getBoolean("auto_play_reversed", false)
        }
    }

    init {
        refreshCachedSurahs()
        loadFavorites()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
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
        prefs.edit().putStringSet("favorite_surahs", current.map { it.toString() }.toSet()).apply()
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
                                .setTitle(getLocalizedTitle(surah))
                                .setArtist(getLocalizedArtist())
                                .apply { getArtworkData()?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
                                .build()
                        )
                        .build()
                    
                    exo.setMediaItem(mediaItem, lastPos)
                    exo.playWhenReady = false
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
                bufferingJob?.cancel()
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingJob = viewModelScope.launch {
                        delay(500L)
                        _isBuffering.value = true
                    }
                } else {
                    _isBuffering.value = false
                }
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    isTransitioning = false
                }
                if (playbackState == Player.STATE_ENDED && !isTransitioning) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    _repeatMode.value = prefs.getBoolean("repeat_mode", false)
                    _autoPlayNext.value = prefs.getBoolean("auto_play_next", true)
                    _autoPlayReversed.value = prefs.getBoolean("auto_play_reversed", false)
                    
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
                _currentPosition.value = 0L
            }
        })
    }

    private fun startTrackingProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                if (!isSeeking) {
                    _currentPosition.value = player?.currentPosition ?: 0L
                }
                tickCount++
                if (tickCount % 4 == 0) saveCurrentState()
                delay(500L)
            }
        }
    }

    private fun saveCurrentState() {
        val surahId = _currentPlayingSurahId.value
        val isEnded = player?.playbackState == Player.STATE_ENDED
        val pos = if (isEnded) 0L else (player?.currentPosition ?: 0L)
        if (surahId != null) {
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
        if (surah.id in _cachedSurahIds.value) {
            playFromCache(surah)
        } else if (surah.id !in _downloadingSurahs.value) {
            _currentPlayingSurahId.value = surah.id
            val cache = PlaybackService.cache ?: return
            viewModelScope.launch {
                _downloadingSurahs.value += surah.id
                withContext(Dispatchers.IO) {
                    try {
                        _downloadingProgress.value = _downloadingProgress.value.toMutableMap().apply {
                            put(surah.id, 0.001f)
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
                if (surah.id in _cachedSurahIds.value && _currentPlayingSurahId.value == surah.id) {
                    playFromCache(surah)
                }
            }
        }
    }

    private fun playFromCache(surah: Surah) {
        player?.let { exoPlayer ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(surah.id.toString())
                .setUri(surah.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getLocalizedTitle(surah))
                        .setArtist(getLocalizedArtist())
                        .apply { getArtworkData()?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
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
                if (it.playbackState == Player.STATE_ENDED) {
                    it.seekTo(0)
                    _currentPosition.value = 0L
                }
                it.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        isSeeking = true
        _currentPosition.value = positionMs
    }

    fun finishSeek() {
        player?.seekTo(_currentPosition.value)
        isSeeking = false
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
        val newState = !_repeatMode.value
        _repeatMode.value = newState
        prefs.edit().putBoolean("repeat_mode", newState).apply()
        if (newState) {
            _autoPlayNext.value = false
            prefs.edit().putBoolean("auto_play_next", false).apply()
        }
        PlaybackService.instance?.refreshCustomLayout()
    }

    fun toggleAutoPlayNext() {
        val newState = !_autoPlayNext.value
        _autoPlayNext.value = newState
        prefs.edit().putBoolean("auto_play_next", newState).apply()
        if (newState) {
            _repeatMode.value = false
            prefs.edit().putBoolean("repeat_mode", false).apply()
        }
        PlaybackService.instance?.refreshCustomLayout()
    }

    fun toggleAutoPlayReversed() {
        val newState = !_autoPlayReversed.value
        _autoPlayReversed.value = newState
        prefs.edit().putBoolean("auto_play_reversed", newState).apply()
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
