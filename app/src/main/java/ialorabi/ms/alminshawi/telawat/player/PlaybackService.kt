package ialorabi.ms.alminshawi.telawat.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import java.io.File
import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        var instance: PlaybackService? = null
            private set
        
        var cache: SimpleCache? = null
            private set
        private const val CACHE_SIZE = 500L * 1024 * 1024

        const val ACTION_REPEAT = "action_repeat"
        const val ACTION_AUTO_NEXT = "action_auto_next"
        const val ACTION_SEEK_FORWARD = "action_seek_forward"
        const val ACTION_SEEK_BACKWARD = "action_seek_backward"

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

    private fun buildCustomLayout(): List<CommandButton> {
        val repeatOn = prefs.getBoolean("repeat_mode", false)
        val autoNextOn = prefs.getBoolean("auto_play_next", true)

        return listOf(
            CommandButton.Builder(CommandButton.ICON_REPEAT_ONE)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.repeat_surah))
                .setEnabled(repeatOn)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setSessionCommand(SessionCommand(ACTION_SEEK_BACKWARD, Bundle.EMPTY))
                .setDisplayName(getString(R.string.rewind))
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setSessionCommand(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .setDisplayName(getString(R.string.forward))
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(SessionCommand(ACTION_AUTO_NEXT, Bundle.EMPTY))
                .setDisplayName(getString(R.string.auto_play_next))
                .setEnabled(autoNextOn)
                .build()
        )
    }

    fun refreshCustomLayout() {
        mediaSession?.let { session ->
            val layout = buildCustomLayout()
            session.setCustomLayout(layout)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

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

        val player = ExoPlayer.Builder(this)
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

        val customCommands = listOf(ACTION_REPEAT, ACTION_AUTO_NEXT, ACTION_SEEK_FORWARD, ACTION_SEEK_BACKWARD)
            .map { SessionCommand(it, Bundle.EMPTY) }

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                customCommands.forEach { sessionCommands.add(it) }
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands.build())
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
                    ACTION_SEEK_FORWARD -> {
                        val p = session.player
                        val nextPos = (p.currentPosition + 30_000).coerceAtMost(p.duration)
                        p.seekTo(nextPos)
                    }
                    ACTION_SEEK_BACKWARD -> {
                        val p = session.player
                        val prevPos = (p.currentPosition - 10_000).coerceAtLeast(0L)
                        p.seekTo(prevPos)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
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
