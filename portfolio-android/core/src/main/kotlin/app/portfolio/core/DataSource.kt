package app.portfolio.core

/** Откуда читать репозиторий данных. Меняется в настройках приложения. */
data class RepoConfig(
    val owner: String = DEFAULT_OWNER,
    val repo: String = DEFAULT_REPO,
    val branch: String = DEFAULT_BRANCH,
) {
    fun url(path: String): String =
        "https://raw.githubusercontent.com/$owner/$repo/$branch/${path.trimStart('/')}"

    companion object {
        const val DEFAULT_OWNER = "nubasikplayyt-dev"
        const val DEFAULT_REPO = "invest-watch"
        const val DEFAULT_BRANCH = "main"
    }
}

/** Пути файлов в репозитории данных. */
object Paths {
    const val WATCHLIST = "config/watchlist.json"
    const val CRITERIA = "CRITERIA.md"
    const val FEED = "feed/feed.json"

    fun status(assetId: String) = "data/$assetId/status.json"
    fun events(assetId: String) = "data/$assetId/events.jsonl"
}
