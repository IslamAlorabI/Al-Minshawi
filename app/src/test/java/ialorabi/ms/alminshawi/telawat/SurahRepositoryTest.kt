package ialorabi.ms.alminshawi.telawat

import ialorabi.ms.alminshawi.telawat.data.RevelationType
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurahRepositoryTest {
    private val surahs = SurahRepository.surahs

    private fun surahsInJuz(juz: Int) =
        surahs.filter { juz in SurahRepository.juzRange(it) }.map { it.id }

    @Test
    fun has114SurahsWithSequentialIds() {
        assertEquals(114, surahs.size)
        assertEquals((1..114).toList(), surahs.map { it.id })
    }

    @Test
    fun revelationTypeCountsMatchMushaf() {
        assertEquals(86, surahs.count { it.revelationType == RevelationType.MAKKI })
        assertEquals(28, surahs.count { it.revelationType == RevelationType.MADANI })
    }

    @Test
    fun startJuzNeverDecreases() {
        surahs.zipWithNext().forEach { (a, b) ->
            assertTrue("Surah ${b.id} starts before surah ${a.id}", b.juz >= a.juz)
        }
    }

    @Test
    fun juzRangeCoversKnownSurahs() {
        assertEquals(1..1, SurahRepository.juzRange(surahs[0]))     // Al-Fatiha
        assertEquals(1..3, SurahRepository.juzRange(surahs[1]))     // Al-Baqarah
        assertEquals(11..11, SurahRepository.juzRange(surahs[9]))   // Yunus
        assertEquals(13..13, SurahRepository.juzRange(surahs[13]))  // Ibrahim, ends where juz 14 starts
        assertEquals(26..27, SurahRepository.juzRange(surahs[50]))  // Adh-Dhariyat
        assertEquals(29..29, SurahRepository.juzRange(surahs[76]))  // Al-Mursalat, ends where juz 30 starts
        assertEquals(30..30, SurahRepository.juzRange(surahs[113])) // An-Nas
    }

    @Test
    fun everyJuzHasAtLeastOneSurah() {
        (1..30).forEach { juz ->
            assertTrue("Juz $juz is empty", surahsInJuz(juz).isNotEmpty())
        }
    }

    @Test
    fun juz30ListsAnNabaThroughAnNas() {
        assertEquals((78..114).toList(), surahsInJuz(30))
    }

    @Test
    fun urlsUsePaddedIdAndAreUnique() {
        surahs.forEach { surah ->
            val prefix = "https://archive.org/download/002_20260220/${surah.id.toString().padStart(3, '0')}-"
            assertTrue(surah.url, surah.url.startsWith(prefix))
            assertTrue(surah.url, surah.url.endsWith(".mp3"))
        }
        assertEquals(114, surahs.map { it.url }.toSet().size)
    }
}
