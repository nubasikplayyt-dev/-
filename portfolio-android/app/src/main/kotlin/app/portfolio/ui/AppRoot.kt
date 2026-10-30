package app.portfolio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private sealed interface Screen {
    data object Home : Screen
    data class AssetDetails(val assetId: String) : Screen
    data object Settings : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(model: AppViewModel, openAssetId: String?, onOpenHandled: () -> Unit) {
    PortfolioTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }

        // Нажатие на уведомление открывает экран нужной бумаги.
        LaunchedEffect(openAssetId) {
            if (openAssetId != null) {
                screen = Screen.AssetDetails(openAssetId)
                onOpenHandled()
            }
        }

        val state by model.state.collectAsStateWithLifecycle()
        val seen by model.seenEvents.collectAsStateWithLifecycle()
        val muted by model.mutedAssets.collectAsStateWithLifecycle()
        val config by model.repoConfig.collectAsStateWithLifecycle()
        val notificationsEnabled by model.notificationsEnabled.collectAsStateWithLifecycle()
        val interval by model.checkIntervalHours.collectAsStateWithLifecycle()
        val lastCheck by model.lastCheck.collectAsStateWithLifecycle()

        val snackbars = remember { SnackbarHostState() }
        LaunchedEffect(state.error) {
            state.error?.let { snackbars.showSnackbar(it) }
        }

        BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (val current = screen) {
                                is Screen.Home -> "Портфель по критериям"
                                is Screen.Settings -> "Настройки"
                                is Screen.AssetDetails ->
                                    state.data?.watchlist?.find(current.assetId)
                                        ?.second?.displayTicker ?: current.assetId
                            },
                        )
                    },
                    navigationIcon = {
                        if (screen !is Screen.Home) {
                            IconButton(onClick = { screen = Screen.Home }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    actions = {
                        if (screen is Screen.Home) {
                            IconButton(onClick = { model.load(force = true) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                            }
                            IconButton(onClick = { screen = Screen.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = "Настройки")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val data = state.data

            when {
                data == null && state.loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    CircularProgressIndicator()
                }

                data == null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    Text(
                        text = state.error ?: "Данные не загрузились",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> when (val current = screen) {
                    is Screen.Home -> HomeScreen(
                        data = data,
                        config = config,
                        contentPadding = padding,
                        hasUnread = { assetId ->
                            app.portfolio.core.NewEvents.hasUnread(data.eventsOf(assetId), seen)
                        },
                        onOpenAsset = { screen = Screen.AssetDetails(it) },
                    )

                    is Screen.AssetDetails -> AssetScreen(
                        data = data,
                        assetId = current.assetId,
                        config = config,
                        contentPadding = padding,
                        muted = current.assetId in muted,
                        onMutedChange = { model.setMuted(current.assetId, it) },
                        onSeen = { model.markAssetSeen(current.assetId) },
                    )

                    is Screen.Settings -> SettingsScreen(
                        config = config,
                        notificationsEnabled = notificationsEnabled,
                        intervalHours = interval,
                        lastCheck = lastCheck,
                        contentPadding = padding,
                        onRepoChange = model::setRepo,
                        onNotificationsChange = model::setNotificationsEnabled,
                        onIntervalChange = model::setCheckIntervalHours,
                    )
                }
            }
        }
    }
}
