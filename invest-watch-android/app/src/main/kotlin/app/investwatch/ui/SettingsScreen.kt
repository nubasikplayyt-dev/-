package app.investwatch.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.investwatch.core.QuietHours
import app.investwatch.core.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Настройки: тема, уведомления, токены и состояние синхронизации. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(model: MainViewModel) {
    val context = LocalContext.current
    val theme by model.theme.collectAsStateWithLifecycle()
    val dynamicColors by model.dynamicColors.collectAsStateWithLifecycle()
    val notifications by model.notificationsEnabled.collectAsStateWithLifecycle()
    val quiet by model.quietHours.collectAsStateWithLifecycle()
    val lastSync by model.lastSync.collectAsStateWithLifecycle()
    val sync by model.sync.collectAsStateWithLifecycle()
    val demo by model.demoMode.collectAsStateWithLifecycle()

    var gitHubToken by remember { mutableStateOf("") }
    var priceKey by remember { mutableStateOf("") }
    var priceKeyCheck by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { SectionTitle("Тема") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = theme == mode,
                                onClick = { model.setTheme(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Цвета системы", style = MaterialTheme.typography.bodyMedium)
                                Hint("Material You: приложение подхватывает палитру обоев")
                            }
                            Switch(checked = dynamicColors, onCheckedChange = model::setDynamicColors)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Уведомления") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Сообщать о событиях", style = MaterialTheme.typography.bodyMedium)
                            Hint("Только по избранным бумагам, по полю notify")
                        }
                        Switch(checked = notifications, onCheckedChange = model::setNotificationsEnabled)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Тихие часы", style = MaterialTheme.typography.bodyMedium)
                            Hint(
                                "С ${QuietHours.format(quiet.fromMinutes)} " +
                                    "до ${QuietHours.format(quiet.toMinutes)}",
                            )
                        }
                        Switch(
                            checked = quiet.enabled,
                            onCheckedChange = { model.setQuietHours(quiet.copy(enabled = it)) },
                        )
                    }

                    if (quiet.enabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(21, 22, 23, 0).forEach { hour ->
                                FilterChip(
                                    selected = quiet.fromMinutes == hour * 60,
                                    onClick = { model.setQuietHours(quiet.copy(fromMinutes = hour * 60)) },
                                    label = { Text("с %02d:00".format(hour)) },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(6, 7, 8, 9).forEach { hour ->
                                FilterChip(
                                    selected = quiet.toMinutes == hour * 60,
                                    onClick = { model.setQuietHours(quiet.copy(toMinutes = hour * 60)) },
                                    label = { Text("до %02d:00".format(hour)) },
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { openBatterySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Исключить из экономии батареи")
                    }
                    Hint(
                        "Android может откладывать фоновые проверки. Исключение из " +
                            "оптимизации делает их регулярнее.",
                    )
                }
            }
        }

        item { SectionTitle("Доступ к репозиторию") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Hint(
                        model.gitHubTokenMask()?.let { "Токен сохранён: $it" }
                            ?: "Токен не задан — данные не загрузятся",
                    )
                    OutlinedTextField(
                        value = gitHubToken,
                        onValueChange = { gitHubToken = it },
                        label = { Text("Fine-grained токен GitHub") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = {
                                model.saveGitHubToken(gitHubToken.trim())
                                gitHubToken = ""
                            },
                            enabled = gitHubToken.isNotBlank(),
                        ) {
                            Text("Сохранить")
                        }
                        TextButton(onClick = model::clearGitHubToken) { Text("Удалить") }
                    }
                    Hint(
                        "Нужны права Contents: read and write только на репозиторий " +
                            "invest-watch. Запись — для config/favorites.json.",
                    )
                }
            }
        }

        item { SectionTitle("Ключ источника цен") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Hint(
                        "Россия — ISS Мосбиржи, ключ не нужен. США и Китай — EODHD, " +
                            "там нужен ключ тарифа с мировыми биржами.",
                    )
                    Hint(model.priceKeyMask()?.let { "Ключ сохранён: $it" } ?: "Ключ не задан")
                    OutlinedTextField(
                        value = priceKey,
                        onValueChange = { priceKey = it },
                        label = { Text("Ключ EODHD") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Три кнопки в одну строку не влезают на узком экране,
                    // поэтому переносим их, а не рвём подпись по слогам.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = {
                                model.savePriceKey(priceKey.trim())
                                priceKey = ""
                            },
                            enabled = priceKey.isNotBlank(),
                        ) {
                            Text("Сохранить")
                        }
                        OutlinedButton(
                            onClick = {
                                model.checkPriceKey(priceKey.trim()) {
                                    priceKeyCheck = it ?: "Ключ работает"
                                }
                            },
                            enabled = priceKey.isNotBlank(),
                        ) {
                            Text("Проверить")
                        }
                        TextButton(onClick = model::clearPriceKey) { Text("Удалить") }
                    }
                    priceKeyCheck?.let { Hint(it) }
                }
            }
        }

        item { SectionTitle("Синхронизация") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Hint(
                        if (lastSync == 0L) "Ещё не синхронизировалось"
                        else "Последняя: " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))
                            .format(Date(lastSync)),
                    )
                    Button(
                        onClick = { model.refresh(force = true) },
                        enabled = !sync.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (sync.running) "Обновляю…" else "Обновить сейчас")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Демонстрационные данные", style = MaterialTheme.typography.bodyMedium)
                            Hint("Экраны на встроенном срезе репозитория, без сети и токена")
                        }
                        Switch(checked = demo, onCheckedChange = model::setDemoMode)
                    }
                }
            }
        }

        item { SectionTitle("О приложении") }
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Hint(
                        "Приложение показывает только факты и оценки по критериям владельца " +
                            "из репозитория invest-watch. Своих оценок, рейтингов, целевых цен " +
                            "и прогнозов здесь нет. Это не инвестиционная рекомендация.",
                    )
                }
            }
        }
    }
}

/** Системный экран исключения из оптимизации батареи. */
private fun openBatterySettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
