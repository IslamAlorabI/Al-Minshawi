package ialorabi.ms.alminshawi.telawat.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import android.content.Context
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        var cache: SimpleCache? = null
            private set
        private const val CACHE_SIZE = 500L * 1024 * 1024

        fun getCacheSize(context: Context): Long {
            return cache?.cacheSpace ?: getFolderSize(File(context.cacheDir, "audio_cache"))
        }

        fun clearCache(context: Context) {
            cache?.keys?.toSet()?.forEach { cache?.removeResource(it) }
            // If cache was null but exists on disk, we can delete the folder contents
            val cacheFolder = File(context.cacheDir, "audio_cache")
            if (cache == null && cacheFolder.exists()) {
                cacheFolder.deleteRecursively()
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

    override fun onCreate() {
        super.onCreate()

        if (cache == null) {
            val cacheDir = File(cacheDir, "audio_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(this)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady || player?.mediaItemCount == 0) {
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
        super.onDestroy()
    }
}
