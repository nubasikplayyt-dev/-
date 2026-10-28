package app.lingvo.island

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.lingvo.core.Answer
import app.lingvo.core.Creatures
import app.lingvo.data.AppSettings
import app.lingvo.data.IslandState
import app.lingvo.data.LingvoRepository
import app.lingvo.data.SessionStatus
import app.lingvo.notify.Notifications
import app.lingvo.ui.components.CreaturePainter
import app.lingvo.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Остров» занятия — компактный интерфейс в стиле Dynamic Island, который действительно работает:
 *  - уведомление (Live Update на Android 16+): существо, текущее слово, прогресс, «Не помню» / «Помню»;
 *  - необязательная плавающая капсула поверх других приложений — то же самое, раскрывается по нажатию;
 *  (внутри открытого приложения острова нет — только когда приложение свёрнуто).
 *
 * Все три читают одно состояние [LingvoRepository.observeIsland] и отвечают через
 * [LingvoRepository.answer], который засчитывает ответ только для текущей карточки —
 * поэтому они всегда синхронны с экраном повторения, и двойного счёта не бывает.
 * Работает только по событиям базы данных — без polling, wake lock и постоянного сервиса.
 */
class IslandController(
    private val context: Context,
    private val repo: LingvoRepository,
    private val scope: CoroutineScope,
) {
    /** ID сессии → прогресс (done), при котором пользователь нажал «Отложить». */
    private val snoozed = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val foreground = MutableStateFlow(false)
    /** Карточка, для которой в уведомлении уже показан перевод: sessionId to entryId. */
    private val revealed = MutableStateFlow<Pair<Long, String>?>(null)
    private val overlay by lazy { CapsuleOverlay(context, ::answerFromCapsule) }
    private var shownSessionId: Long? = null
    private var bitmapKey: String? = null
    private var bitmap: Bitmap? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground.value = true }
            override fun onStop(owner: LifecycleOwner) { foreground.value = false }
        })
        scope.launch {
            combine(repo.observeIsland(), repo.settings.flow, snoozed, foreground, revealed) { island, settings, sn, fg, rv ->
                State(island, settings, sn, fg, rv)
            }.collect { runCatching { render(it) } }
        }
    }

    fun snooze(sessionId: Long) {
        scope.launch {
            val done = repo.session(sessionId)?.done ?: 0
            snoozed.update { it + (sessionId to done) }
        }
    }

    fun reveal(sessionId: Long, entryId: String) { revealed.value = sessionId to entryId }

    private fun answerFromCapsule(sessionId: Long, entryId: String, remembered: Boolean) {
        scope.launch { repo.answer(sessionId, entryId, if (remembered) Answer.REMEMBERED else Answer.FORGOT) }
    }

    private data class State(
        val island: IslandState?,
        val settings: AppSettings,
        val snoozed: Map<Long, Int>,
        val foreground: Boolean,
        val revealed: Pair<Long, String>?,
    )

    private fun creatureBitmap(settings: AppSettings, mood: Mood): Bitmap? {
        val def = Creatures.byId(settings.favoriteCreature) ?: Creatures.all.first()
        val key = "${def.id}-$mood"
        if (key != bitmapKey) {
            val px = (64 * context.resources.displayMetrics.density).toInt()
            bitmap = runCatching { CreaturePainter.bitmap(def, 1, mood, px) }.getOrNull()
            bitmapKey = key
        }
        return bitmap
    }

    private suspend fun render(s: State) {
        val island = s.island
        val card = island?.card
        // «Отложено» действует, пока пользователь не продвинулся в этой сессии.
        val stillSnoozed = s.snoozed.filter { (id, done) -> island?.session?.id == id && island.session.done == done }
        if (stillSnoozed != s.snoozed) snoozed.value = stillSnoozed

        if (island == null || card == null) {
            // Сессии больше нет: если она закончилась сама (а не закрыта), показываем поздравление.
            val finishedId = shownSessionId
            shownSessionId = null
            withContext(Dispatchers.Main) { overlay.hide() }
            if (finishedId != null) {
                val ses = repo.session(finishedId)
                val course = ses?.let { repo.course(it.language) }
                if (ses != null && course != null && ses.status == SessionStatus.FINISHED && s.settings.sessionNotification) {
                    val def = Creatures.byId(s.settings.favoriteCreature)
                    Notifications.showIslandFinished(context, course.spec, ses.done, ses.correct, creatureBitmap(s.settings, Mood.CHEER), def?.name)
                } else Notifications.cancelIsland(context)
            } else Notifications.cancelIsland(context)
            return
        }

        val spec = island.course.spec
        val ses = island.session
        val isRevealed = s.revealed == (ses.id to card.id)
        val streak = runCatching { repo.streak().current }.getOrDefault(0)
        val def = Creatures.byId(s.settings.favoriteCreature)
        val showNotification = s.settings.sessionNotification && ses.id !in stillSnoozed &&
            Notifications.channelEnabled(context, Notifications.CHANNEL_SESSION)
        if (showNotification) {
            Notifications.showIsland(
                context,
                Notifications.IslandInfo(
                    spec = spec, sessionId = ses.id, entryId = card.id, word = card.displayTerm, translation = card.translation,
                    revealed = isRevealed, done = ses.done, total = ses.total, streak = streak,
                    creature = creatureBitmap(s.settings, Mood.HAPPY), creatureName = def?.name, answers = s.settings.islandAnswers,
                ),
                s.settings,
            )
        } else Notifications.cancelIsland(context)
        shownSessionId = ses.id

        val capsuleVisible = s.settings.capsuleEnabled && !s.foreground && canDrawOverlays(context)
        val bmp = creatureBitmap(s.settings, Mood.HAPPY)
        withContext(Dispatchers.Main) {
            if (capsuleVisible) {
                overlay.show(
                    CapsuleOverlay.Model(
                        sessionId = ses.id, entryId = card.id, language = spec.code,
                        word = card.displayTerm, translation = card.translation,
                        courseTitle = "${spec.flag} ${spec.nameRu}", done = ses.done, total = ses.total,
                        animate = s.settings.animations, creature = bmp, answers = s.settings.islandAnswers,
                        colorA = (def?.colorB ?: 0xFF7C72E0).toInt(),
                    )
                )
            } else overlay.hide()
        }
    }

    companion object {
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
