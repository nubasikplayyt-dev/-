package app.portfolio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Решение «о чём звонить» — самое важное в приложении, проверяем подробно. */
class NewEventsTest {

    private val all = Fixtures.assetIds.flatMap { assetId ->
        Parse.eventsJsonl(Fixtures.read("data/$assetId/events.jsonl"), assetId).events
    }

    private val notifiable = all.filter { it.notify }

    @Test
    fun `в срезе данных есть и шумные, и тихие события`() {
        assertEquals(6, notifiable.size)
        assertTrue(all.size > notifiable.size)
    }

    @Test
    fun `первый запуск ничего не присылает, но всё запоминает`() {
        val result = NewEvents.select(all, alreadySeen = emptySet(), firstRun = true)

        assertEquals(emptyList(), result.toNotify)
        assertEquals(notifiable.map { it.id }.toSet(), result.seen)
    }

    @Test
    fun `уведомляет только о новых событиях с notify`() {
        val seen = notifiable.drop(1).map { it.id }.toSet()

        val result = NewEvents.select(all, alreadySeen = seen)

        assertEquals(listOf(notifiable.first().id), result.toNotify.map { it.id })
        assertTrue(result.toNotify.all { it.notify })
    }

    @Test
    fun `повторная проверка без изменений молчит`() {
        val first = NewEvents.select(all, alreadySeen = emptySet())
        val second = NewEvents.select(all, alreadySeen = first.seen)

        assertEquals(6, first.toNotify.size)
        assertEquals(emptyList(), second.toNotify)
    }

    @Test
    fun `заглушённая бумага не присылает уведомлений, но помечается прочитанной`() {
        val result = NewEvents.select(all, alreadySeen = emptySet(), mutedAssets = setOf("PHOR"))

        assertFalse(result.toNotify.any { it.assetId == "PHOR" })
        assertTrue(result.seen.containsAll(notifiable.map { it.id }))
    }

    @Test
    fun `новые события идут сверху`() {
        val dates = NewEvents.select(all, alreadySeen = emptySet()).toNotify.map { it.date }

        assertEquals(dates.sortedDescending(), dates)
    }

    @Test
    fun `точка непрочитанного гаснет после отметки`() {
        val sber = Parse.eventsJsonl(Fixtures.read("data/SBER/events.jsonl"), "SBER").events

        assertTrue(NewEvents.hasUnread(sber, emptySet()))
        assertFalse(NewEvents.hasUnread(sber, sber.map { it.id }.toSet()))
    }

    @Test
    fun `бумага без событий непрочитанного не показывает`() {
        assertFalse(NewEvents.hasUnread(emptyList(), emptySet()))
    }
}
