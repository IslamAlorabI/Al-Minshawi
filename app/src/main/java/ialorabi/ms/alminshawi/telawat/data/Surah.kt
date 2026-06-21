package ialorabi.ms.alminshawi.telawat.data

enum class RevelationType { MAKKI, MADANI }

data class Surah(
    val id: Int,
    val name: String,
    val url: String,
    val revelationType: RevelationType,
    val juz: Int
)

object SurahRepository {
    private const val BASE_URL = "https://archive.org/download/002_20260220/"

    private val surahNames = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
        "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل",
        "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور",
        "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة",
        "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح",
        "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة",
        "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون", "التغابن",
        "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح", "الجن",
        "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية",
        "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق",
        "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر", "الهمزة",
        "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد", "الإخلاص",
        "الفلق", "الناس"
    )

    private val revelationTypes = listOf(
        RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MADANI,
        RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI,
        RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MADANI,
        RevelationType.MADANI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MADANI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MADANI, RevelationType.MADANI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MADANI,
        RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI, RevelationType.MAKKI
    )

    private val surahJuz = listOf(
        1, 1, 3, 4, 6, 7, 8, 9,
        10, 11, 11, 12, 13, 13, 14, 14,
        15, 15, 16, 16, 17, 17, 18, 18,
        18, 19, 19, 20, 20, 21, 21, 21,
        21, 22, 22, 22, 23, 23, 23, 24,
        24, 25, 25, 25, 25, 26, 26, 26,
        26, 26, 26, 27, 27, 27, 27, 27,
        27, 28, 28, 28, 28, 28, 28, 28,
        28, 28, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30
    )

    private val serverFileNames = mapOf(
        3 to "\u0627\u0653\u0644 \u0639\u0645\u0631\u0627\u0646",
        5 to "\u0627\u0644\u0645\u0627\u064A\u0654\u062F\u0629",
        6 to "\u0627\u0644\u0627\u0646\u0639\u0627\u0645",
        7 to "\u0627\u0644\u0627\u0654\u0639\u0631\u0627\u0641",
        8 to "\u0627\u0644\u0627\u0654\u0646\u0641\u0627\u0644",
        14 to "\u0627\u0628\u0631\u0627\u0647\u064A\u0645",
        17 to "\u0627\u0644\u0627\u0633\u0631\u0627\u0621",
        21 to "\u0627\u0644\u0627\u0646\u0628\u064A\u0627\u0621",
        23 to "\u0627\u0644\u0645\u0648\u0654\u0645\u0646\u0648\u0646",
        33 to "\u0627\u0644\u0627\u062D\u0632\u0627\u0628",
        34 to "\u0633\u0628\u0627\u0654",
        46 to "\u0627\u0644\u0627\u062D\u0642\u0627\u0641",
        76 to "\u0627\u0644\u0627\u0655\u0646\u0633\u0627\u0646",
        78 to "\u0627\u0644\u0646\u0628\u0627\u0654",
        82 to "\u0627\u0644\u0627\u0655\u0646\u0641\u0637\u0627\u0631",
        87 to "\u0627\u0644\u0627\u0654\u0639\u0644\u0649",
        112 to "\u0627\u0644\u0627\u0655\u062E\u0644\u0627\u0635"
    )

    val surahs: List<Surah> = surahNames.mapIndexed { index, name ->
        val id = index + 1
        val formattedId = id.toString().padStart(3, '0')
        val fileName = serverFileNames[id] ?: name
        Surah(
            id = id,
            name = name,
            url = "${BASE_URL}${formattedId}-\u0633\u0648\u0631\u0629 ${fileName}.mp3",
            revelationType = revelationTypes[index],
            juz = surahJuz[index]
        )
    }
}

