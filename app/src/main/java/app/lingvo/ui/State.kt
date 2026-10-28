package app.lingvo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lingvo.AppContainer
import app.lingvo.core.Languages
import app.lingvo.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.ZoneId

/** Текущее время раз в минуту — чтобы счётчики «к повторению» обновлялись без polling базы. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

@Composable
fun rememberOverviews(container: AppContainer): State<List<LanguageOverview>?> {
    val flow = remember(container) {
        val repo = container.repository
        combine(repo.observeAllCards(), repo.observeLogs(), repo.settings.flow, minuteTicker()) { cards, logs, settings, now ->
            Languages.all.map {
                OverviewCalculator.compute(it, cards, logs, settings.lang(it.code).newPerDay, now, ZoneId.systemDefault())
            }
        }.flowOn(Dispatchers.Default)
    }
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
