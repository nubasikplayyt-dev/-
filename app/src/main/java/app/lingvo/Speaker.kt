package app.lingvo

import android.content.Context
import android.speech.tts.TextToSpeech
import app.lingvo.core.LanguageSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Произношение через системный TextToSpeech. Если голоса нет — сообщает об этом, а не падает. */
class Speaker(private val context: Context, private val onSpoken: () -> Unit = {}) {
    enum class Status { NOT_STARTED, READY, UNAVAILABLE }

    private var tts: TextToSpeech? = null
    private val _status = MutableStateFlow(Status.NOT_STARTED)
    val status: StateFlow<Status> = _status

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { code ->
            _status.value = if (code == TextToSpeech.SUCCESS) Status.READY else Status.UNAVAILABLE
        }
    }

    fun warmUp() = ensure()

    /** @return текст ошибки для пользователя или null, если произношение запущено. */
    fun speak(text: String, spec: LanguageSpec): String? {
        ensure()
        val engine = tts ?: return "Синтез речи недоступен."
        if (_status.value == Status.UNAVAILABLE) return "Синтез речи недоступен на этом устройстве."
        if (_status.value == Status.NOT_STARTED) return "Синтез речи ещё запускается, попробуйте через секунду."
        val result = engine.setLanguage(Locale.forLanguageTag(spec.ttsTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return "Нет голоса для курса «${spec.nameRu}». Установите его в настройках «Синтез речи» Android."
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lingvo-${text.hashCode()}")
        onSpoken()
        return null
    }

    fun hasVoice(spec: LanguageSpec): Boolean {
        val engine = tts ?: return false
        if (_status.value != Status.READY) return false
        val r = engine.isLanguageAvailable(Locale.forLanguageTag(spec.ttsTag))
        return r >= TextToSpeech.LANG_AVAILABLE
    }
}
