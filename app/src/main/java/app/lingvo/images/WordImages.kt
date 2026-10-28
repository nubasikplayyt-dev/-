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
import app.lingvo.data.AppSettings
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
    private val dir = File(context.filesDir, "word_images").apply { mkdirs() }
    private val _revision = MutableStateFlow(0)
    /** Меняется, когда появляется новая картинка, — экраны перечитывают кэш. */
    val revision: StateFlow<Int> = _revision

    fun fileFor(key: String) = File(dir, "$key.webp")
    fun fileFor(entry: VocabEntry, spec: LanguageSpec) = fileFor(WordImagePrompt.cacheKey(spec.languageCode, entry))

    fun notifyChanged() { _revision.value++ }

    /** Ставит слово в очередь на рисование (если генерация включена и картинки ещё нет в кэше). */
    fun request(entry: VocabEntry, spec: LanguageSpec, settings: AppSettings) {
        if (!settings.imageGeneration || settings.imageServerUrl.isBlank()) return
        val req = WordImagePrompt.build(entry, spec, WordImagePrompt.glossEn(entry.extraJson))
        if (fileFor(req.key).exists()) return
        val work = OneTimeWorkRequestBuilder<WordImageWorker>()
            .setInputData(workDataOf(WordImageWorker.KEY to req.key, WordImageWorker.PROMPT to req.prompt))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Одна задача на слово: повторное добавление того же слова не запускает вторую генерацию.
        WorkManager.getInstance(context).enqueueUniqueWork("word-image-${req.key}", ExistingWorkPolicy.KEEP, work)
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
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Не удалось прочитать картинку")
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
        // Генерацию выключили, пока слово ждало в очереди, — ничего не делаем.
        if (!settings.imageGeneration) return Result.success()
        return try {
            val backend = LocalSdServerBackend(settings.imageServerUrl)
            val bytes = withContext(Dispatchers.IO) { backend.generate(prompt, WordImagePrompt.NEGATIVE) }
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
    }
}
