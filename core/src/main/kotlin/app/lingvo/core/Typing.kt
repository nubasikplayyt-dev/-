package app.lingvo.core

import java.util.Locale

/** Тип задания при повторении. */
enum class TaskType { BUTTONS, TYPING }

/** Режим повторения курса. */
enum class ReviewMode(val labelRu: String) {
    BUTTONS("Помню / Не помню"),
    TYPING("Написать слово"),
    MIXED("Вперемешку"),
    ;

    /**
     * Какое задание дать для карточки. MIXED — примерно каждое третье задание письменное;
     * выбор стабилен для карточки и меняется от повторения к повторению.
     */
    fun taskFor(entryId: String, reps: Int): TaskType = when (this) {
        BUTTONS -> TaskType.BUTTONS
        TYPING -> TaskType.TYPING
        MIXED -> if (Math.floorMod(entryId.hashCode() + reps, 3) == 0) TaskType.TYPING else TaskType.BUTTONS
    }
}

/** Результат автоматического сравнения — только подсказка; окончательно решает пользователь. */
enum class AutoResult(val labelRu: String, val looksCorrect: Boolean) {
    EXACT("Совпадает", true),
    ALTERNATIVE("Совпадает с допустимым вариантом", true),
    CASE("Отличается только заглавными буквами", false),
    MISSING_ARTICLE("Слово верное, но без артикля", false),
    DIACRITICS("Отличаются ударения или диакритика", false),
    CLOSE("Похоже, есть опечатка", false),
    WRONG("Не совпадает", false),
    EMPTY("Ответ не введён", false),
}

enum class DiffKind { SAME, WRONG, MISSING, EXTRA }

/** Фрагмент посимвольного сравнения для подсветки. */
data class DiffPart(val text: String, val kind: DiffKind)

data class AnswerCheck(
    val typed: String,
    /** Ближайший к ответу правильный вариант. */
    val expected: String,
    val result: AutoResult,
    /** Сравнение ответа с [expected] для показа пользователю. */
    val diff: List<DiffPart>,
)

/**
 * Сравнение письменного ответа с карточкой.
 * Нормализуются только случайные пробелы, форма Unicode и обрамляющая пунктуация (¿?¡!.).
 * Регистр не важен, кроме немецкого (там заглавная буква существительного значима).
 * Диакритика и ударения не игнорируются: такие расхождения показываются как спорные.
 */
object AnswerChecker {
    private val edge = Regex("^[¿¡\"'«»„“”.,;:!?\\s]+|[\"'«»„“”.,;:!?\\s]+$")
    private val caseSignificant = setOf("de")

    fun normalize(text: String): String = Normalizer.clean(text).replace(edge, "")

    /** Все допустимые записи: выражение (с артиклем и без), сохранённые варианты. */
    fun variants(entry: VocabEntry): List<Pair<String, Boolean>> {
        val main = listOf(entry.displayTerm, entry.term).map(::normalize).filter { it.isNotEmpty() }.distinct()
        val alts = entry.alternatives.map(::normalize).filter { it.isNotEmpty() && it !in main }.distinct()
        return main.map { it to false } + alts.map { it to true }
    }

    fun check(typedRaw: String, entry: VocabEntry, spec: LanguageSpec): AnswerCheck {
        val typed = normalize(typedRaw)
        val variants = variants(entry)
        val primary = variants.firstOrNull()?.first.orEmpty()
        if (typed.isEmpty()) return AnswerCheck(typed, primary, AutoResult.EMPTY, listOf(DiffPart(primary, DiffKind.MISSING)))
        val caseMatters = spec.languageCode in caseSignificant
        var best: Pair<AutoResult, String>? = null
        fun consider(r: AutoResult, v: String) {
            if (best == null || r.ordinal < best!!.first.ordinal) best = r to v
        }
        val lowerTyped = typed.lowercase(Locale.ROOT)
        for ((v, isAlt) in variants) {
            val lv = v.lowercase(Locale.ROOT)
            when {
                typed == v -> consider(if (isAlt) AutoResult.ALTERNATIVE else AutoResult.EXACT, v)
                lowerTyped == lv -> consider(
                    if (caseMatters) AutoResult.CASE else if (isAlt) AutoResult.ALTERNATIVE else AutoResult.EXACT, v,
                )
                missingArticle(lowerTyped, lv, spec) -> consider(AutoResult.MISSING_ARTICLE, v)
                Normalizer.searchKey(typed) == Normalizer.searchKey(v) -> consider(AutoResult.DIACRITICS, v)
                levenshtein(lowerTyped, lv) <= maxOf(1, lv.length / 6) -> consider(AutoResult.CLOSE, v)
                else -> consider(AutoResult.WRONG, v)
            }
        }
        val (result, expected) = best ?: (AutoResult.WRONG to primary)
        val shownExpected = if (result == AutoResult.WRONG) primary else expected
        return AnswerCheck(typed, shownExpected, result, diff(typed, shownExpected))
    }

    private fun missingArticle(typed: String, variant: String, spec: LanguageSpec): Boolean {
        if (spec.articles.isEmpty()) return false
        val first = variant.substringBefore(' ', "")
        if (first !in spec.articles) return false
        return typed == variant.substringAfter(' ')
    }

    fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    /** Посимвольное выравнивание ответа с ожидаемым вариантом (по расстоянию Левенштейна). */
    fun diff(typed: String, expected: String): List<DiffPart> {
        val n = typed.length
        val m = expected.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (typed[i - 1] == expected[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        }
        val parts = ArrayList<DiffPart>()
        fun push(c: Char, k: DiffKind) {
            val last = parts.lastOrNull()
            if (last != null && last.kind == k) parts[parts.lastIndex] = last.copy(text = last.text + c) else parts += DiffPart(c.toString(), k)
        }
        var i = n
        var j = m
        val rev = ArrayList<Pair<Char, DiffKind>>()
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + if (typed[i - 1] == expected[j - 1]) 0 else 1 -> {
                    rev += expected[j - 1] to if (typed[i - 1] == expected[j - 1]) DiffKind.SAME else DiffKind.WRONG
                    i--; j--
                }
                j > 0 && d[i][j] == d[i][j - 1] + 1 -> { rev += expected[j - 1] to DiffKind.MISSING; j-- }
                else -> { rev += typed[i - 1] to DiffKind.EXTRA; i-- }
            }
        }
        rev.asReversed().forEach { (c, k) -> push(c, k) }
        return parts
    }

    /**
     * Перевод ручного подтверждения в оценку FSRS:
     *   «Ответ правильный»   → GOOD (как «Помню») — даже если автопроверка не нашла совпадения;
     *   «Ответ неправильный» → AGAIN (как «Не помню»).
     */
    fun answerFor(confirmedCorrect: Boolean): Answer = if (confirmedCorrect) Answer.REMEMBERED else Answer.FORGOT
}
