package ialorabi.ms.alminshawi.telawat

import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import ialorabi.ms.alminshawi.telawat.data.SurahSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurahSearchTest {
    private fun matchingIds(query: String) =
        SurahRepository.surahs.filter { SurahSearch.matches(it, it.name, query) }.map { it.id }

    @Test
    fun hamzaVariantsMatch() {
        assertTrue(17 in matchingIds("الاسراء"))
        assertTrue(3 in matchingIds("ال عمران"))
    }

    @Test
    fun taaMarbutaAndSurahPrefixAreIgnored() {
        assertTrue(2 in matchingIds("سورة البقره"))
        assertTrue(2 in matchingIds("بقرة"))
    }

    @Test
    fun diacriticsAreIgnored() {
        assertTrue(18 in matchingIds("الْكَهْف"))
    }

    @Test
    fun arabicDigitsMatchSurahNumber() {
        assertTrue(18 in matchingIds("١٨"))
        assertTrue(18 in matchingIds("18"))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertEquals(114, matchingIds("  ").size)
    }

    @Test
    fun unrelatedQueryMatchesNothing() {
        assertFalse(matchingIds("xyz").isNotEmpty())
    }
}
