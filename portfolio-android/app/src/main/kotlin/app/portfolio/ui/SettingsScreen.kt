package app.portfolio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.portfolio.core.RepoConfig

/** Настройки: откуда читать данные и как часто проверять. */
@Composable
fun SettingsScreen(
    config: RepoConfig,
    notificationsEnabled: Boolean,
    intervalHours: Long,
    lastCheck: Long,
    contentPadding: PaddingValues,
    onRepoChange: (String, String, String) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onIntervalChange: (Long) -> Unit,
) {
    var owner by remember(config) { mutableStateOf(config.owner) }
    var repo by remember(config) { mutableStateOf(config.repo) }
    var branch by remember(config) { mutableStateOf(config.branch) }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle("Уведомления")
        Card {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Проверять в фоне", style = MaterialTheme.typography.bodyMedium)
                        Hint("Приложение само смотрит репозиторий и сообщает о новых событиях")
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChange)
                }

                if (notificationsEnabled) {
                    Text("Как часто", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1L, 3L, 6L, 12L).forEach { hours ->
                            FilterChip(
                                selected = intervalHours == hours,
                                onClick = { onIntervalChange(hours) },
                                label = { Text("${hours} ч") },
                            )
                        }
                    }
                    Hint(
                        "Точное время выбирает система: при экономии батареи проверка может " +
                            "задержаться. Постоянных служб приложение не держит.",
                    )
                }

                Hint(
                    if (lastCheck == 0L) "Пока не проверялось"
                    else "Последняя проверка: " + java.text.SimpleDateFormat(
                        "d MMM yyyy, HH:mm",
                        java.util.Locale("ru"),
                    ).format(java.util.Date(lastCheck)),
                )
            }
        }

        SectionTitle("Репозиторий данных")
        Card {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Владелец") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Репозиторий") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Ветка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onRepoChange(owner, repo, branch) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Сохранить и перечитать")
                }
                Hint(
                    "Читается только публичный репозиторий. Бумаги и разделы берутся из " +
                        "config/watchlist.json — чтобы добавить бумагу, правьте репозиторий, " +
                        "а не приложение.",
                )
            }
        }

        SectionTitle("О приложении")
        Card {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Hint(
                    "Приложение только читает репозиторий мониторинга и ничего в него не пишет. " +
                        "Это не инвестиционная рекомендация.",
                )
            }
        }
    }
}
