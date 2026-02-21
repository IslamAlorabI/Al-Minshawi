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
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import java.io.File
import android.content.Context
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        var instance: PlaybackService? = null
            private set
        
        var cache: SimpleCache? = null
            private set
        private const val CACHE_SIZE = 500L * 1024 * 1024

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
        instance = null
        super.onDestroy()
    }
}
