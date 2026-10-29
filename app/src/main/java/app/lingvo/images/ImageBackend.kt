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
 * Поиск готовой картинки к слову.
 *
 * Главное требование — картинка должна подходить слову, поэтому поиск идёт не по тексту статей,
 * а по понятиям:
 *
 *  1) **Викиданные**: слово ищется как название понятия на языке курса, затем на языке перевода,
 *     затем по-английски. У понятия берётся свойство «изображение» (P18) — это выверенная людьми
 *     картинка именно для этого значения, а не первая попавшаяся из поиска.
 *  2) **Статья Википедии с точным названием** (а не поиск по тексту): заглавная иллюстрация статьи.
 *     Страницы значений («Мута может означать…») отбрасываются.
 *  3) **Openverse** — только если ничего не нашлось и известно английское слово.
 *
 * Если подходящего не нашлось, картинки просто не будет: это лучше, чем случайное фото.
 */
object WebImageSearch {
    private const val UA = "LingvoWorld/1.0 (Android language-learning app)"
    /** Форматы, которые Android не покажет или покажет как схему, а не картинку. */
    private val badFiles = listOf(".svg", ".ogv", ".webm", ".ogg", ".pdf", ".tif", ".tiff", ".gif")
    /** Служебные страницы Викиданных: списки, категории, значения — у них картинки не по смыслу. */
    private val serviceDescriptions = listOf("wikimedia", "википеди", "disambiguation", "значени")

    suspend fun find(q: WordImagePrompt.WebQuery, skip: Int = 0): ByteArray = withContext(Dispatchers.IO) {
        val sources: List<() -> List<String>> = buildList {
            add { wikidata(q.wikiLang, q.wikiQuery) }
            if (q.translationLang != q.wikiLang) add { wikidata(q.translationLang, q.translationQuery) }
            q.englishQuery?.let { english -> add { wikidata("en", english) } }
            add { listOfNotNull(article(q.wikiLang, q.wikiQuery)) }
            if (q.translationLang != q.wikiLang) add { listOfNotNull(article(q.translationLang, q.translationQuery)) }
            q.englishQuery?.let { english -> add { listOfNotNull(article("en", english)) } }
            q.englishQuery?.let { english -> add { openverse(english) } }
        }
        // Нужен (skip + 1)-й подходящий вариант: так кнопка «другая картинка» показывает следующий.
        val found = ArrayList<String>()
        for (source in sources) {
            source().forEach { url -> if (url !in found) found += url }
            if (found.size > skip) break
        }
        val url = found.getOrNull(skip) ?: found.lastOrNull() ?: throw IOException("Подходящей картинки не нашлось")
        download(url)
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun usable(file: String) = badFiles.none { file.lowercase().endsWith(it) }

    /** Ссылка на миниатюру файла Викисклада шириной 640 точек. */
    private fun commonsThumb(file: String) = "https://commons.wikimedia.org/wiki/Special:FilePath/" +
        enc(file.removePrefix("File:").replace(' ', '_')) + "?width=640"

    /**
     * Викиданные: ищем понятие с таким названием и берём его картинку (P18).
     * Берём только те понятия, чьё название совпадает со словом — иначе легко попасть
     * на фамилию, деревню или песню с похожим написанием.
     */
    private fun wikidata(lang: String, query: String): List<String> = runCatching {
        if (query.isBlank()) return emptyList()
        val search = "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&origin=*" +
            "&type=item&limit=7&language=$lang&uselang=$lang&search=${enc(query)}"
        val results = JSONObject(get(search)).optJSONArray("search") ?: return emptyList()
        val wanted = query.lowercase()
        val ids = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { item ->
                val label = item.optString("label").lowercase()
                val description = item.optString("description").lowercase()
                val matched = item.optJSONObject("match")?.optString("text").orEmpty().lowercase()
                (label == wanted || matched == wanted) && serviceDescriptions.none { description.contains(it) }
            }
            .mapNotNull { it.optString("id").takeIf { id -> id.startsWith("Q") } }
            .take(5)
        if (ids.isEmpty()) return emptyList()
        val entities = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&origin=*" +
            "&props=claims&ids=${ids.joinToString("|")}"
        val all = JSONObject(get(entities)).optJSONObject("entities") ?: return emptyList()
        // Порядок важен: сначала понятие, которое поиск Викиданных счёл самым подходящим.
        ids.mapNotNull { id ->
            all.optJSONObject(id)?.optJSONObject("claims")?.optJSONArray("P18")
                ?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                ?.takeIf { it.isNotBlank() && usable(it) }
        }.map(::commonsThumb)
    }.getOrElse { emptyList() }

    /**
     * Заглавная иллюстрация статьи с точным названием. Поиск по тексту не используется:
     * именно он приводил к случайным картинкам.
     */
    private fun article(lang: String, title: String): String? = runCatching {
        if (title.isBlank()) return null
        val api = "https://$lang.wikipedia.org/api/rest_v1/page/summary/" +
            enc(title.replace(' ', '_')).replace("+", "%20")
        val json = JSONObject(get(api))
        // "standard" — обычная статья; страницы значений и перенаправления на списки пропускаем.
        if (json.optString("type") != "standard") return null
        val source = json.optJSONObject("thumbnail")?.optString("source")
            ?: json.optJSONObject("originalimage")?.optString("source")
        source?.takeIf { it.startsWith("https://") && usable(it.substringBefore('?')) }
    }.getOrNull()

    private fun openverse(query: String): List<String> = runCatching {
        if (query.isBlank()) return emptyList()
        val api = "https://api.openverse.org/v1/images/?q=${enc(query)}&page_size=5&mature=false"
        val results = JSONObject(get(api)).optJSONArray("results") ?: return emptyList()
        (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .mapNotNull { (it.optString("thumbnail").ifEmpty { it.optString("url") }).takeIf { u -> u.startsWith("https://") } }
    }.getOrElse { emptyList() }

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
