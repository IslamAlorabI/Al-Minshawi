package ialorabi.ms.alminshawi.telawat.player

import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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
import java.io.ByteArrayOutputStream
import java.io.File
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var _artworkData: ByteArray? = null

    companion object {
        var instance: PlaybackService? = null
            private set
        
        var cache: SimpleCache? = null
            private set
        private const val CACHE_SIZE = 500L * 1024 * 1024

        const val ACTION_REPEAT = "action_repeat"
        const val ACTION_AUTO_NEXT = "action_auto_next"
        const val ACTION_PREV_SURAH = "action_prev_surah"
        const val ACTION_NEXT_SURAH = "action_next_surah"

        fun getCacheSize(context: Context): Long {
            return cache?.cacheSpace ?: getFolderSize(File(context.cacheDir, "audio_cache"))
        }

        fun clearCache(context: Context) {
            val player = instance?.mediaSession?.player
            player?.stop()
            player?.clearMediaItems()
            cache?.keys?.toSet()?.forEach { cache?.removeResource(it) }
            val cacheFolder = File(context.cacheDir, "audio_cache")
            if (cache == null && cacheFolder.exists()) {
                cacheFolder.deleteRecursively()
            }
        }

        fun getCachedSurahs(): List<Surah> {
            val cachedKeys = cache?.keys ?: emptySet()
            return SurahRepository.surahs.filter { surah -> cachedKeys.contains(surah.url) }
        }

        fun removeSurahCache(surah: Surah) {
            val player = instance?.mediaSession?.player
            if (player?.currentMediaItem?.mediaId == surah.id.toString()) {
                player.stop()
                player.clearMediaItems()
            }
            cache?.removeResource(surah.url)
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
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val primaryContainer = if (isDark) {
            getColor(android.R.color.system_accent1_700)
        } else {
            getColor(android.R.color.system_accent1_100)
        }
        val primary = if (isDark) {
            getColor(android.R.color.system_accent1_200)
        } else {
            getColor(android.R.color.system_accent1_600)
        }
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(primaryContainer)
        val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.player_logo)
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

    private fun playNextSurah(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        val surahs = SurahRepository.surahs
        if (currentId < surahs.size) {
            val nextSurah = surahs[currentId]
            player.stop()
            player.setMediaItem(buildMediaItem(nextSurah))
            player.prepare()
            player.play()
        }
    }

    private fun playPreviousSurah(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        val surahs = SurahRepository.surahs
        if (currentId > 1) {
            val prevSurah = surahs[currentId - 2]
            player.stop()
            player.setMediaItem(buildMediaItem(prevSurah))
            player.prepare()
            player.play()
        }
    }

    @androidx.media3.common.util.UnstableApi
    private fun buildCustomLayout(): List<CommandButton> {
        val repeatOn = prefs.getBoolean("repeat_mode", false)
        val autoNextOn = prefs.getBoolean("auto_play_next", true)

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

    @androidx.media3.common.util.UnstableApi
    fun refreshCustomLayout() {
        mediaSession?.let { session ->
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
            val cacheDir = File(cacheDir, "audio_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(this)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

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
                        prefs.edit().putBoolean("repeat_mode", newState).apply()
                        if (newState) {
                            prefs.edit().putBoolean("auto_play_next", false).apply()
                        }
                        refreshCustomLayout()
                    }
                    ACTION_AUTO_NEXT -> {
                        val newState = !prefs.getBoolean("auto_play_next", true)
                        prefs.edit().putBoolean("auto_play_next", newState).apply()
                        if (newState) {
                            prefs.edit().putBoolean("repeat_mode", false).apply()
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

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        instance = null
        super.onDestroy()
    }
}
