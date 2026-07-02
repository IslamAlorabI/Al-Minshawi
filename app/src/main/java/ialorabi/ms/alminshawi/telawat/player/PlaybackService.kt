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
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import java.io.File
import android.os.Environment
import com.google.common.collect.ImmutableList
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
import kotlin.time.Duration.Companion.seconds

@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaLibraryService() {
    private var _mediaSession: MediaLibrarySession? = null
    val mediaSession: MediaLibrarySession? get() = _mediaSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downloadJob: Job? = null
    private var currentDownloadingSurahId: Int? = null
    private var isSkipping = false
    private var isAutoPlayTransitioning = false
    var onWidgetDownloadStateChanged: ((surahId: Int, downloading: Boolean, progress: Float) -> Unit)? = null
    var onManualDownloadStateChanged: ((surahId: Int, downloading: Boolean, progress: Float) -> Unit)? = null
    var onDownloadFailed: (() -> Unit)? = null
    var onPlaybackError: (() -> Unit)? = null
    var onDownloadQueueLimitReached: (() -> Unit)? = null
    private val manualDownloadJobs = mutableMapOf<Int, Job>()
    private val cancelledManualDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val downloadQueue = mutableListOf<Surah>()

    companion object {
        var instance: PlaybackService? = null
            private set
        @Volatile
        var isUserSeeking = false
        
        var cache: SimpleCache? = null
            private set

        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val MAX_DOWNLOAD_RETRIES = 5
        private const val HTTP_TIMEOUT_MS = 30_000

        const val ACTION_REPEAT = "action_repeat"
        const val ACTION_AUTO_NEXT = "action_auto_next"
        const val ACTION_PREV_SURAH = "action_prev_surah"
        const val ACTION_NEXT_SURAH = "action_next_surah"

        private const val BROWSE_ROOT = "root"
        private const val BROWSE_ALL_SURAHS = "all_surahs"
        private const val BROWSE_BY_JUZ = "by_juz"
        private const val BROWSE_JUZ_PREFIX = "juz_"

        fun getCacheSize(context: Context): Long {
            return cache?.cacheSpace ?: getFolderSize(File(context.filesDir, "audio_cache"))
        }

        fun clearCache(context: Context) {
            val player = instance?.mediaSession?.player
            player?.stop()
            player?.clearMediaItems()
            cache?.keys?.toSet()?.forEach { 
                try {
                    cache?.removeResource(it)
                } catch (_: Exception) {}
            }
            val cacheFolder = File(context.filesDir, "audio_cache")
            if (cache == null && cacheFolder.exists()) {
                cacheFolder.deleteRecursively()
            }
            val sharedPrefs = context.getSharedPreferences("player_prefs", MODE_PRIVATE)
            sharedPrefs.edit { remove("downloaded_surahs") }
        }

        fun getCachedSurahs(): List<Surah> {
            return SurahRepository.surahs.filter { isSurahCached(it) }
        }

        fun isSurahCached(surah: Surah): Boolean {
            val c = cache ?: return false
            val inst = instance
            if (inst != null) {
                val downloadedSet = inst.prefs.getStringSet("downloaded_surahs", emptySet()) ?: emptySet()
                return downloadedSet.contains(surah.id.toString()) && c.keys.contains(surah.url)
            }
            val length = c.getContentMetadata(surah.url).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            if (length <= 0) return false
            return c.getCachedBytes(surah.url, 0, length) == length
        }

        fun removeSurahCache(surah: Surah) {
            val player = instance?.mediaSession?.player
            if (player?.currentMediaItem?.mediaId == surah.id.toString()) {
                player.stop()
                player.clearMediaItems()
            }
            cache?.removeResource(surah.url)
            instance?.removeSurahFromDownloaded(surah.id)
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

    private val prefs by lazy { getSharedPreferences("player_prefs", MODE_PRIVATE) }

    fun markSurahAsDownloaded(surahId: Int) {
        val current = prefs.getStringSet("downloaded_surahs", emptySet()) ?: emptySet()
        val newSet = current.toMutableSet().apply { add(surahId.toString()) }
        prefs.edit { putStringSet("downloaded_surahs", newSet) }
    }

    fun removeSurahFromDownloaded(surahId: Int) {
        val current = prefs.getStringSet("downloaded_surahs", emptySet()) ?: emptySet()
        val newSet = current.toMutableSet().apply { remove(surahId.toString()) }
        prefs.edit { putStringSet("downloaded_surahs", newSet) }
    }

    private fun getArtworkUri(): android.net.Uri? {
        return ArtworkHelper.getArtworkUri(this)
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
                    .apply { getArtworkUri()?.let { setArtworkUri(it) } }
                    .build()
            )
            .build()
    }

    private fun buildBrowseFolderItem(mediaId: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    private fun buildBrowsableSurahItem(surah: Surah): MediaItem {
        val localizedNames = resources.getStringArray(R.array.surah_names)
        val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
        val prefix = getString(R.string.surah_prefix)
        val title = "$prefix $name (${surah.id})"
        val artist = getString(R.string.sheikh_name)
        val downloadStatus = if (isSurahCached(surah)) {
            "✓ ${getString(R.string.filter_downloaded)}"
        } else {
            getString(R.string.filter_not_downloaded)
        }

        return MediaItem.Builder()
            .setMediaId(surah.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle("$artist · $downloadStatus")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply { getArtworkUri()?.let { setArtworkUri(it) } }
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
            val newItem = buildMediaItem(surah)
            if (player.mediaItemCount > 0) {
                player.replaceMediaItem(player.currentMediaItemIndex, newItem)
                player.seekTo(0)
            } else {
                player.setMediaItem(newItem)
            }
            player.prepare()
            player.play()
            isSkipping = false
            return
        }

        try {
            cache?.removeResource(surah.url)
        } catch (_: Exception) {}

        val downloadingTitle = getLocalizedSurahName(surah)
        val downloadingArtist = getString(R.string.widget_downloading_status)

        val originalMediaItem = if (player.mediaItemCount > 0) player.currentMediaItem else null
        val originalPosition = player.currentPosition

        if (player.mediaItemCount > 0) {
            val currentItem = player.currentMediaItem!!
            val indicatorItem = currentItem.buildUpon()
                .setMediaMetadata(
                    currentItem.mediaMetadata.buildUpon()
                        .setTitle(downloadingTitle)
                        .setArtist(downloadingArtist)
                        .build()
                )
                .build()
            player.replaceMediaItem(0, indicatorItem)
        } else {
            val placeholderItem = buildMediaItem(surah).buildUpon()
                .setMediaMetadata(
                    buildMediaItem(surah).mediaMetadata.buildUpon()
                        .setTitle(downloadingTitle)
                        .setArtist(downloadingArtist)
                        .build()
                )
                .build()
            player.setMediaItem(placeholderItem)
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
                    if (currentDownloadingSurahId != surah.id) {
                        throw CancellationException("Play download cancelled")
                    }
                    if (requestLength > 0) {
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
                            delay(attempt.seconds)
                        }
                    }
                }
                lastError?.printStackTrace()
                false
            }
            currentDownloadingSurahId = null
            if (success) {
                markSurahAsDownloaded(surah.id)
            }
            onWidgetDownloadStateChanged?.invoke(surah.id, false, 0f)
            if (success) {
                val newItem = buildMediaItem(surah)
                if (player.mediaItemCount > 0) {
                    player.replaceMediaItem(player.currentMediaItemIndex, newItem)
                    player.seekTo(0)
                } else {
                    player.setMediaItem(newItem)
                }
                player.prepare()
                player.play()
            } else {
                if (player.mediaItemCount > 0) {
                    val currentItem = player.currentMediaItem!!
                    val restoredItem = currentItem.buildUpon()
                        .setMediaMetadata(
                            originalMediaItem?.mediaMetadata ?: currentItem.mediaMetadata
                        )
                        .build()
                    player.replaceMediaItem(0, restoredItem)
                    player.seekTo(originalPosition)
                }
                onDownloadFailed?.invoke()
            }
            isSkipping = false
        }
    }


    private fun processNextInQueue() {
        if (downloadQueue.isNotEmpty() && manualDownloadJobs.size < MAX_PARALLEL_DOWNLOADS) {
            val nextSurah = downloadQueue.removeAt(0)
            startManualDownload(nextSurah)
        }
    }

    fun downloadSurahInBackground(surah: Surah) {
        if (manualDownloadJobs.containsKey(surah.id) || downloadQueue.any { it.id == surah.id }) return
        
        val totalCount = manualDownloadJobs.size + downloadQueue.size
        if (totalCount >= 10) {
            onDownloadQueueLimitReached?.invoke()
            return
        }
        
        if (manualDownloadJobs.size >= MAX_PARALLEL_DOWNLOADS) {
            downloadQueue.add(surah)
            onManualDownloadStateChanged?.invoke(surah.id, true, 0f)
            return
        }
        
        startManualDownload(surah)
    }

    private fun startManualDownload(surah: Surah) {
        val c = cache ?: return
        manualDownloadJobs[surah.id] = serviceScope.launch {
            onManualDownloadStateChanged?.invoke(surah.id, true, 0.001f)
            val success = withContext(Dispatchers.IO) {
                try {
                    c.removeResource(surah.url)
                } catch (_: Exception) {}
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
                    .setReadTimeoutMs(HTTP_TIMEOUT_MS)
                val dataSource = CacheDataSource.Factory()
                    .setCache(c)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .createDataSource()
                val dataSpec = DataSpec(surah.url.toUri())
                val progressListener = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                    if (cancelledManualDownloads.contains(surah.id)) {
                        throw CancellationException("Manual download cancelled")
                    }
                    if (requestLength > 0 && !cancelledManualDownloads.contains(surah.id)) {
                        val progress = bytesCached.toFloat() / requestLength.toFloat()
                        val safeProgress = progress.coerceIn(0.001f, 1f)
                        serviceScope.launch {
                            if (!cancelledManualDownloads.contains(surah.id)) {
                                onManualDownloadStateChanged?.invoke(surah.id, true, safeProgress)
                            }
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
                            delay(attempt.seconds)
                        }
                    }
                }
                lastError?.printStackTrace()
                false
            }
            manualDownloadJobs.remove(surah.id)
            if (!cancelledManualDownloads.remove(surah.id)) {
                if (success) {
                    markSurahAsDownloaded(surah.id)
                }
                onManualDownloadStateChanged?.invoke(surah.id, false, 0f)
                if (!success) {
                    onDownloadFailed?.invoke()
                }
            }
            processNextInQueue()
        }
    }

    fun cancelManualDownload(surahId: Int) {
        val queuedIndex = downloadQueue.indexOfFirst { it.id == surahId }
        if (queuedIndex != -1) {
            downloadQueue.removeAt(queuedIndex)
            onManualDownloadStateChanged?.invoke(surahId, false, 0f)
            return
        }

        val job = manualDownloadJobs.remove(surahId)
        if (job != null) {
            cancelledManualDownloads.add(surahId)
            val surah = SurahRepository.surahs.find { it.id == surahId }
            serviceScope.launch {
                job.cancelAndJoin()
                if (surah != null) {
                    try {
                        withContext(Dispatchers.IO) { cache?.removeResource(surah.url) }
                    } catch (_: Exception) {}
                    removeSurahFromDownloaded(surah.id)
                }
                onManualDownloadStateChanged?.invoke(surahId, false, 0f)
                processNextInQueue()
            }
        }
    }

    fun cancelPlayDownload() {
        val surahId = currentDownloadingSurahId ?: return
        val job = downloadJob
        downloadJob = null
        currentDownloadingSurahId = null
        isSkipping = false
        val surah = SurahRepository.surahs.find { it.id == surahId }
        serviceScope.launch {
            job?.cancelAndJoin()
            if (surah != null) withContext(Dispatchers.IO) { cache?.removeResource(surah.url) }
        }
    }

    fun cancelAllDownloads() {
        val queuedIds = downloadQueue.map { it.id }
        downloadQueue.clear()
        queuedIds.forEach { onManualDownloadStateChanged?.invoke(it, false, 0f) }

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

    private fun isNextSurahCached(): Boolean {
        val currentId = _mediaSession?.player?.currentMediaItem?.mediaId?.toIntOrNull() ?: return false
        val surahs = SurahRepository.surahs
        if (currentId >= surahs.size) return false
        return isSurahCached(surahs[currentId])
    }

    private fun isPreviousSurahCached(): Boolean {
        val currentId = _mediaSession?.player?.currentMediaItem?.mediaId?.toIntOrNull() ?: return false
        if (currentId <= 1) return false
        return isSurahCached(SurahRepository.surahs[currentId - 2])
    }

    private fun playNextSurah(player: Player) {
        if (isSkipping) return
        isSkipping = true
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: run { isSkipping = false; return }
        val surahs = SurahRepository.surahs
        if (currentId < surahs.size && isSurahCached(surahs[currentId])) {
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
        if (currentId > 1 && isSurahCached(surahs[currentId - 2])) {
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

    private fun resolveAutoPlayIcon(): Pair<Int, Int> {
        val autoNextOn = prefs.getBoolean("auto_play_next", false)
        val autoReversed = prefs.getBoolean("auto_play_reversed", false)
        return when {
            autoNextOn && autoReversed -> R.drawable.ic_auto_play_reverse to R.string.auto_play_reverse
            autoNextOn -> R.drawable.ic_auto_play_next to R.string.auto_play_next
            else -> R.drawable.ic_auto_play_off to R.string.auto_play_next
        }
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val repeatOn = prefs.getBoolean("repeat_mode", false)
        val repeatIcon = if (repeatOn) CommandButton.ICON_REPEAT_ONE else CommandButton.ICON_REPEAT_OFF
        val (autoPlayIconRes, autoPlayNameRes) = resolveAutoPlayIcon()

        val prevCached = isPreviousSurahCached()
        val nextCached = isNextSurahCached()

        return listOf(
            if (prevCached) {
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setSessionCommand(SessionCommand(ACTION_PREV_SURAH, Bundle.EMPTY))
                    .setDisplayName(getString(R.string.rewind))
                    .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
            } else {
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setCustomIconResId(R.drawable.ic_notification_download)
                    .setSessionCommand(SessionCommand(ACTION_PREV_SURAH, Bundle.EMPTY))
                    .setDisplayName(getString(R.string.rewind))
                    .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
            },
            CommandButton.Builder(repeatIcon)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.repeat_surah))
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(autoPlayIconRes)
                .setSessionCommand(SessionCommand(ACTION_AUTO_NEXT, Bundle.EMPTY))
                .setDisplayName(getString(autoPlayNameRes))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            if (nextCached) {
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setSessionCommand(SessionCommand(ACTION_NEXT_SURAH, Bundle.EMPTY))
                    .setDisplayName(getString(R.string.forward))
                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
            } else {
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setCustomIconResId(R.drawable.ic_notification_download)
                    .setSessionCommand(SessionCommand(ACTION_NEXT_SURAH, Bundle.EMPTY))
                    .setDisplayName(getString(R.string.forward))
                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
            }
        )
    }

    private fun buildAutoCustomLayout(): List<CommandButton> {
        val repeatOn = prefs.getBoolean("repeat_mode", false)
        val autoNextOn = prefs.getBoolean("auto_play_next", false)

        val repeatIcon = if (repeatOn) CommandButton.ICON_REPEAT_ONE else CommandButton.ICON_REPEAT_OFF
        val autoNextIcon = if (autoNextOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF

        return listOf(
            CommandButton.Builder(repeatIcon)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.repeat_surah))
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(autoNextIcon)
                .setSessionCommand(SessionCommand(ACTION_AUTO_NEXT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.auto_play_next))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build()
        )
    }

    fun refreshCustomLayout() {
        _mediaSession?.let { session ->
            for (controller in session.connectedControllers) {
                if (session.isMediaNotificationController(controller)) {
                    session.setCustomLayout(controller, buildCustomLayout())
                } else {
                    session.setCustomLayout(controller, buildAutoCustomLayout())
                }
            }
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
            val evictor = NoOpCacheEvictor()
            val databaseProvider = StandaloneDatabaseProvider(this)
            cache = SimpleCache(newCacheDir, evictor, databaseProvider)
        }

        serviceScope.launch(Dispatchers.IO) {
            val c = cache ?: return@launch
            val downloadedSet = prefs.getStringSet("downloaded_surahs", emptySet()) ?: emptySet()
            val newSet = downloadedSet.toMutableSet()
            var modified = false
            
            // 1. Recover/migrate any already fully downloaded surahs into SharedPreferences
            SurahRepository.surahs.forEach { surah ->
                val surahIdStr = surah.id.toString()
                if (!newSet.contains(surahIdStr)) {
                    val length = c.getContentMetadata(surah.url).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                    if (length > 0 && c.getCachedBytes(surah.url, 0, length) == length) {
                        newSet.add(surahIdStr)
                        modified = true
                    }
                }
            }
            
            // 2. Remove any IDs from SharedPreferences that are missing in the actual cache
            val iterator = newSet.iterator()
            while (iterator.hasNext()) {
                val surahIdStr = iterator.next()
                val id = surahIdStr.toIntOrNull()
                val surah = SurahRepository.surahs.find { it.id == id }
                if (surah == null || !c.keys.contains(surah.url)) {
                    iterator.remove()
                    modified = true
                }
            }
            
            if (modified) {
                prefs.edit { putStringSet("downloaded_surahs", newSet) }
            }
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

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isAutoPlayTransitioning = false
                }
                if (playbackState == Player.STATE_ENDED && !isAutoPlayTransitioning && !isUserSeeking) {
                    val repeatOn = prefs.getBoolean("repeat_mode", false)
                    val autoNextOn = prefs.getBoolean("auto_play_next", false)
                    val autoReversed = prefs.getBoolean("auto_play_reversed", false)

                    if (repeatOn) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (autoNextOn) {
                        isAutoPlayTransitioning = true
                        val currentId = exoPlayer.currentMediaItem?.mediaId?.toIntOrNull() ?: return
                        val surahs = SurahRepository.surahs
                        if (autoReversed) {
                            if (currentId > 1) downloadAndPlay(exoPlayer, surahs[currentId - 2])
                            else isAutoPlayTransitioning = false
                        } else {
                            if (currentId < surahs.size) downloadAndPlay(exoPlayer, surahs[currentId])
                            else isAutoPlayTransitioning = false
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshCustomLayout()
            }
        })

        val player = object : ForwardingPlayer(exoPlayer) {
            override fun seekToPrevious() {
                playPreviousSurah(this)
            }

            override fun seekToPreviousMediaItem() {
                playPreviousSurah(this)
            }

            override fun seekToNext() {
                playNextSurah(this)
            }

            override fun seekToNextMediaItem() {
                playNextSurah(this)
            }
        }

        val customCommands = listOf(ACTION_REPEAT, ACTION_AUTO_NEXT, ACTION_PREV_SURAH, ACTION_NEXT_SURAH)
            .map { SessionCommand(it, Bundle.EMPTY) }


        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                customCommands.forEach { sessionCommands.add(it) }

                if (session.isMediaNotificationController(controller)) {
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

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands.build())
                    .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                    .setCustomLayout(buildAutoCustomLayout())
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
                            prefs.edit {
                                putBoolean("auto_play_next", false)
                                putBoolean("auto_play_reversed", false)
                            }
                        }
                        refreshCustomLayout()
                    }
                    ACTION_AUTO_NEXT -> {
                        val autoNextOn = prefs.getBoolean("auto_play_next", false)
                        val autoReversed = prefs.getBoolean("auto_play_reversed", false)
                        when {
                            !autoNextOn -> {
                                prefs.edit {
                                    putBoolean("auto_play_next", true)
                                    putBoolean("auto_play_reversed", false)
                                    putBoolean("repeat_mode", false)
                                }
                            }
                            !autoReversed -> {
                                prefs.edit { putBoolean("auto_play_reversed", true) }
                            }
                            else -> {
                                prefs.edit {
                                    putBoolean("auto_play_next", false)
                                    putBoolean("auto_play_reversed", false)
                                }
                            }
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

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val root = MediaItem.Builder()
                    .setMediaId(BROWSE_ROOT)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .setTitle(getString(R.string.app_name))
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(root, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val children: List<MediaItem> = when {
                    parentId == BROWSE_ROOT -> {
                        listOf(
                            buildBrowseFolderItem(BROWSE_ALL_SURAHS, getString(R.string.browse_all_surahs)),
                            buildBrowseFolderItem(BROWSE_BY_JUZ, getString(R.string.browse_by_juz))
                        )
                    }
                    parentId == BROWSE_ALL_SURAHS -> {
                        SurahRepository.surahs.map { buildBrowsableSurahItem(it) }
                    }
                    parentId == BROWSE_BY_JUZ -> {
                        (1..30).map { juz ->
                            buildBrowseFolderItem(
                                "$BROWSE_JUZ_PREFIX$juz",
                                getString(R.string.juz_label, juz)
                            )
                        }
                    }
                    parentId.startsWith(BROWSE_JUZ_PREFIX) -> {
                        val juz = parentId.removePrefix(BROWSE_JUZ_PREFIX).toIntOrNull()
                        if (juz != null) {
                            val surahs = SurahRepository.surahs
                            surahs.filter { surah ->
                                val nextSurahJuz = surahs.getOrNull(surah.id)?.juz ?: 31
                                juz in surah.juz until nextSurahJuz
                            }.map { buildBrowsableSurahItem(it) }
                        } else emptyList()
                    }
                    else -> emptyList()
                }
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val surahId = mediaId.toIntOrNull()
                if (surahId != null) {
                    val surah = SurahRepository.surahs.find { it.id == surahId }
                    if (surah != null) {
                        return Futures.immediateFuture(LibraryResult.ofItem(buildBrowsableSurahItem(surah), null))
                    }
                }
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                val firstItem = mediaItems.firstOrNull() ?: return Futures.immediateFuture(emptyList())
                val surahId = firstItem.mediaId.toIntOrNull()
                val surah = if (surahId != null) SurahRepository.surahs.find { it.id == surahId } else null

                if (surah == null) return Futures.immediateFuture(emptyList())

                if (isSurahCached(surah)) {
                    return Futures.immediateFuture(listOf(buildMediaItem(surah)))
                }

                downloadAndPlay(mediaSession.player, surah)
                return Futures.immediateFuture(emptyList())
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                val localizedNames = resources.getStringArray(R.array.surah_names)
                val results = SurahRepository.surahs.filter { surah ->
                    val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
                    name.contains(query, ignoreCase = true) ||
                        surah.name.contains(query, ignoreCase = true) ||
                        surah.id.toString() == query
                }.map { buildBrowsableSurahItem(it) }
                session.notifySearchResultChanged(browser, query, results.size, params)
                return Futures.immediateFuture(LibraryResult.ofVoid(params))
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val localizedNames = resources.getStringArray(R.array.surah_names)
                val results = SurahRepository.surahs.filter { surah ->
                    val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
                    name.contains(query, ignoreCase = true) ||
                        surah.name.contains(query, ignoreCase = true) ||
                        surah.id.toString() == query
                }.map { buildBrowsableSurahItem(it) }
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(results), params))
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

        _mediaSession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = try {
        super.onStartCommand(intent, flags, startId)
    } catch (_: Exception) {
        START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = _mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = _mediaSession

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
