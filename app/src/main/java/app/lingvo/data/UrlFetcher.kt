package app.lingvo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Загрузка словаря по HTTPS-ссылке (например, raw-файл из GitHub или прямая ссылка из облака).
 * Без токенов и секретов: поддерживаются только общедоступные ссылки.
 */
object UrlFetcher {
    private const val MAX_BYTES = 20 * 1024 * 1024

    /** Ссылку на страницу файла GitHub превращает в raw-ссылку. */
    fun normalize(url: String): String {
        val u = url.trim()
        val m = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(u) ?: return u
        val (owner, repo, rest) = m.destructured
        return "https://raw.githubusercontent.com/$owner/$repo/$rest"
    }

    fun validate(url: String): String? = when {
        url.isBlank() -> "Введите ссылку."
        !url.trim().startsWith("https://") -> "Поддерживаются только ссылки https://"
        else -> null
    }

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        validate(url)?.let { throw IllegalArgumentException(it) }
        val conn = URL(normalize(url)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            val code = conn.responseCode
            if (code == 404) throw IllegalStateException("Файл не найден (404). Для приватного репозитория ссылка без токена не работает.")
            if (code !in 200..299) throw IllegalStateException("Сервер ответил кодом $code.")
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IllegalStateException("Файл слишком большой (больше 20 МБ).")
                }
                out.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Не удалось загрузить файл: нет сети или сервер недоступен.", e)
        } finally {
            conn.disconnect()
        }
    }
}
