package ialorabi.ms.alminshawi.telawat.data

object SurahSearch {
    // Harakat, superscript alef and tatweel
    private val diacritics = Regex("[\\u064B-\\u0652\\u0670\\u0640]")
    private val surahPrefix = Regex("^(سوره|surah|sura)\\s+")

    // Folds spelling variants so "الاسراء" finds "الإسراء" and "سورة البقره" finds "البقرة"
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in diacritics.replace(text, "")) {
            builder.append(
                when (char) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ة' -> 'ه'
                    'ى' -> 'ي'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    in '٠'..'٩' -> '0' + (char - '٠')
                    else -> char.lowercaseChar()
                }
            )
        }
        return builder.toString().trim().replace(Regex("\\s+"), " ").replace(surahPrefix, "")
    }

    fun matches(surah: Surah, localizedName: String, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return true
        return normalize(localizedName).contains(normalizedQuery) ||
            normalize(surah.name).contains(normalizedQuery) ||
            surah.id.toString().contains(normalizedQuery)
    }
}
