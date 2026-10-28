package app.lingvo.island

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.lingvo.core.LanguageSpec
import app.lingvo.core.Languages
import app.lingvo.core.SessionProgress
import app.lingvo.core.SessionText
import app.lingvo.data.AppSettings
import app.lingvo.data.CapsuleContent
import app.lingvo.data.LearningSessionEntity
import app.lingvo.data.LingvoRepository
import app.lingvo.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Компактный интерфейс активной сессии («Dynamic Island»-подобный):
 *  уровень A — одно обновляемое уведомление с прогрессом (Live Update на Android 16+);
 *  уровень B — необязательная плавающая капсула поверх других приложений.
 * Работает только по событиям базы данных — без polling, wake lock и постоянного сервиса.
 */
class IslandController(
    private val context: Context,
    private val repo: LingvoRepository,
    private val scope: CoroutineScope,
) {
    /** Язык → прогресс (done), при котором пользователь нажал «Отложить». */
    private val snoozed = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val foreground = MutableStateFlow(false)
    private val overlay by lazy { CapsuleOverlay(context) }
    private val shownLanguages = HashSet<String>()

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground.value = true }
            override fun onStop(owner: LifecycleOwner) { foreground.value = false }
        })
        scope.launch {
            combine(repo.observeActiveSessions(), repo.settings.flow, snoozed, foreground) { sessions, settings, sn, fg ->
                State(sessions, settings, sn, fg)
            }.collect { render(it) }
        }
    }

    fun snooze(spec: LanguageSpec) {
        scope.launch {
            val done = repo.activeSessions().firstOrNull { it.language == spec.code }?.done ?: 0
            snoozed.update { it + (spec.code to done) }
            Notifications.cancelSession(context, spec)
        }
    }

    fun hideCapsuleForNow() {
        scope.launch(Dispatchers.Main) { overlay.dismissUntilNextSession() }
    }

    private data class State(
        val sessions: List<LearningSessionEntity>,
        val settings: AppSettings,
        val snoozed: Map<String, Int>,
        val foreground: Boolean,
    )

    private suspend fun render(s: State) {
        val active = s.sessions.filter { it.done < it.total }.associateBy { it.language }
        // Сбрасываем «отложено», если пользователь продолжил сессию или она закончилась.
        val stillSnoozed = s.snoozed.filter { (lang, done) -> active[lang]?.done == done }
        if (stillSnoozed != s.snoozed) snoozed.value = stillSnoozed

        val otherQueues = if (s.settings.capsuleContent == CapsuleContent.BOTH_QUEUES) {
            SessionText.dueSummary(Languages.all.map { it to repo.dueCount(it.code) }).ifEmpty { null }
        } else null

        for (spec in Languages.all) {
            val session = active[spec.code]
            val show = session != null && s.settings.sessionNotification && spec.code !in stillSnoozed &&
                Notifications.channelEnabled(context, Notifications.CHANNEL_SESSION)
            if (show && session != null) {
                val word = if (s.settings.showWordInNotification) {
                    SessionProgress.decodeQueue(session.queue).firstOrNull()?.let { repo.cardForSession(it)?.entry?.displayTerm }
                } else null
                Notifications.showSession(
                    context,
                    Notifications.SessionInfo(spec, session.id, session.done, session.total, word, otherQueues),
                    s.settings,
                )
                shownLanguages += spec.code
            } else if (spec.code in shownLanguages || session == null) {
                Notifications.cancelSession(context, spec)
                shownLanguages -= spec.code
            }
        }

        val current = s.sessions.firstOrNull { it.done < it.total }
        val capsuleVisible = current != null && s.settings.capsuleEnabled && !s.foreground && canDrawOverlays(context)
        withContext(Dispatchers.Main) {
            if (capsuleVisible && current != null) {
                val spec = Languages.require(current.language)
                val label = when (s.settings.capsuleContent) {
                    CapsuleContent.SESSION -> "${spec.flag} ${current.done}/${current.total}"
                    CapsuleContent.BOTH_QUEUES -> otherQueues ?: "${spec.flag} ${current.done}/${current.total}"
                }
                overlay.show(
                    CapsuleOverlay.Model(
                        sessionId = current.id,
                        language = spec.code,
                        label = label,
                        progress = if (current.total == 0) 0f else current.done.toFloat() / current.total,
                        animate = s.settings.animations,
                    )
                )
            } else {
                overlay.hide()
            }
        }
    }

    companion object {
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
