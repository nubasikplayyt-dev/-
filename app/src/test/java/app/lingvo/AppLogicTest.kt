package app.lingvo

import app.lingvo.core.Languages
import app.lingvo.core.LearningStatus
import app.lingvo.core.VocabEntry
import app.lingvo.data.Card
import app.lingvo.data.ReviewLogEntity
import app.lingvo.data.ReviewStateEntity
import app.lingvo.data.VocabularyEntryEntity
import app.lingvo.ui.OverviewCalculator
import app.lingvo.ui.screens.DictionaryFilter
import app.lingvo.ui.screens.DueFilter
import app.lingvo.ui.screens.EntryDraft
import app.lingvo.ui.screens.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AppLogicTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun card(id: String, lang: String, term: String, tr: String, status: LearningStatus = LearningStatus.NEW,
                     introduced: Long? = null, due: Long = 0, tags: List<String> = emptyList(), article: String = "") =
        Card(
            VocabEntry(id = id, language = lang, term = term, translation = tr, tags = tags, article = article, createdAt = now, updatedAt = now),
            ReviewStateEntity.fresh(id, lang).copy(introducedAt = introduced, due = due),
            status, false,
        )

    private val cards = listOf(
        card("de-1", "de", "die Voraussetzung", "условие", LearningStatus.REVIEW, introduced = now - day, due = now - 1, tags = listOf("академическая лексика"), article = "die"),
        card("de-2", "de", "eine Entscheidung treffen", "принимать решение", LearningStatus.LEARNING, introduced = now, due = now + 3 * day),
        card("es-1", "es", "¿Qué tal?", "как дела?"),
    )

    @Test
    fun searchFindsByTranslationAndTermIgnoringCaseAndPunctuation() {
        assertEquals(listOf("de-2"), DictionaryFilter(query = "РЕШЕНИЕ").apply(cards, now).map { it.id })
        assertEquals(listOf("es-1"), DictionaryFilter(query = "QUÉ tal").apply(cards, now).map { it.id })
        // Поиск не требует набирать ударения.
        assertEquals(listOf("es-1"), DictionaryFilter(query = "que tal").apply(cards, now).map { it.id })
        assertEquals(listOf("de-1"), DictionaryFilter(query = "Voraussetzung").apply(cards, now).map { it.id })
    }

    @Test
    fun filtersByLanguageStatusTagAndDue() {
        assertEquals(2, DictionaryFilter(language = "de").apply(cards, now).size)
        assertEquals(listOf("de-1"), DictionaryFilter(status = LearningStatus.REVIEW).apply(cards, now).map { it.id })
        assertEquals(listOf("de-1"), DictionaryFilter(tag = "Академическая лексика").apply(cards, now).map { it.id })
        assertEquals(listOf("de-1"), DictionaryFilter(due = DueFilter.TODAY).apply(cards, now).map { it.id })
        assertEquals(2, DictionaryFilter(due = DueFilter.WEEK).apply(cards, now).size)
        assertEquals("die Voraussetzung", DictionaryFilter(sort = SortOrder.ALPHA, language = "de").apply(cards, now).first().entry.term)
    }

    @Test
    fun draftKeepsGermanArticleAndValidates() {
        val spec = Languages.GERMAN
        assertEquals("Введите перевод.", EntryDraft(term = "Haus").validate(spec))
        val e = EntryDraft(term = "Voraussetzung", translation = "условие", article = "die", plural = "Voraussetzungen")
            .toEntry(spec, "de-x", null)
        assertEquals("die Voraussetzung", e.term)
        assertEquals("die", e.article)
        assertEquals("noun", e.partOfSpeech)
        assertEquals("Voraussetzungen", e.plural)
        assertNull(EntryDraft(term = "die Bank", translation = "скамья", article = "die").validate(spec))
        assertEquals("die Bank", EntryDraft(term = "die Bank", translation = "скамья", article = "die").toEntry(spec, "x", null).term)
    }

    @Test
    fun editingKeepsIdentityAndEntityRoundTrip() {
        val base = cards[0].entry
        val edited = EntryDraft.from(base).copy(translation = "предпосылка; условие").toEntry(Languages.GERMAN, base.id, base)
        assertEquals(base.id, edited.id)
        assertEquals(base.createdAt, edited.createdAt)
        val entity = VocabularyEntryEntity.from(edited, locallyEdited = true)
        assertEquals(edited.copy(), entity.toDomain())
        assertEquals("die voraussetzung", entity.termKey)
    }

    @Test
    fun overviewCountsDueNewTodayAndStreak() {
        val zone = ZoneId.of("UTC")
        val logs = listOf(
            ReviewLogEntity(entryId = "de-1", language = "de", rating = 3, reviewedAt = now - 60_000, stateBefore = "REVIEW", elapsedMs = 0),
            ReviewLogEntity(entryId = "de-1", language = "de", rating = 1, reviewedAt = now - day, stateBefore = "REVIEW", elapsedMs = 0),
            ReviewLogEntity(entryId = "es-1", language = "es", rating = 3, reviewedAt = now - 60_000, stateBefore = "NEW", elapsedMs = 0),
        )
        val de = OverviewCalculator.compute(Languages.GERMAN, cards, logs, newLimit = 10, now = now, zone = zone)
        assertEquals(2, de.total)
        assertEquals(1, de.due)
        assertEquals(0, de.newAvailable)
        assertEquals(1, de.reviewsToday)
        assertEquals(2, de.streak)
        assertEquals(2, de.totalReviews)
        assertEquals(1, de.totalCorrect)
        val es = OverviewCalculator.compute(Languages.SPANISH, cards, logs, newLimit = 10, now = now, zone = zone)
        assertEquals(1, es.newAvailable)
        assertEquals(1, es.newLeftToday)
        assertEquals("de", OverviewCalculator.needsAttention(listOf(de, es))?.spec?.code)
        assertTrue(OverviewCalculator.achievements(listOf(de, es), logs, zone, java.time.LocalDate.of(2027, 1, 15)).first().unlocked)
    }
}
