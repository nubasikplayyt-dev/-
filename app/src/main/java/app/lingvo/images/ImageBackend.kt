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
