package app.investwatch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Избранное — единственное, что приложение пишет в репозиторий.
 * Агент читает его, чтобы знать, что мониторить, поэтому чужую правку
 * терять нельзя: проверяем слияние подробно.
 */
class FavoritesTest {

    private val repoFavorites = Parse.favorites(Fixtures.repo("config/favorites.json"))

    @Test
    fun `читает избранное репозитория`() {
        assertEquals(listOf("SBER", "SIBN", "PHOR", "PARUS-SBL"), repoFavorites)
    }

    @Test
    fun `читает и голый массив`() {
        assertEquals(listOf("SBER", "NBU"), Parse.favorites("""["SBER","NBU"]"""))
    }

    @Test
    fun `битый файл не роняет приложение`() {
        assertEquals(emptyList(), Parse.favorites("это не json"))
        assertEquals(emptyList(), Parse.favorites("{}"))
    }

    @Test
    fun `добавление появляется в итоговом списке`() {
        val pending = Favorites.Pending().add("NBU")

        val merged = Favorites.merge(repoFavorites, repoFavorites, pending)

        assertEquals(listOf("SBER", "SIBN", "PHOR", "PARUS-SBL", "NBU"), merged)
    }

    @Test
    fun `удаление убирает бумагу`() {
        val pending = Favorites.Pending().remove("SIBN")

        val merged = Favorites.merge(repoFavorites, repoFavorites, pending)

        assertEquals(listOf("SBER", "PHOR", "PARUS-SBL"), merged)
    }

    @Test
    fun `звёздочку нажали дважды — побеждает последнее нажатие`() {
        val pending = Favorites.Pending().add("NBU").remove("NBU").add("NBU")

        assertTrue("NBU" in Favorites.merge(repoFavorites, repoFavorites, pending))

        val back = Favorites.Pending().remove("SBER").add("SBER").remove("SBER")

        assertTrue("SBER" !in Favorites.merge(repoFavorites, repoFavorites, back))
    }

    // Конфликт sha: пока правка ждала сети, файл изменили с другой стороны.
    // Перечитываем свежий файл и накладываем на него свои правки, а не затираем.
    @Test
    fun `чужая правка не теряется при конфликте sha`() {
        val localOrder = listOf("SBER", "SIBN", "PHOR", "PARUS-SBL")
        val pending = Favorites.Pending().add("NBU")
        // Кто-то другой тем временем добавил 2899 и убрал PHOR.
        val freshRemote = listOf("SBER", "SIBN", "PARUS-SBL", "2899")

        val merged = Favorites.merge(freshRemote, localOrder, pending)

        assertEquals(listOf("SBER", "SIBN", "PARUS-SBL", "2899", "NBU"), merged)
        assertTrue("PHOR" !in merged, "чужое удаление уважаем")
        assertTrue("2899" in merged, "чужое добавление не теряем")
        assertTrue("NBU" in merged, "своё добавление доезжает")
    }

    @Test
    fun `своё удаление сильнее чужого присутствия`() {
        val pending = Favorites.Pending().remove("PHOR")

        val merged = Favorites.merge(listOf("SBER", "PHOR"), listOf("SBER", "PHOR"), pending)

        assertEquals(listOf("SBER"), merged)
    }

    @Test
    fun `порядок владельца сохраняется, новое из репозитория идёт в конец`() {
        val localOrder = listOf("PARUS-SBL", "PHOR", "SBER", "SIBN")
        val freshRemote = listOf("SBER", "SIBN", "PHOR", "PARUS-SBL", "2899")

        val merged = Favorites.merge(freshRemote, localOrder, Favorites.Pending())

        assertEquals(listOf("PARUS-SBL", "PHOR", "SBER", "SIBN", "2899"), merged)
    }

    @Test
    fun `без локальных правок список равен удалённому`() {
        assertEquals(
            repoFavorites,
            Favorites.merge(repoFavorites, repoFavorites, Favorites.Pending()),
        )
    }

    @Test
    fun `дубликатов в итоге не бывает`() {
        val merged = Favorites.merge(
            listOf("SBER", "SBER"),
            listOf("SBER"),
            Favorites.Pending().add("SBER"),
        )

        assertEquals(listOf("SBER"), merged)
    }

    @Test
    fun `содержимое файла читается обратно без потерь`() {
        val favorites = listOf("SBER", "PARUS-SBL", "2899")

        val text = Favorites.render(favorites)

        assertEquals(favorites, Parse.favorites(text))
        assertTrue(text.contains("\"note\""), "пояснение агенту сохраняется")
    }

    @Test
    fun `пустое избранное тоже корректный файл`() {
        assertEquals(emptyList(), Parse.favorites(Favorites.render(emptyList())))
    }
}

class NotifyTest {

    private val events = Fixtures.mockEvents()
    private val favorites = Parse.favorites(Fixtures.mock("config/favorites.json")).toSet()

    @Test
    fun `в моках есть и шумные, и тихие события`() {
        assertEquals(3, events.count { it.notify })
        assertTrue(events.any { !it.notify })
    }

    @Test
    fun `первый запуск ничего не присылает, но всё запоминает`() {
        val result = Notify.select(events, emptySet(), Notify.Rules(favorites, firstRun = true))

        assertEquals(emptyList(), result.toNotify)
        assertEquals(events.filter { it.notify }.map { it.id }.toSet(), result.seen)
    }

    // Главное правило: звонить только по избранному.
    @Test
    fun `события не избранных бумаг не присылаются`() {
        val onlyNbu = Notify.Rules(favorites = setOf("NBU"))

        val result = Notify.select(events, emptySet(), onlyNbu)

        assertTrue(result.toNotify.isNotEmpty())
        assertTrue(result.toNotify.all { it.assetId == "NBU" })
        // HRF не в избранном, хотя у его события notify: true.
        assertTrue(events.any { it.assetId == "HRF" && it.notify })
        assertTrue(result.toNotify.none { it.assetId == "HRF" })
    }

    @Test
    fun `заглушённая бумага молчит, но помечается прочитанной`() {
        val rules = Notify.Rules(favorites = setOf("NBU"), muted = setOf("NBU"))

        val result = Notify.select(events, emptySet(), rules)

        assertEquals(emptyList(), result.toNotify)
        assertTrue(result.seen.containsAll(events.filter { it.notify }.map { it.id }))
    }

    @Test
    fun `повторная проверка без изменений молчит`() {
        val rules = Notify.Rules(favorites)
        val first = Notify.select(events, emptySet(), rules)

        val second = Notify.select(events, first.seen, rules)

        assertTrue(first.toNotify.isNotEmpty())
        assertEquals(emptyList(), second.toNotify)
    }

    @Test
    fun `выключенные уведомления ничего не присылают`() {
        val result = Notify.select(events, emptySet(), Notify.Rules(favorites, enabled = false))

        assertEquals(emptyList(), result.toNotify)
    }

    @Test
    fun `новые события идут сверху`() {
        val dates = Notify.select(events, emptySet(), Notify.Rules(favorites)).toNotify.map { it.date }

        assertEquals(dates.sortedDescending(), dates)
    }

    @Test
    fun `тихие часы через полночь`() {
        val night = QuietHours(enabled = true, fromMinutes = 23 * 60, toMinutes = 8 * 60)

        assertTrue(night.covers(23 * 60 + 30))
        assertTrue(night.covers(3 * 60))
        assertTrue(night.covers(0))
        assertTrue(!night.covers(8 * 60))
        assertTrue(!night.covers(12 * 60))
    }

    @Test
    fun `тихие часы внутри одного дня`() {
        val lunch = QuietHours(enabled = true, fromMinutes = 13 * 60, toMinutes = 14 * 60)

        assertTrue(lunch.covers(13 * 60 + 30))
        assertTrue(!lunch.covers(12 * 60))
        assertTrue(!lunch.covers(14 * 60))
    }

    @Test
    fun `выключенные тихие часы никого не глушат`() {
        assertTrue(!QuietHours(enabled = false).covers(2 * 60))
    }

    @Test
    fun `одинаковые границы тихих часов не глушат сутки напролёт`() {
        val same = QuietHours(enabled = true, fromMinutes = 9 * 60, toMinutes = 9 * 60)

        assertTrue(!same.covers(9 * 60))
        assertTrue(!same.covers(20 * 60))
    }

    @Test
    fun `время тихих часов показывается как 23_00`() {
        assertEquals("23:00", QuietHours.format(23 * 60))
        assertEquals("08:30", QuietHours.format(8 * 60 + 30))
    }

    @Test
    fun `точка непрочитанного гаснет после отметки`() {
        val nbu = events.filter { it.assetId == "NBU" }

        assertTrue(Notify.hasUnread(nbu, emptySet()))
        assertTrue(!Notify.hasUnread(nbu, nbu.map { it.id }.toSet()))
        assertTrue(!Notify.hasUnread(emptyList(), emptySet()))
    }
}
