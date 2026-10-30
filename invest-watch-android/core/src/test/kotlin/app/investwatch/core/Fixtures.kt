package app.investwatch.core

/**
 * Два набора данных в test/resources:
 *   invest-watch/ — точная копия репозитория nubasikplayyt-dev/invest-watch;
 *   mock/         — та же копия плюс три выдуманные бумаги США и Китая
 *                   (компаний не существует, цифры придуманы) — для превью
 *                   экранов и проверок на непустых данных.
 */
object Fixtures {

    fun repo(path: String): String = read("invest-watch/$path")

    fun mock(path: String): String = read("mock/$path")

    private fun read(path: String): String =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/$path")) {
            "нет файла $path в test/resources"
        }.bufferedReader().readText()

    val repoAssetIds = listOf("SBER", "SIBN", "PHOR", "PARUS-SBL")
    val mockOnlyAssetIds = listOf("NBU", "HRF", "2899")

    val repoWatchlist: Watchlist by lazy { Parse.watchlist(repo("config/watchlist.json")) }
    val mockWatchlist: Watchlist by lazy { Parse.watchlist(mock("config/watchlist.json")) }

    fun mockEvents(): List<MonitorEvent> =
        (repoAssetIds + mockOnlyAssetIds).flatMap { assetId ->
            Parse.eventsJsonl(mock("data/$assetId/events.jsonl"), assetId).events
        }
}
