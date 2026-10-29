package app.lingvo.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lingvo.LingvoApp
import app.lingvo.core.LanguageSpec
import app.lingvo.core.VocabEntry
import app.lingvo.core.WordImagePrompt
import kotlinx.coroutines.flow.map
import app.lingvo.data.AppSettings
import app.lingvo.data.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Картинки к словам: кэш на устройстве и фоновая генерация.
 *
 * - Файл картинки определяется ключом слова (язык + нормализованное слово), поэтому одно и то же слово
 *   рисуется один раз и переиспользуется в любом курсе; схема базы данных не меняется.
 * - Генерация идёт в WorkManager: карточка уже сохранена и показана, картинка появляется позже.
 * - Любая ошибка (сервер выключен, модель не загружена) — картинки просто нет, приложение работает дальше.
 */
class WordImages(private val context: Context) {
    /** Для проверок возможностей устройства из интерфейса. */
    val appContext: Context get() = context

    private val dir = File(context.filesDir, "word_images").apply { mkdirs() }
    private val _revision = MutableStateFlow(0)
    /** Меняется, когда появляется новая картинка, — экраны перечитывают кэш. */
    val revision: StateFlow<Int> = _revision

    fun fileFor(key: String) = File(dir, "$key.webp")

    /** Состояние рисования слова — для индикатора и кнопки «Повторить» на карточке. */
    enum class Status { NONE, QUEUED, RUNNING, FAILED }

    private fun workName(key: String) = "word-image-$key"

    fun keyFor(entry: VocabEntry, spec: LanguageSpec) = WordImagePrompt.cacheKey(spec.languageCode, entry)

    /**
     * Очередь живёт в WorkManager: она на диске, поэтому задачи продолжаются после закрытия
     * приложения и переживают перезагрузку телефона.
     */
    fun observeStatus(key: String): kotlinx.coroutines.flow.Flow<Status> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(key)).map { infos ->
            when (infos.maxByOrNull { it.state.ordinal }?.state) {
                androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED -> Status.QUEUED
                androidx.work.WorkInfo.State.RUNNING -> Status.RUNNING
                androidx.work.WorkInfo.State.FAILED -> Status.FAILED
                else -> Status.NONE
            }
        }

    /** Повторить неудачную попытку вручную. */
    fun retry(entry: VocabEntry, spec: LanguageSpec, settings: AppSettings) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(keyFor(entry, spec)))
        request(entry, spec, settings, force = true)
    }
    fun fileFor(entry: VocabEntry, spec: LanguageSpec) = fileFor(WordImagePrompt.cacheKey(spec.languageCode, entry))

    fun notifyChanged() { _revision.value++ }

    /** Ставит слово в очередь на рисование (если генерация включена и картинки ещё нет в кэше). */
    fun request(entry: VocabEntry, spec: LanguageSpec, settings: AppSettings, force: Boolean = false) {
        if (!settings.imageGeneration) return
        if (settings.imageSource == ImageSource.AI_SERVER && settings.imageServerUrl.isBlank()) return
        val gloss = WordImagePrompt.glossEn(entry.extraJson)
        val req = WordImagePrompt.build(entry, spec, gloss)
        if (fileFor(req.key).exists()) return
        val web = WordImagePrompt.webQuery(entry, spec, gloss)
        val work = OneTimeWorkRequestBuilder<WordImageWorker>()
            .setInputData(workDataOf(
                WordImageWorker.KEY to req.key, WordImageWorker.PROMPT to req.prompt,
                WordImageWorker.WIKI_LANG to web.wikiLang, WordImageWorker.WIKI_QUERY to web.wikiQuery,
                WordImageWorker.TR_LANG to web.translationLang, WordImageWorker.TR_QUERY to web.translationQuery,
                WordImageWorker.EN_QUERY to web.englishQuery,
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Одна задача на слово: повторное добавление того же слова не запускает вторую генерацию.
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(req.key), if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, work,
        )
    }

    /**
     * Найти картинку в интернете прямо сейчас (кнопка на странице слова); заменяет прежнюю.
     * [skip] — сколько подходящих вариантов пропустить: так кнопка «Другая картинка»
     * показывает следующий вариант, а не тот же самый.
     */
    suspend fun searchNow(entry: VocabEntry, spec: LanguageSpec, skip: Int = 0) {
        val gloss = WordImagePrompt.glossEn(entry.extraJson)
        val bytes = WebImageSearch.find(WordImagePrompt.webQuery(entry, spec, gloss), skip)
        withContext(Dispatchers.IO) { save(WordImagePrompt.cacheKey(spec.languageCode, entry), bytes) }
    }

    /** Своя картинка пользователя из галереи; заменяет прежнюю. */
    suspend fun saveFromUri(entry: VocabEntry, spec: LanguageSpec, uri: android.net.Uri) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось открыть файл")
        save(WordImagePrompt.cacheKey(spec.languageCode, entry), bytes)
    }

    fun remove(entry: VocabEntry, spec: LanguageSpec) {
        fileFor(entry, spec).delete(); notifyChanged()
    }

    /** Ставит в очередь все слова курса, у которых картинки ещё нет. Возвращает, сколько добавлено. */
    fun requestMissing(entries: List<VocabEntry>, spec: LanguageSpec, settings: AppSettings): Int {
        if (!settings.imageGeneration) return 0
        var queued = 0
        entries.forEach { entry ->
            if (!fileFor(entry, spec).exists()) { request(entry, spec, settings); queued++ }
        }
        return queued
    }

    fun clearCache(): Int {
        val files = dir.listFiles().orEmpty()
        files.forEach { it.delete() }
        notifyChanged()
        return files.size
    }

    fun cacheSize(): Pair<Int, Long> = dir.listFiles().orEmpty().let { it.size to it.sumOf { f -> f.length() } }

    /** Сохраняет картинку уменьшенной (384 px, WebP) — экономит место. */
    fun save(key: String, bytes: ByteArray) {
        // Сначала только размеры, чтобы большая фотография из галереи не съела память.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 384) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Не удалось прочитать картинку")
        val scaled = if (bmp.width > 384) Bitmap.createScaledBitmap(bmp, 384, 384 * bmp.height / bmp.width, true) else bmp
        val tmp = File(dir, "$key.tmp")
        tmp.outputStream().use { out ->
            @Suppress("DEPRECATION")
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            scaled.compress(format, 85, out)
        }
        tmp.renameTo(fileFor(key))
        notifyChanged()
    }
}

class WordImageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LingvoApp
        val images = app.container.wordImages
        val key = inputData.getString(KEY) ?: return Result.failure()
        val prompt = inputData.getString(PROMPT) ?: return Result.failure()
        if (images.fileFor(key).exists()) return Result.success()
        val settings = app.container.settings.flow.first()
        // Подбор выключили, пока слово ждало в очереди, — ничего не делаем.
        if (!settings.imageGeneration) return Result.success()
        return try {
            val bytes = when (settings.imageSource) {
                ImageSource.WEB -> WebImageSearch.find(WordImagePrompt.WebQuery(
                    wikiLang = inputData.getString(WIKI_LANG) ?: "en",
                    wikiQuery = inputData.getString(WIKI_QUERY) ?: return Result.failure(),
                    translationLang = inputData.getString(TR_LANG) ?: "ru",
                    translationQuery = inputData.getString(TR_QUERY).orEmpty(),
                    englishQuery = inputData.getString(EN_QUERY),
                ))
                ImageSource.AI_SERVER -> LocalSdServerBackend(settings.imageServerUrl).generate(prompt, WordImagePrompt.NEGATIVE)
            }
            withContext(Dispatchers.IO) { images.save(key, bytes) }
            Result.success()
        } catch (e: IllegalArgumentException) {
            Result.failure() // адрес не из домашней сети — повторять бессмысленно
        } catch (e: Exception) {
            Log.w("WordImageWorker", "Картинка не получилась: ${e.message}")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY = "key"
        const val PROMPT = "prompt"
        const val WIKI_LANG = "wiki_lang"
        const val WIKI_QUERY = "wiki_query"
        const val TR_LANG = "tr_lang"
        const val TR_QUERY = "tr_query"
        const val EN_QUERY = "en_query"
    }
}
