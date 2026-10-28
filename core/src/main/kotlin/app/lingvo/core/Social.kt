package app.lingvo.core

import kotlin.random.Random

/**
 * Соревновательный режим без своего сервера: каждый делится коротким кодом, по коду друг видит
 * обезличенную сводку прогресса (ник и цифры), учебные карточки и словарь не передаются.
 */

/** Что пользователь разрешил показывать друзьям. */
data class SharePrivacy(
    val stats: Boolean = true,
    val streak: Boolean = true,
    val courses: Boolean = false,
)

/** Сводка прогресса, которой делятся по коду. Никаких слов и заметок — только счётчики. */
data class SharedProgress(
    val nick: String,
    val updatedAt: Long,
    val todayWords: Int = 0,
    val todayPoints: Int = 0,
    val weekWords: Int = 0,
    val weekPoints: Int = 0,
    val totalWords: Int = 0,
    val totalPoints: Int = 0,
    val streak: Int = 0,
    /** Номер недели (локальный), за которую посчитаны weekWords/weekPoints. */
    val weekKey: Int = 0,
    val courses: List<String> = emptyList(),
) {
    fun toMap(): Map<String, Any> = mapOf(
        "nick" to nick, "updatedAt" to updatedAt,
        "todayWords" to todayWords, "todayPoints" to todayPoints,
        "weekWords" to weekWords, "weekPoints" to weekPoints,
        "totalWords" to totalWords, "totalPoints" to totalPoints,
        "streak" to streak, "weekKey" to weekKey,
        "courses" to courses.joinToString("\u0001"),
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): SharedProgress? {
            val nick = (m["nick"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            fun int(key: String) = (m[key] as? Number)?.toInt() ?: 0
            return SharedProgress(
                nick = nick,
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                todayWords = int("todayWords"), todayPoints = int("todayPoints"),
                weekWords = int("weekWords"), weekPoints = int("weekPoints"),
                totalWords = int("totalWords"), totalPoints = int("totalPoints"),
                streak = int("streak"), weekKey = int("weekKey"),
                courses = (m["courses"] as? String).orEmpty().split('\u0001').filter { it.isNotBlank() },
            )
        }
    }
}

/** Строка таблицы лидеров. */
data class Standing(val code: String, val nick: String, val points: Int, val words: Int, val streak: Int, val isMe: Boolean)

/** Период, за который сравниваются результаты. */
enum class LeaderboardPeriod(val labelRu: String) { TODAY("Сегодня"), WEEK("Неделя"), ALL("Всё время") }

object ShareCodes {
    /** Без похожих символов (0/O, 1/I), чтобы код можно было продиктовать. */
    private const val ALPHABET = "ACDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8

    fun generate(random: Random = Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Приводит введённый код к каноническому виду: убирает пробелы и дефисы, большие буквы. */
    fun normalize(raw: String): String? {
        // I и 1 на вид не отличить от L, их приводим к L; 0 и O в алфавите нет вовсе.
        val clean = raw.trim().uppercase().filter { it != '-' && !it.isWhitespace() }
            .map { if (it == 'I' || it == '1') 'L' else it }.joinToString("")
        if (clean.length != LENGTH || clean.any { it !in ALPHABET }) return null
        return clean
    }

    /** Красивый вид для показа и диктовки: ABCD-EFGH. */
    fun pretty(code: String): String = if (code.length == LENGTH) "${code.take(4)}-${code.drop(4)}" else code

    fun link(code: String) = "https://lingvo.world/i/$code"

    /** Код из ссылки-приглашения или просто введённого текста. */
    fun fromLink(text: String): String? {
        val tail = text.trim().substringAfterLast('/')
        return normalize(tail) ?: normalize(text)
    }
}

object Competition {
    /** Сколько дней считаем «неделей». */
    const val WEEK_DAYS = 7

    /**
     * Очки за день: один ответ — одно очко, вспомненное слово — два,
     * выполненная цель дня — ещё 20. Так виден и объём, и качество, и регулярность.
     */
    fun points(day: DayRecord): Int = day.answers + day.remembered + if (day.completed) 20 else 0

    /** Номер недели по локальной дате (понедельник — начало). */
    fun weekKey(epochDay: Long): Int = ((epochDay + 3) / 7).toInt()

    fun summarize(
        nick: String,
        days: List<DayRecord>,
        today: Long,
        snapshot: ProgressSnapshot,
        streak: Int,
        courses: List<String>,
        privacy: SharePrivacy,
        now: Long,
    ): SharedProgress {
        val todayRecord = days.firstOrNull { it.epochDay == today }
        val week = days.filter { it.epochDay > today - WEEK_DAYS && it.epochDay <= today }
        val stats = privacy.stats
        return SharedProgress(
            nick = nick,
            updatedAt = now,
            todayWords = if (stats) todayRecord?.remembered ?: 0 else 0,
            todayPoints = if (stats) todayRecord?.let(::points) ?: 0 else 0,
            weekWords = if (stats) week.sumOf { it.remembered } else 0,
            weekPoints = if (stats) week.sumOf { points(it) } else 0,
            totalWords = if (stats) snapshot.wordsLearned else 0,
            totalPoints = if (stats) days.sumOf { points(it) } else 0,
            streak = if (privacy.streak) streak else 0,
            weekKey = weekKey(today),
            courses = if (privacy.courses) courses else emptyList(),
        )
    }

    /** Таблица лидеров: сначала больше очков, при равенстве — больше слов, потом по нику. */
    fun leaderboard(me: SharedProgress?, friends: Map<String, SharedProgress>, period: LeaderboardPeriod): List<Standing> {
        fun row(code: String, p: SharedProgress, isMe: Boolean) = when (period) {
            LeaderboardPeriod.TODAY -> Standing(code, p.nick, p.todayPoints, p.todayWords, p.streak, isMe)
            LeaderboardPeriod.WEEK -> Standing(code, p.nick, p.weekPoints, p.weekWords, p.streak, isMe)
            LeaderboardPeriod.ALL -> Standing(code, p.nick, p.totalPoints, p.totalWords, p.streak, isMe)
        }
        val rows = friends.map { (code, p) -> row(code, p, false) } + listOfNotNull(me?.let { row("", it, true) })
        return rows.sortedWith(compareByDescending<Standing> { it.points }.thenByDescending { it.words }.thenBy { it.nick.lowercase() })
    }

    /**
     * Кого стоит показать в уведомлении: друг, который за эту неделю обошёл меня,
     * причём раньше не обходил. Возвращает ники.
     */
    fun overtookMe(myWeekPoints: Int, friends: Map<String, SharedProgress>, alreadyNotified: Set<String>, weekKey: Int): List<SharedProgress> =
        friends.values
            .filter { it.weekKey == weekKey && it.weekPoints > myWeekPoints && it.nick !in alreadyNotified }
            .sortedByDescending { it.weekPoints }

    /** Ник по умолчанию, если пользователь не задал свой. */
    fun defaultNick(): String = "Полиглот " + (100..999).random()
}
