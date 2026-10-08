package ialorabi.ms.alminshawi.telawat.player

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

// Single owner of the downloaded audio. Lives for the whole process so Settings
// can use it even when PlaybackService is not running.
@androidx.media3.common.util.UnstableApi
object AudioCache {
    private const val PREFS_NAME = "player_prefs"
    private const val KEY_DOWNLOADED = "downloaded_surahs"
    private const val CACHE_DIR = "audio_cache"

    sealed interface Change {
        data class Removed(val surahId: Int) : Change
        data object Cleared : Change
    }

    private lateinit var appContext: Context
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val cache: SimpleCache by lazy { createCache() }

    private val _downloadedIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadedIds: StateFlow<Set<Int>> = _downloadedIds.asStateFlow()

    // Called before files are deleted, so the player can let go of them first
    private val listeners = CopyOnWriteArraySet<(Change) -> Unit>()

    fun init(context: Context) {
        appContext = context.applicationContext
        _downloadedIds.value = readDownloadedIds()
    }

    fun addListener(listener: (Change) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Change) -> Unit) {
        listeners.remove(listener)
    }

    fun isSurahCached(surah: Surah): Boolean =
        surah.id in _downloadedIds.value && cache.getCachedSpans(surah.url).isNotEmpty()

    fun getCachedSurahs(): List<Surah> = SurahRepository.surahs.filter { isSurahCached(it) }

    fun getCacheSize(): Long = cache.cacheSpace

    fun freeSpaceBytes(): Long = appContext.filesDir.usableSpace

    fun markDownloaded(surahId: Int) = updateDownloadedIds { it + surahId }

    fun unmarkDownloaded(surahId: Int) = updateDownloadedIds { it - surahId }

    suspend fun remove(surah: Surah) {
        listeners.forEach { it(Change.Removed(surah.id)) }
        withContext(Dispatchers.IO) { cache.removeResource(surah.url) }
        unmarkDownloaded(surah.id)
    }

    suspend fun clearAll() {
        listeners.forEach { it(Change.Cleared) }
        withContext(Dispatchers.IO) {
            cache.keys.forEach {
                try {
                    cache.removeResource(it)
                } catch (_: Exception) {}
            }
        }
        updateDownloadedIds { emptySet() }
    }

    suspend fun saveToDownloads(surah: Surah, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val spans = cache.getCachedSpans(surah.url)
        if (spans.isEmpty()) return@withContext false

        try {
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
                appContext,
                arrayOf(outputFile.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    // Makes the downloaded list match what is really in the cache
    suspend fun syncWithCache() = withContext(Dispatchers.IO) {
        val cachedKeys = cache.keys
        val fullyCached = SurahRepository.surahs.filter { surah ->
            val length = cache.getContentMetadata(surah.url).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            length > 0 && cache.getCachedBytes(surah.url, 0, length) == length
        }.map { it.id }
        updateDownloadedIds { current ->
            (current + fullyCached).filter { id ->
                val surah = SurahRepository.surahs.find { it.id == id }
                surah != null && cachedKeys.contains(surah.url)
            }.toSet()
        }
    }

    private fun updateDownloadedIds(transform: (Set<Int>) -> Set<Int>) {
        val newIds = _downloadedIds.updateAndGet(transform)
        prefs.edit { putStringSet(KEY_DOWNLOADED, newIds.map { it.toString() }.toSet()) }
    }

    private fun readDownloadedIds(): Set<Int> =
        (prefs.getStringSet(KEY_DOWNLOADED, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    private fun createCache(): SimpleCache {
        val oldCacheDir = File(appContext.cacheDir, CACHE_DIR)
        val newCacheDir = File(appContext.filesDir, CACHE_DIR)
        if (oldCacheDir.exists() && !newCacheDir.exists()) {
            oldCacheDir.renameTo(newCacheDir)
        } else if (oldCacheDir.exists() && newCacheDir.exists()) {
            oldCacheDir.deleteRecursively()
        }
        return SimpleCache(newCacheDir, NoOpCacheEvictor(), StandaloneDatabaseProvider(appContext))
    }
}
