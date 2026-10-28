package app.lingvo.images

import android.util.Base64
import app.lingvo.core.WordImagePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Источник картинок к словам. Интерфейс отделяет приложение от конкретной нейросети:
 * сейчас есть сервер Stable Diffusion в домашней сети, позже можно добавить модель прямо на телефоне
 * (например, через MediaPipe Image Generator), не меняя остальной код.
 */
interface ImageBackend {
    val name: String

    /** Возвращает PNG/JPEG/WebP-байты картинки или бросает исключение. */
    suspend fun generate(prompt: String, negativePrompt: String): ByteArray

    /** Проверка связи для экрана настроек: null — всё хорошо, иначе текст ошибки. */
    suspend fun check(): String?
}

/**
 * Stable Diffusion на компьютере пользователя в той же сети: API Automatic1111 / Forge / SD.Next
 * (запуск с флагом `--api --listen`). Разрешены только локальные адреса — слова не уходят в интернет.
 */
class LocalSdServerBackend(baseUrl: String) : ImageBackend {
    private val base = baseUrl.trim().trimEnd('/')
    override val name = "Stable Diffusion (локальный сервер)"

    init {
        require(WordImagePrompt.isLocalAddress(base)) { "Разрешены только адреса домашней сети (например, http://192.168.1.20:7860)" }
    }

    override suspend fun generate(prompt: String, negativePrompt: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("negative_prompt", negativePrompt)
            .put("steps", 20)
            .put("width", 512)
            .put("height", 512)
            .put("cfg_scale", 7)
            .put("batch_size", 1)
        val text = request("$base/sdapi/v1/txt2img", body.toString(), readTimeoutMs = 180_000)
        val images = JSONObject(text).optJSONArray("images") ?: throw IOException("Сервер не вернул картинку")
        if (images.length() == 0) throw IOException("Сервер не вернул картинку")
        val b64 = images.getString(0).substringAfter("base64,")
        Base64.decode(b64, Base64.DEFAULT)
    }

    override suspend fun check(): String? = withContext(Dispatchers.IO) {
        runCatching {
            request("$base/sdapi/v1/sd-models", null, readTimeoutMs = 10_000); null
        }.getOrElse { it.message ?: "Сервер недоступен" }
    }

    private fun request(url: String, json: String?, readTimeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5_000
            c.readTimeout = readTimeoutMs
            if (json != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) throw IOException("Сервер ответил ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/**
 * Поиск готовой картинки в интернете (не генерация). Источники перебираются по очереди,
 * пока картинка не найдётся:
 *
 *  1) главная иллюстрация статьи Википедии на языке курса («Mutter» в de.wikipedia.org);
 *  2) то же на языке перевода («мама» в ru.wikipedia.org) — выручает, когда у слова нет статьи
 *     на изучаемом языке;
 *  3) Викисклад (Wikimedia Commons) — огромная библиотека свободных фотографий; ищем по английскому
 *     слову, затем по самому слову и переводу;
 *  4) Openverse — ещё одна библиотека свободных картинок;
 *  5) английская Википедия.
 *
 * Слово при этом уходит в Википедию, Викисклад и Openverse — об этом сказано в настройках.
 */
object WebImageSearch {
    private const val UA = "LingvoWorld/1.0 (Android language-learning app)"

    suspend fun find(q: WordImagePrompt.WebQuery): ByteArray = withContext(Dispatchers.IO) {
        val attempts: List<() -> String?> = buildList {
            add { wikipedia(q.wikiLang, q.wikiQuery) }
            if (q.translationLang != q.wikiLang && q.translationQuery.isNotBlank()) {
                add { wikipedia(q.translationLang, q.translationQuery) }
            }
            q.imageQueries.forEach { query -> add { commons(query) } }
            q.imageQueries.forEach { query -> add { openverse(query) } }
            q.englishQuery?.takeIf { q.wikiLang != "en" }?.let { english -> add { wikipedia("en", english) } }
        }
        val url = attempts.firstNotNullOfOrNull { it() } ?: throw IOException("Картинка не найдена")
        download(url)
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Главная иллюстрация статьи. Миниатюра всегда в PNG/JPEG, даже если в статье векторный рисунок. */
    private fun wikipedia(lang: String, query: String): String? = runCatching {
        if (query.isBlank()) return null
        val api = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&redirects=1&generator=search" +
            "&gsrlimit=3&gsrsearch=${enc(query)}&prop=pageimages&piprop=thumbnail&pithumbsize=640"
        val pages = JSONObject(get(api)).optJSONObject("query")?.optJSONObject("pages") ?: return null
        pages.keys().asSequence()
            .mapNotNull { pages.optJSONObject(it)?.optJSONObject("thumbnail")?.optString("source") }
            .firstOrNull { it.startsWith("https://") }
    }.getOrNull()

    /**
     * Викисклад: ищем только растровые файлы и берём готовую миниатюру шириной 640 —
     * так в приложение не попадут SVG, видео и PDF, которые Android не покажет.
     */
    private fun commons(query: String): String? = runCatching {
        if (query.isBlank()) return null
        val api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrnamespace=6&gsrlimit=5&gsrsearch=${enc("$query filetype:bitmap")}" +
            "&prop=imageinfo&iiprop=url&iiurlwidth=640"
        val pages = JSONObject(get(api)).optJSONObject("query")?.optJSONObject("pages") ?: return null
        pages.keys().asSequence()
            .mapNotNull { pages.optJSONObject(it)?.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl") }
            .firstOrNull { it.startsWith("https://") }
    }.getOrNull()

    private fun openverse(query: String): String? = runCatching {
        if (query.isBlank()) return null
        val api = "https://api.openverse.org/v1/images/?q=${enc(query)}&page_size=3&mature=false"
        val results = JSONObject(get(api)).optJSONArray("results") ?: return null
        (0 until results.length()).asSequence()
            .mapNotNull { results.optJSONObject(it) }
            .mapNotNull { (it.optString("thumbnail").ifEmpty { it.optString("url") }).takeIf { u -> u.startsWith("https://") } }
            .firstOrNull()
    }.getOrNull()

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000; readTimeout = 20_000
        setRequestProperty("User-Agent", UA)
        instanceFollowRedirects = true
    }

    private fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("Ответ ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun download(url: String): ByteArray {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("Ответ ${c.responseCode}")
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > 8 * 1024 * 1024) throw IOException("Слишком большая картинка")
            return bytes
        } finally { c.disconnect() }
    }
}
