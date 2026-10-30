package app.portfolio.core

/**
 * Срез репозитория данных nubasikplayyt-dev/invest-watch в test/resources.
 * Файлы watchlist, status, CRITERIA.md и схема скопированы как есть;
 * в events.jsonl и feed.json добавлены примеры событий — в репозитории они
 * пока пустые, а без событий нечего проверять. Примеры помечены
 * строкой-комментарием в начале файла.
 */
object Fixtures {
    fun read(path: String): String =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/invest-watch/$path")) {
            "нет файла $path в test/resources"
        }.bufferedReader().readText()

    val assetIds = listOf("SBER", "SIBN", "PHOR", "PARUS-SBL")
}
