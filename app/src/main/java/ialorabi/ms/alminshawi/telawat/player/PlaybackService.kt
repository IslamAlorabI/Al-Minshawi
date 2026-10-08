package ialorabi.ms.alminshawi.telawat.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import ialorabi.ms.alminshawi.telawat.data.SurahSearch
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaLibraryService() {
    private var _mediaSession: MediaLibrarySession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downloadJob: Job? = null
    private var currentDownloadingSurahId: Int? = null
    private var isSkipping = false
    private var isAutoPlayTransitioning = false
    private var playDownloadOriginalItem: MediaItem? = null
    private var playDownloadOriginalPosition = 0L
    private val manualDownloadJobs = mutableMapOf<Int, Job>()
    private val cancelledManualDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val downloadQueue = mutableListOf<Surah>()

    companion object {
        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val MAX_DOWNLOAD_RETRIES = 5
        private const val HTTP_TIMEOUT_MS = 30_000

        const val ACTION_REPEAT = "action_repeat"
        const val ACTION_AUTO_NEXT = "action_auto_next"
        const val ACTION_PREV_SURAH = "action_prev_surah"
        const val ACTION_NEXT_SURAH = "action_next_surah"

        // Sent only by this app's own MediaController
        const val ACTION_PLAY_SURAH = "action_play_surah"
        const val ACTION_DOWNLOAD_SURAH = "action_download_surah"
        const val ACTION_DOWNLOAD_JUZ = "action_download_juz"
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        const val ACTION_CANCEL_ALL_DOWNLOADS = "action_cancel_all_downloads"
        const val ACTION_REFRESH_METADATA = "action_refresh_metadata"
        const val EXTRA_SURAH_ID = "surah_id"
        const val EXTRA_JUZ = "juz"

        private const val BROWSE_ROOT = "root"
        private const val BROWSE_ALL_SURAHS = "all_surahs"
        private const val BROWSE_BY_JUZ = "by_juz"
        private const val BROWSE_JUZ_PREFIX = "juz_"
    }

    private val prefs by lazy { getSharedPreferences("player_prefs", MODE_PRIVATE) }

    private fun getArtworkUri(): android.net.Uri? {
        return ArtworkHelper.getArtworkUri(this)
    }

    private fun minutesLeft(remainingMs: Long): Long = (remainingMs + 59_999) / 60_000

    private fun sleepTimerLabel(): String? = when {
        SleepTimer.endOfSurah.value -> getString(R.string.sleep_timer_notification_end_of_surah)
        SleepTimer.remainingMs.value > 0 -> getString(R.string.sleep_timer_notification_minutes, minutesLeft(SleepTimer.remainingMs.value).toInt())
        else -> null
    }

    private fun buildMediaItem(surah: Surah): MediaItem {
        val localizedNames = resources.getStringArray(R.array.surah_names)
        val name = localizedNames.getOrElse(surah.id - 1) { surah.name }
        val prefix = getString(R.string.surah_prefix)
        val title = "$prefix $name (${surah.id})"
        val sheikh = getString(R.string.sheikh_name)
        val artist = sleepTimerLabel()?.let { "$sheikh · $it" } ?: sheikh

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
        val downloadStatus = if (AudioCache.isSurahCached(surah)) {
            "✓ ${getString(R.string.filter_downloaded)}"
        } else {
            "✕ ${getString(R.string.filter_not_downloaded)}"
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

    private fun saveResumePosition(player: Player) {
        val surahId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        // While downloading, the current item only shows progress; its position means nothing
        if (currentDownloadingSurahId != null || player.playbackState == Player.STATE_ENDED) return
        ResumePositions.save(prefs, surahId, player.currentPosition, player.duration.coerceAtLeast(0L))
    }

    private fun startSurah(player: Player, surah: Surah, resume: Boolean) {
        val startAt = if (resume) ResumePositions.get(prefs, surah.id) else 0L
        val newItem = buildMediaItem(surah)
        if (player.mediaItemCount > 0) {
            player.replaceMediaItem(player.currentMediaItemIndex, newItem)
            player.seekTo(startAt)
        } else {
            player.setMediaItem(newItem, startAt)
        }
        player.prepare()
        player.play()
        if (startAt > 0) PlaybackStore.emitResumed(startAt)
    }

    // resume = false starts from the beginning (used by auto-play)
    fun downloadAndPlay(player: Player, surah: Surah, resume: Boolean = true) {
        saveResumePosition(player)
        val previousDownloadingSurahId = currentDownloadingSurahId
        downloadJob?.cancel()
        if (previousDownloadingSurahId != null) {
            PlaybackStore.removeDownload(previousDownloadingSurahId)
            PlaybackStore.setPlayDownload(null)
        }
        currentDownloadingSurahId = null

        if (AudioCache.isSurahCached(surah)) {
            playDownloadOriginalItem = null
            startSurah(player, surah, resume)
            isSkipping = false
            return
        }

        try {
            AudioCache.cache.removeResource(surah.url)
        } catch (_: Exception) {}

        val downloadingTitle = getLocalizedSurahName(surah)
        val downloadingArtist = getString(R.string.widget_downloading_status)

        // When one play download replaces another, keep the item from before the first one
        if (previousDownloadingSurahId == null) {
            playDownloadOriginalItem = if (player.mediaItemCount > 0) player.currentMediaItem else null
            playDownloadOriginalPosition = player.currentPosition
        }

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
        PlaybackStore.setPlayDownload(surah.id)
        PlaybackStore.setDownloadProgress(surah.id, 0.001f)

        downloadJob = serviceScope.launch {
            val success = withContext(Dispatchers.IO) {
                val c = AudioCache.cache
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
                        PlaybackStore.setDownloadProgress(surah.id, progress)
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
                AudioCache.markDownloaded(surah.id)
                _mediaSession?.let { session ->
                    session.notifyChildrenChanged(BROWSE_ALL_SURAHS, SurahRepository.surahs.size, null)
                    session.notifyChildrenChanged("$BROWSE_JUZ_PREFIX${surah.juz}", SurahRepository.surahs.count { it.juz == surah.juz }, null)
                }
            }
            PlaybackStore.removeDownload(surah.id)
            PlaybackStore.setPlayDownload(null)
            if (success) {
                playDownloadOriginalItem = null
                startSurah(player, surah, resume)
            } else {
                restoreItemBeforePlayDownload(player)
                PlaybackStore.emit(PlaybackStore.Event.DOWNLOAD_FAILED)
            }
            isSkipping = false
        }
    }


    private fun restoreItemBeforePlayDownload(player: Player) {
        val original = playDownloadOriginalItem
        playDownloadOriginalItem = null
        if (player.mediaItemCount == 0) return
        if (original != null) {
            player.replaceMediaItem(player.currentMediaItemIndex, original)
            player.seekTo(playDownloadOriginalPosition)
        } else {
            player.clearMediaItems()
        }
    }

    private fun processNextInQueue() {
        if (downloadQueue.isNotEmpty() && manualDownloadJobs.size < MAX_PARALLEL_DOWNLOADS) {
            val nextSurah = downloadQueue.removeAt(0)
            startManualDownload(nextSurah)
        }
    }

    // A whole juz is queued at once, so it skips the 10-download limit for single taps
    private fun downloadJuz(juz: Int) {
        SurahRepository.surahs
            .filter { juz in SurahRepository.juzRange(it) }
            .filter { !AudioCache.isSurahCached(it) && it.id != currentDownloadingSurahId }
            .forEach { downloadSurahInBackground(it, enforceQueueLimit = false) }
    }

    fun downloadSurahInBackground(surah: Surah, enforceQueueLimit: Boolean = true) {
        if (manualDownloadJobs.containsKey(surah.id) || downloadQueue.any { it.id == surah.id }) return
        
        val totalCount = manualDownloadJobs.size + downloadQueue.size
        if (enforceQueueLimit && totalCount >= 10) {
            PlaybackStore.emit(PlaybackStore.Event.QUEUE_LIMIT_REACHED)
            return
        }
        
        if (manualDownloadJobs.size >= MAX_PARALLEL_DOWNLOADS) {
            downloadQueue.add(surah)
            PlaybackStore.setDownloadProgress(surah.id, 0f)
            return
        }
        
        startManualDownload(surah)
    }

    private fun startManualDownload(surah: Surah) {
        val c = AudioCache.cache
        manualDownloadJobs[surah.id] = serviceScope.launch {
            PlaybackStore.setDownloadProgress(surah.id, 0.001f)
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
                        PlaybackStore.setDownloadProgress(surah.id, safeProgress)
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
                    AudioCache.markDownloaded(surah.id)
                    _mediaSession?.let { session ->
                        session.notifyChildrenChanged(BROWSE_ALL_SURAHS, SurahRepository.surahs.size, null)
                        session.notifyChildrenChanged("$BROWSE_JUZ_PREFIX${surah.juz}", SurahRepository.surahs.count { it.juz == surah.juz }, null)
                    }
                }
                PlaybackStore.removeDownload(surah.id)
                if (!success) {
                    PlaybackStore.emit(PlaybackStore.Event.DOWNLOAD_FAILED)
                }
            }
            processNextInQueue()
        }
    }

    fun cancelManualDownload(surahId: Int) {
        val queuedIndex = downloadQueue.indexOfFirst { it.id == surahId }
        if (queuedIndex != -1) {
            downloadQueue.removeAt(queuedIndex)
            PlaybackStore.removeDownload(surahId)
            return
        }

        val job = manualDownloadJobs.remove(surahId)
        if (job != null) {
            cancelledManualDownloads.add(surahId)
            val surah = SurahRepository.surahs.find { it.id == surahId }
            serviceScope.launch {
                job.cancelAndJoin()
                cancelledManualDownloads.remove(surahId)
                if (surah != null) {
                    try {
                        withContext(Dispatchers.IO) { AudioCache.cache.removeResource(surah.url) }
                    } catch (_: Exception) {}
                    AudioCache.unmarkDownloaded(surah.id)
                }
                PlaybackStore.removeDownload(surahId)
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
        _mediaSession?.player?.let { restoreItemBeforePlayDownload(it) }
        PlaybackStore.removeDownload(surahId)
        PlaybackStore.setPlayDownload(null)
        val surah = SurahRepository.surahs.find { it.id == surahId }
        serviceScope.launch {
            job?.cancelAndJoin()
            // A progress update can land between the cancel and the join
            PlaybackStore.removeDownload(surahId)
            if (surah != null) withContext(Dispatchers.IO) { AudioCache.cache.removeResource(surah.url) }
        }
    }

    fun cancelAllDownloads() {
        val queuedIds = downloadQueue.map { it.id }
        downloadQueue.clear()
        queuedIds.forEach { PlaybackStore.removeDownload(it) }

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
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        skipToSurah(player, SurahRepository.surahs.getOrNull(currentId))
    }

    private fun playPreviousSurah(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        skipToSurah(player, SurahRepository.surahs.getOrNull(currentId - 2))
    }

    // Same as the in-app buttons: download first if needed, then play
    private fun skipToSurah(player: Player, target: Surah?) {
        if (target == null || isSkipping) return
        if (manualDownloadJobs.containsKey(target.id) || downloadQueue.any { it.id == target.id }) return
        isSkipping = true
        downloadAndPlay(player, target)
    }

    fun refreshLanguage() {
        refreshCustomLayout()
        // Rebuilding the item now would wipe the "Downloading…" metadata
        if (currentDownloadingSurahId != null) return
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

        return listOf(
            CommandButton.Builder(repeatIcon)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.repeat_surah))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(autoPlayIconRes)
                .setSessionCommand(SessionCommand(ACTION_AUTO_NEXT, Bundle.EMPTY))
                .setDisplayName(getString(autoPlayNameRes))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_OVERFLOW)
                .build()
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
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(autoNextIcon)
                .setSessionCommand(SessionCommand(ACTION_AUTO_NEXT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.auto_play_next))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        )
    }

    private fun buildSessionCommands(controller: MediaSession.ControllerInfo): SessionCommands {
        val builder = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        listOf(ACTION_REPEAT, ACTION_AUTO_NEXT, ACTION_PREV_SURAH, ACTION_NEXT_SURAH)
            .forEach { builder.add(SessionCommand(it, Bundle.EMPTY)) }
        if (controller.packageName == packageName) {
            listOf(ACTION_PLAY_SURAH, ACTION_DOWNLOAD_SURAH, ACTION_DOWNLOAD_JUZ, ACTION_CANCEL_DOWNLOAD, ACTION_CANCEL_ALL_DOWNLOADS, ACTION_REFRESH_METADATA)
                .forEach { builder.add(SessionCommand(it, Bundle.EMPTY)) }
        }
        return builder.build()
    }

    private fun buildPlayerCommands(player: Player): Player.Commands {
        val builder = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        if (!player.hasNextMediaItem()) {
            builder.removeAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (!player.hasPreviousMediaItem()) {
            builder.removeAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return builder.build()
    }

    private fun refreshAvailableCommands() {
        _mediaSession?.let { session ->
            val playerCommands = buildPlayerCommands(session.player)
            for (controller in session.connectedControllers) {
                session.setAvailableCommands(controller, buildSessionCommands(controller), playerCommands)
            }
        }
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

    private fun surahFromArgs(args: Bundle): Surah? {
        val surahId = args.getInt(EXTRA_SURAH_ID, -1)
        return SurahRepository.surahs.find { it.id == surahId }
    }

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "repeat_mode" || key == "auto_play_next" || key == "auto_play_reversed") {
            refreshCustomLayout()
        }
    }

    // Let go of files before Settings deletes them
    private val cacheListener: (AudioCache.Change) -> Unit = { change ->
        val player = _mediaSession?.player
        when (change) {
            is AudioCache.Change.Cleared -> {
                cancelAllDownloads()
                player?.stop()
                player?.clearMediaItems()
            }
            is AudioCache.Change.Removed -> {
                if (player?.currentMediaItem?.mediaId == change.surahId.toString()) {
                    player.stop()
                    player.clearMediaItems()
                }
            }
        }
        _mediaSession?.notifyChildrenChanged(BROWSE_ALL_SURAHS, SurahRepository.surahs.size, null)
    }

    override fun onCreate() {
        super.onCreate()
        AudioCache.addListener(cacheListener)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        serviceScope.launch { AudioCache.syncWithCache() }
        serviceScope.launch {
            SleepTimer.expired.collect {
                _mediaSession?.player?.pause()
                SleepTimer.restoreVolume()
            }
        }
        serviceScope.launch {
            SleepTimer.volume.collect { _mediaSession?.player?.volume = it }
        }
        // Show the timer in the notification; only rebuild when the shown minute changes
        serviceScope.launch {
            combine(SleepTimer.remainingMs, SleepTimer.endOfSurah) { remaining, endOfSurah ->
                minutesLeft(remaining) to endOfSurah
            }.distinctUntilChanged().drop(1).collect { refreshLanguage() }
        }

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.player_logo)
        setMediaNotificationProvider(notificationProvider)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCache.cache)
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
                // Only IO (2xxx) and parsing (3xxx) errors mean the cached file itself is bad
                val isBrokenFile = error.errorCode in 2000..3999
                val mediaId = exoPlayer.currentMediaItem?.mediaId?.toIntOrNull()
                if (isBrokenFile && mediaId != null) {
                    val surah = SurahRepository.surahs.find { it.id == mediaId }
                    if (surah != null) {
                        AudioCache.cache.removeResource(surah.url)
                        AudioCache.unmarkDownloaded(surah.id)
                    }
                }
                PlaybackStore.emit(PlaybackStore.Event.PLAYBACK_ERROR)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveResumePosition(exoPlayer)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isAutoPlayTransitioning = false
                }
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.currentMediaItem?.mediaId?.toIntOrNull()?.let { ResumePositions.clear(prefs, it) }
                }
                if (playbackState == Player.STATE_ENDED && !isAutoPlayTransitioning && !PlaybackStore.isUserSeeking) {
                    if (SleepTimer.consumeEndOfSurah()) return
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
                            if (currentId > 1) downloadAndPlay(exoPlayer, surahs[currentId - 2], resume = false)
                            else isAutoPlayTransitioning = false
                        } else {
                            if (currentId < surahs.size) downloadAndPlay(exoPlayer, surahs[currentId], resume = false)
                            else isAutoPlayTransitioning = false
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshCustomLayout()
                refreshAvailableCommands()
            }
        })

        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean {
                val currentId = currentMediaItem?.mediaId?.toIntOrNull() ?: return false
                return currentId < SurahRepository.surahs.size
            }

            override fun hasPreviousMediaItem(): Boolean {
                val currentId = currentMediaItem?.mediaId?.toIntOrNull() ?: return false
                return currentId > 1
            }

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


        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val layout = if (session.isMediaNotificationController(controller)) {
                    buildCustomLayout()
                } else {
                    buildAutoCustomLayout()
                }

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(buildSessionCommands(controller))
                    .setAvailablePlayerCommands(buildPlayerCommands(session.player))
                    .setCustomLayout(layout)
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
                    ACTION_PLAY_SURAH -> {
                        surahFromArgs(args)?.let { surah ->
                            val isManuallyDownloading = manualDownloadJobs.containsKey(surah.id) ||
                                downloadQueue.any { it.id == surah.id }
                            if (!isManuallyDownloading) downloadAndPlay(session.player, surah)
                        }
                    }
                    ACTION_DOWNLOAD_SURAH -> {
                        surahFromArgs(args)?.let { downloadSurahInBackground(it) }
                    }
                    ACTION_DOWNLOAD_JUZ -> downloadJuz(args.getInt(EXTRA_JUZ, -1))
                    ACTION_CANCEL_DOWNLOAD -> {
                        val surahId = args.getInt(EXTRA_SURAH_ID, -1)
                        if (surahId == currentDownloadingSurahId) cancelPlayDownload() else cancelManualDownload(surahId)
                    }
                    ACTION_CANCEL_ALL_DOWNLOADS -> cancelAllDownloads()
                    ACTION_REFRESH_METADATA -> refreshLanguage()
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
                            SurahRepository.surahs
                                .filter { juz in SurahRepository.juzRange(it) }
                                .map { buildBrowsableSurahItem(it) }
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

                if (AudioCache.isSurahCached(surah)) {
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
                    SurahSearch.matches(surah, localizedNames.getOrElse(surah.id - 1) { surah.name }, query)
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
                    SurahSearch.matches(surah, localizedNames.getOrElse(surah.id - 1) { surah.name }, query)
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ArtworkHelper.invalidate()
        refreshLanguage()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = _mediaSession

    override fun onDestroy() {
        SleepTimer.set(null)
        downloadJob?.cancel()
        manualDownloadJobs.values.forEach { it.cancel() }
        manualDownloadJobs.clear()
        AudioCache.removeListener(cacheListener)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        PlaybackStore.reset()
        _mediaSession?.run {
            player.release()
            release()
            _mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
