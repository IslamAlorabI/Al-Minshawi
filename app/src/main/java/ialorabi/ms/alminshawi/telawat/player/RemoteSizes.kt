package ialorabi.ms.alminshawi.telawat.player

import android.net.Uri
import ialorabi.ms.alminshawi.telawat.data.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

// File sizes on the server, read with HEAD requests before a bulk download
object RemoteSizes {
    private const val MAX_PARALLEL_REQUESTS = 6
    private const val TIMEOUT_MS = 15_000

    private val knownSizes = ConcurrentHashMap<Int, Long>()

    // Null when any size could not be read
    suspend fun totalBytes(surahs: List<Surah>): Long? = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
        val sizes = coroutineScope {
            surahs.map { surah -> async { semaphore.withPermit { sizeOf(surah) } } }.awaitAll()
        }
        if (sizes.any { it == null }) null else sizes.sumOf { it ?: 0L }
    }

    private fun sizeOf(surah: Surah): Long? {
        knownSizes[surah.id]?.let { return it }
        return try {
            val parsed = Uri.parse(surah.url)
            // The multi-argument URI constructor percent-encodes the Arabic file name
            val url = URI(parsed.scheme, parsed.host, parsed.path, null).toURL()
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                if (connection.responseCode in 200..299) {
                    connection.contentLengthLong.takeIf { it > 0 }?.also { knownSizes[surah.id] = it }
                } else null
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
