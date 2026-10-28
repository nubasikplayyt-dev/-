package app.lingvo.core

import java.net.URI
import java.security.MessageDigest

/**
 * Подготовка запроса к локальной нейросети, которая рисует картинку к слову.
 *
 * Модели семейства Stable Diffusion лучше всего понимают английский, поэтому предмет рисунка берётся так:
 *  1) курс английского — само слово;
 *  2) есть английская подсказка `gloss_en` (стартовые словари) — она;
 *  3) перевод курса на английский — перевод;
 *  4) иначе — слово и его перевод как есть (модель может понять хуже; честное ограничение).
 * Всё это оборачивается в простую конструкцию «a simple cute illustration of …».
 */
object WordImagePrompt {
    const val NEGATIVE = "text, letters, words, watermark, signature, logo, blurry, low quality, nsfw, gore"

    data class Request(val key: String, val subject: String, val prompt: String)

    /** Ключ кэша: язык + нормализованное слово. Одно и то же слово в любом курсе рисуется один раз. */
    fun cacheKey(languageCode: String, entry: VocabEntry): String {
        val raw = languageCode.lowercase() + "|" + Normalizer.entryKey(entry.term, entry.article)
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    fun subject(entry: VocabEntry, spec: LanguageSpec, glossEn: String?): String {
        val term = entry.term.trim()
        return when {
            spec.languageCode == "en" -> term.removePrefix("to ").trim()
            !glossEn.isNullOrBlank() -> glossEn.trim()
            spec.translationCode == "en" -> entry.translation.trim()
            else -> "\"$term\" (${entry.translation.trim()})"
        }
    }

    fun build(entry: VocabEntry, spec: LanguageSpec, glossEn: String?): Request {
        val s = subject(entry, spec, glossEn).take(120)
        val prompt = "a simple cute flat illustration of $s, single subject, centered, soft pastel colors, " +
            "clean white background, children's book style, no text"
        return Request(cacheKey(spec.languageCode, entry), s, prompt)
    }

    /** Английская подсказка из неизвестных полей карточки (`gloss_en`), если она есть. */
    fun glossEn(extraJson: String): String? = runCatching {
        val obj = VocabularyJson.json.parseToJsonElement(extraJson) as? kotlinx.serialization.json.JsonObject
        (obj?.get("gloss_en") as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Адрес сервера разрешён, только если он в локальной сети: localhost, 10.x, 172.16–31.x, 192.168.x,
     * 169.254.x, *.local или .lan. Так слова пользователя не уходят во внешние сервисы.
     */
    fun isLocalAddress(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return true
        val p = host.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 127 || p[0] == 10 || (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) || (p[0] == 169 && p[1] == 254)
    }
}
