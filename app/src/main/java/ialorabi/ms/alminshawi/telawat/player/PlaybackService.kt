package ialorabi.ms.alminshawi.telawat.player

import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import java.io.File
import android.os.Environment
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaSessionService() {
    private var _mediaSession: MediaSession? = null
    val mediaSession: MediaSession? get() = _mediaSession
    private var _artworkData: ByteArray? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downloadJob: Job? = null
    private var currentDownloadingSurahId: Int? = null
    private var isSkipping = false
    var onWidgetDownloadStateChanged: ((surahId: Int, downloading: Boolean, progress: Float) -> Unit)? = null
    var onManualDownloadStateChanged: ((surahId: Int, downloading: Boolean, progress: Float) -> Unit)? = null
    var onDownloadFailed: (() -> Unit)? = null
    var onPlaybackError: (() -> Unit)? = null
    private val manualDownloadJobs = mutableMapOf<Int, Job>()

    companion object {
        var instance: PlaybackService? = null
            private set
        
        var cache: SimpleCache? = null
            private set
        private const val CACHE_SIZE = 2L * 1024 * 1024 * 1024
        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val HTTP_TIMEOUT_MS = 30_000

        const val ACTION_REPEAT = "action_repeat"
        const val ACTION_AUTO_NEXT = "action_auto_next"
        const val ACTION_PREV_SURAH = "action_prev_surah"
        const val ACTION_NEXT_SURAH = "action_next_surah"

        fun getCacheSize(context: Context): Long {
            return cache?.cacheSpace ?: getFolderSize(File(context.filesDir, "audio_cache"))
        }

        fun clearCache(context: Context) {
            val player = instance?.mediaSession?.player
            player?.stop()
            player?.clearMediaItems()
            cache?.keys?.toSet()?.forEach { cache?.removeResource(it) }
            val cacheFolder = File(context.filesDir, "audio_cache")
            if (cache == null && cacheFolder.exists()) {
                cacheFolder.deleteRecursively()
            }
        }

        fun getCachedSurahs(): List<Surah> {
            val cachedKeys = cache?.keys ?: emptySet()
            return SurahRepository.surahs.filter { surah -> cachedKeys.contains(surah.url) }
        }

        fun isSurahCached(surah: Surah): Boolean {
            return cache?.keys?.contains(surah.url) == true
        }

        fun removeSurahCache(surah: Surah) {
            val player = instance?.mediaSession?.player
            if (player?.currentMediaItem?.mediaId == surah.id.toString()) {
                player.stop()
                player.clearMediaItems()
            }
            cache?.removeResource(surah.url)
        }

        fun saveSurahToDownloads(context: Context, surah: Surah, fileName: String): Boolean {
            val c = cache ?: return false
            val spans = c.getCachedSpans(surah.url)
            if (spans.isEmpty()) return false

            return try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(downloadsDir, fileName)
                outputFile.outputStream().buffered().use { output ->
                    spans.sortedBy { it.position }.forEach { span ->
                        span.file?.inputStream()?.buffered()?.use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("audio/mpeg"),
                    null
                )
                true
            } catch (_: Exception) {
                false
            }
        }

        fun getActiveDownloadCount(): Int {
            return instance?.manualDownloadJobs?.size ?: 0
        }

        private fun getFolderSize(file: File): Long {
            var size: Long = 0
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    size += getFolderSize(child)
                }
            } else if (file.isFile) {
                size += file.length()
            }
            return size
        }
    }

    private val prefs by lazy { getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }

    private fun getArtworkData(): ByteArray? {
        _artworkData?.let { return it }
        _artworkData = ArtworkHelper.generate(this)
        return _artworkData
    }

    private fun buildMediaItem(surah: Surah): MediaItem {
        val localizedNames = resources.getStringArray(R.array.surah_names)
        val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
        val prefix = getString(R.string.surah_prefix)
        val title = "$prefix $name (${surah.id})"
        val artist = getString(R.string.sheikh_name)

        return MediaItem.Builder()
            .setMediaId(surah.id.toString())
            .setUri(surah.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .apply { getArtworkData()?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
                    .build()
            )
            .build()
    }

    fun downloadAndPlay(player: Player, surah: Surah) {
        val previousDownloadingSurahId = currentDownloadingSurahId
        downloadJob?.cancel()
        if (previousDownloadingSurahId != null) {
            onWidgetDownloadStateChanged?.invoke(previousDownloadingSurahId, false, 0f)
        }
        currentDownloadingSurahId = null

        if (isSurahCached(surah)) {
            player.stop()
            player.setMediaItem(buildMediaItem(surah))
            player.prepare()
            player.play()
            isSkipping = false
            return
        }

        val downloadingTitle = getString(R.string.widget_downloading, getLocalizedSurahName(surah))

        if (player.mediaItemCount > 0) {
            val currentItem = player.currentMediaItem!!
            val indicatorItem = currentItem.buildUpon()
                .setMediaMetadata(
                    currentItem.mediaMetadata.buildUpon()
                        .setTitle(downloadingTitle)
                        .build()
                )
                .build()
            player.replaceMediaItem(0, indicatorItem)
        }
        player.pause()
        currentDownloadingSurahId = surah.id
        onWidgetDownloadStateChanged?.invoke(surah.id, true, 0.001f)

        downloadJob = serviceScope.launch {
            val success = withContext(Dispatchers.IO) {
                val c = cache ?: return@withContext false
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
                    .setReadTimeoutMs(HTTP_TIMEOUT_MS)
                val dataSource = CacheDataSource.Factory()
                    .setCache(c)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .createDataSource()
                val dataSpec = DataSpec(surah.url.toUri())
                val progressListener = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                    if (requestLength > 0 && currentDownloadingSurahId == surah.id) {
                        val progress = bytesCached.toFloat() / requestLength.toFloat()
                        serviceScope.launch {
                            onWidgetDownloadStateChanged?.invoke(surah.id, true, progress)
                        }
                    }
                }
                var lastError: Exception? = null
                for (attempt in 1..MAX_DOWNLOAD_RETRIES) {
                    try {
                        val writer = CacheWriter(dataSource, dataSpec, null, progressListener)
                        runInterruptible { writer.cache() }
                        return@withContext true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        lastError = e
                        if (attempt < MAX_DOWNLOAD_RETRIES) {
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }
                lastError?.printStackTrace()
                false
            }
            currentDownloadingSurahId = null
            onWidgetDownloadStateChanged?.invoke(surah.id, false, 0f)
            if (success) {
                player.stop()
                player.setMediaItem(buildMediaItem(surah))
                player.prepare()
                player.play()
            } else {
                onDownloadFailed?.invoke()
            }
            isSkipping = false
        }
    }

    fun downloadSurahInBackground(surah: Surah) {
        if (manualDownloadJobs.containsKey(surah.id)) return
        if (manualDownloadJobs.size >= MAX_PARALLEL_DOWNLOADS) return
        val c = cache ?: return
        manualDownloadJobs[surah.id] = serviceScope.launch {
            onManualDownloadStateChanged?.invoke(surah.id, true, 0.001f)
            val success = withContext(Dispatchers.IO) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
                    .setReadTimeoutMs(HTTP_TIMEOUT_MS)
                val dataSource = CacheDataSource.Factory()
                    .setCache(c)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .createDataSource()
                val dataSpec = DataSpec(surah.url.toUri())
                val progressListener = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                    if (requestLength > 0 && manualDownloadJobs.containsKey(surah.id)) {
                        val progress = bytesCached.toFloat() / requestLength.toFloat()
                        serviceScope.launch {
                            onManualDownloadStateChanged?.invoke(surah.id, true, progress)
                        }
                    }
                }
                var lastError: Exception? = null
                for (attempt in 1..MAX_DOWNLOAD_RETRIES) {
                    try {
                        val writer = CacheWriter(dataSource, dataSpec, null, progressListener)
                        runInterruptible { writer.cache() }
                        return@withContext true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        lastError = e
                        if (attempt < MAX_DOWNLOAD_RETRIES) {
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }
                lastError?.printStackTrace()
                false
            }
            manualDownloadJobs.remove(surah.id)
            onManualDownloadStateChanged?.invoke(surah.id, false, 0f)
        }
    }

    fun cancelManualDownload(surahId: Int) {
        val job = manualDownloadJobs.remove(surahId)
        onManualDownloadStateChanged?.invoke(surahId, false, 0f)
        val surah = SurahRepository.surahs.find { it.id == surahId } ?: return
        serviceScope.launch {
            job?.cancelAndJoin()
            withContext(Dispatchers.IO) { cache?.removeResource(surah.url) }
        }
    }

    fun cancelPlayDownload() {
        val surahId = currentDownloadingSurahId ?: return
        val job = downloadJob
        downloadJob = null
        currentDownloadingSurahId = null
        onWidgetDownloadStateChanged?.invoke(surahId, false, 0f)
        isSkipping = false
        val surah = SurahRepository.surahs.find { it.id == surahId }
        serviceScope.launch {
            job?.cancelAndJoin()
            if (surah != null) withContext(Dispatchers.IO) { cache?.removeResource(surah.url) }
        }
    }

    fun cancelAllDownloads() {
        val playId = currentDownloadingSurahId
        if (playId != null) cancelPlayDownload()

        manualDownloadJobs.keys.toList().forEach { cancelManualDownload(it) }
    }

    private fun getLocalizedSurahName(surah: Surah): String {
        val localizedNames = resources.getStringArray(R.array.surah_names)
        val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
        val prefix = getString(R.string.surah_prefix)
        return "$prefix $name (${surah.id})"
    }

    private fun playNextSurah(player: Player) {
        if (isSkipping) return
        isSkipping = true
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: run { isSkipping = false; return }
        val surahs = SurahRepository.surahs
        if (currentId < surahs.size) {
            downloadAndPlay(player, surahs[currentId])
        } else {
            isSkipping = false
        }
    }

    private fun playPreviousSurah(player: Player) {
        if (isSkipping) return
        isSkipping = true
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: run { isSkipping = false; return }
        val surahs = SurahRepository.surahs
        if (currentId > 1) {
            downloadAndPlay(player, surahs[currentId - 2])
        } else {
            isSkipping = false
        }
    }


    fun refreshLanguage() {
        refreshCustomLayout()
        val player = _mediaSession?.player ?: return
        val currentItem = player.currentMediaItem ?: return
        val surahId = currentItem.mediaId.toIntOrNull() ?: return
        val currentSurah = SurahRepository.surahs.find { it.id == surahId } ?: return
        
        // This will update the metadata with the new localized strings without interrupting playback
        player.replaceMediaItem(player.currentMediaItemIndex, buildMediaItem(currentSurah))
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val repeatOn = prefs.getBoolean("repeat_mode", false)
        val autoNextOn = prefs.getBoolean("auto_play_next", false)

        val repeatIcon = if (repeatOn) CommandButton.ICON_REPEAT_ONE else CommandButton.ICON_REPEAT_OFF
        val autoNextIcon = if (autoNextOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF

        return listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(SessionCommand(ACTION_PREV_SURAH, Bundle.EMPTY))
                .setDisplayName(getString(R.string.rewind))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            CommandButton.Builder(repeatIcon)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.repeat_surah))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(autoNextIcon)
                .setSessionCommand(SessionCommand(ACTION_AUTO_NEXT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.auto_play_next))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(SessionCommand(ACTION_NEXT_SURAH, Bundle.EMPTY))
                .setDisplayName(getString(R.string.forward))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build()
        )
    }

    fun refreshCustomLayout() {
        _mediaSession?.let { session ->
            val layout = buildCustomLayout()
            session.setCustomLayout(layout)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.player_logo)
        setMediaNotificationProvider(notificationProvider)

        if (cache == null) {
            val oldCacheDir = File(cacheDir, "audio_cache")
            val newCacheDir = File(filesDir, "audio_cache")
            if (oldCacheDir.exists() && !newCacheDir.exists()) {
                oldCacheDir.renameTo(newCacheDir)
            } else if (oldCacheDir.exists() && newCacheDir.exists()) {
                oldCacheDir.deleteRecursively()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(this)
            cache = SimpleCache(newCacheDir, evictor, databaseProvider)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                30_000,
                0,
                0
            )
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val mediaId = exoPlayer.currentMediaItem?.mediaId?.toIntOrNull()
                if (mediaId != null) {
                    val surah = SurahRepository.surahs.find { it.id == mediaId }
                    if (surah != null) cache?.removeResource(surah.url)
                }
                onPlaybackError?.invoke()
            }
        })

        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> false
                    else -> super.isCommandAvailable(command)
                }
            }
        }

        val customCommands = listOf(ACTION_REPEAT, ACTION_AUTO_NEXT, ACTION_PREV_SURAH, ACTION_NEXT_SURAH)
            .map { SessionCommand(it, Bundle.EMPTY) }

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                customCommands.forEach { sessionCommands.add(it) }
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands.build())
                    .setAvailablePlayerCommands(playerCommands)
                    .setCustomLayout(buildCustomLayout())
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_REPEAT -> {
                        val newState = !prefs.getBoolean("repeat_mode", false)
                        prefs.edit { putBoolean("repeat_mode", newState) }
                        if (newState) {
                            prefs.edit { putBoolean("auto_play_next", false) }
                        }
                        refreshCustomLayout()
                    }
                    ACTION_AUTO_NEXT -> {
                        val newState = !prefs.getBoolean("auto_play_next", false)
                        prefs.edit { putBoolean("auto_play_next", newState) }
                        if (newState) {
                            prefs.edit { putBoolean("repeat_mode", false) }
                        }
                        refreshCustomLayout()
                    }
                    ACTION_PREV_SURAH -> {
                        playPreviousSurah(session.player)
                    }
                    ACTION_NEXT_SURAH -> {
                        playNextSurah(session.player)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val sessionActivityIntent = Intent(this, ialorabi.ms.alminshawi.telawat.MainActivity::class.java).apply {
            putExtra("OPEN_PLAYER", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        _mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            return START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = _mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = _mediaSession

    override fun onDestroy() {
        downloadJob?.cancel()
        manualDownloadJobs.values.forEach { it.cancel() }
        manualDownloadJobs.clear()
        onWidgetDownloadStateChanged = null
        onManualDownloadStateChanged = null
        _mediaSession?.run {
            player.release()
            release()
            _mediaSession = null
        }
        cache?.release()
        cache = null
        instance = null
        super.onDestroy()
    }
}
