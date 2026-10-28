package app.lingvo.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lingvo.AppContainer
import app.lingvo.core.Course
import app.lingvo.core.CreatureDef
import app.lingvo.core.Creatures
import app.lingvo.core.DailyStreak
import app.lingvo.core.DayRecord
import app.lingvo.core.ProgressSnapshot
import app.lingvo.core.StreakState
import app.lingvo.core.UnlockKey
import app.lingvo.core.UnlockKind
import app.lingvo.data.AppSettings
import app.lingvo.data.Card
import app.lingvo.data.LearningSessionEntity
import app.lingvo.data.ProgressCalculator
import app.lingvo.data.ReviewLogEntity
import app.lingvo.data.UnlockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.ZoneId

/** Текущее время раз в минуту — чтобы счётчики «к повторению» и смена дня обновлялись без опроса базы. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

/** Всё, что показывают главные экраны. Считается из реальных данных базы. */
data class AppState(
    val courses: List<Course>,
    val settings: AppSettings,
    val overviews: List<LanguageOverview>,
    val streak: StreakState,
    val days: List<DayRecord>,
    val unlocks: List<UnlockEntity>,
    val owned: Set<UnlockKey>,
    val snapshot: ProgressSnapshot,
    val logs: List<ReviewLogEntity>,
    val sessions: List<LearningSessionEntity>,
    val cards: List<Card>,
    val now: Long,
) {
    val activeCourse: Course? get() = courses.firstOrNull { it.id == settings.activeCourseId } ?: courses.firstOrNull()
    fun overview(courseId: String) = overviews.firstOrNull { it.spec.code == courseId }
    fun isOwned(kind: UnlockKind, id: String) = UnlockKey(kind, id) in owned
    fun unlock(kind: UnlockKind, id: String) = unlocks.firstOrNull { it.kind == kind.name && it.itemId == id }
    fun creatureLevel(id: String) = unlock(UnlockKind.CREATURE, id)?.level ?: 0

    /** Выбранное существо (или первое полученное). */
    val companion: Pair<CreatureDef, Int>?
        get() {
            val ownedCreatures = Creatures.all.filter { isOwned(UnlockKind.CREATURE, it.id) }
            val c = ownedCreatures.firstOrNull { it.id == settings.favoriteCreature } ?: ownedCreatures.firstOrNull() ?: return null
            return c to creatureLevel(c.id).coerceAtLeast(1)
        }
}

private data class Base(
    val courses: List<Course>,
    val cards: List<Card>,
    val logs: List<ReviewLogEntity>,
    val settings: AppSettings,
    val days: List<DayRecord>,
)

fun appStateFlow(container: AppContainer): Flow<AppState> {
    val repo = container.repository
    val base = combine(repo.observeCourses(), repo.observeAllCards(), repo.observeLogs(), repo.settings.flow, repo.observeDays()) { c, cards, logs, s, d ->
        Base(c, cards, logs, s, d)
    }
    return combine(base, repo.observeUnlocks(), repo.observeAllSessions(), minuteTicker()) { b, unlocks, sessions, now ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toEpochDay()
        val streak = DailyStreak.state(b.days, today, b.settings.dailyGoal)
        val owned = unlocks.mapNotNull { u -> runCatching { UnlockKey(UnlockKind.valueOf(u.kind), u.itemId) }.getOrNull() }.toSet()
        val snap = ProgressCalculator.snapshot(b.cards, b.logs, sessions, b.courses.map { it.id }.toSet(), streak, b.settings.listens)
            .copy(
                regions = owned.count { it.kind == UnlockKind.REGION },
                creatures = owned.count { it.kind == UnlockKind.CREATURE },
                achievements = owned.count { it.kind == UnlockKind.ACHIEVEMENT },
            )
        AppState(
            courses = b.courses,
            settings = b.settings,
            overviews = b.courses.map { c ->
                OverviewCalculator.compute(c.spec, b.cards, b.logs, b.settings.lang(c.id).newPerDay, now, zone)
            },
            streak = streak,
            days = b.days,
            unlocks = unlocks,
            owned = owned,
            snapshot = snap,
            logs = b.logs,
            sessions = sessions,
            cards = b.cards,
            now = now,
        )
    }.flowOn(Dispatchers.Default)
}

@Composable
fun rememberAppState(container: AppContainer): State<AppState?> {
    val flow = remember(container) { container.appState }
    return flow.collectAsStateWithLifecycle(initialValue = null)
}

@Composable
fun rememberSettings(container: AppContainer): State<AppSettings> =
    container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())

@Composable
fun rememberToast(): (String) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) { { msg: String -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } }
}

/** Спецификация курса по ID (null — курс ещё загружается или удалён). */
@Composable
fun rememberCourseSpec(container: AppContainer, id: String): app.lingvo.core.LanguageSpec? {
    val state = rememberAppState(container).value
    return remember(state?.courses, id) { state?.courses?.firstOrNull { it.id == id }?.spec }
}
