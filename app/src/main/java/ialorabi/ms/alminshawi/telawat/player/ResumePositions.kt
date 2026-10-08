package ialorabi.ms.alminshawi.telawat.player

import android.content.SharedPreferences
import androidx.core.content.edit

// Remembers where the listener stopped in each surah
object ResumePositions {
    // Positions this close to the start or the end are not worth resuming
    private const val MIN_RESUME_MS = 10_000L

    private fun key(surahId: Int) = "resume_pos_$surahId"

    fun save(prefs: SharedPreferences, surahId: Int, positionMs: Long, durationMs: Long) {
        val nearEnd = durationMs > 0 && positionMs > durationMs - MIN_RESUME_MS
        prefs.edit {
            if (positionMs < MIN_RESUME_MS || nearEnd) remove(key(surahId)) else putLong(key(surahId), positionMs)
        }
    }

    fun get(prefs: SharedPreferences, surahId: Int): Long = prefs.getLong(key(surahId), 0L)

    fun clear(prefs: SharedPreferences, surahId: Int) {
        prefs.edit { remove(key(surahId)) }
    }
}
