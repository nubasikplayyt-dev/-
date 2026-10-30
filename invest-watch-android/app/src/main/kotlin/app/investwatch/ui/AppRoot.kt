package app.investwatch.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType

private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_MARKET = "market"
private const val ROUTE_ASSET = "asset"

/**
 * Нижняя панель: «Избранное», по вкладке на каждый рынок из watchlist.json
 * и «Настройки». Рынки не зашиты в код — появится новый, появится и вкладка.
 */
@Composable
fun AppRoot(
    openAssetId: String?,
    onOpenHandled: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val model: MainViewModel = hiltViewModel()
    val theme by model.theme.collectAsStateWithLifecycle()
    val dynamicColors by model.dynamicColors.collectAsStateWithLifecycle()

    InvestWatchTheme(mode = theme, dynamicColors = dynamicColors) {
        val navController = rememberNavController()
        val watchlist by model.watchlist.collectAsStateWithLifecycle()
        val sync by model.sync.collectAsStateWithLifecycle()
        val snackbars = remember { SnackbarHostState() }

        LaunchedEffect(openAssetId) {
            if (!openAssetId.isNullOrBlank()) {
                navController.navigate("$ROUTE_ASSET/$openAssetId")
                onOpenHandled()
            }
        }

        LaunchedEffect(sync.error) {
            sync.error?.let {
                snackbars.showSnackbar(it)
                model.dismissError()
            }
        }

        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination

        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute.isOn(ROUTE_FAVORITES),
                        onClick = { navController.switchTo(ROUTE_FAVORITES) },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        label = { Text("Избранное") },
                    )

                    for (market in watchlist.markets) {
                        NavigationBarItem(
                            selected = currentRoute.isOn("$ROUTE_MARKET/${market.id}"),
                            onClick = { navController.switchTo("$ROUTE_MARKET/${market.id}") },
                            icon = { Text(market.flag) },
                            label = { Text(market.title) },
                        )
                    }

                    NavigationBarItem(
                        selected = currentRoute.isOn(ROUTE_SETTINGS),
                        onClick = { navController.switchTo(ROUTE_SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Настройки") },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_FAVORITES,
                modifier = Modifier.padding(padding),
            ) {
                composable(ROUTE_FAVORITES) {
                    FavoritesScreen(
                        model = model,
                        onOpenAsset = { navController.navigate("$ROUTE_ASSET/$it") },
                    )
                }

                composable(
                    route = "$ROUTE_MARKET/{marketId}",
                    arguments = listOf(navArgument("marketId") { type = NavType.StringType }),
                ) { entry ->
                    MarketScreen(
                        model = model,
                        marketId = entry.arguments?.getString("marketId").orEmpty(),
                        onOpenAsset = { navController.navigate("$ROUTE_ASSET/$it") },
                        onRequestNotifications = onRequestNotifications,
                    )
                }

                composable(
                    route = "$ROUTE_ASSET/{assetId}",
                    arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
                ) {
                    AssetScreen(
                        mainModel = model,
                        onBack = { navController.popBackStack() },
                        onRequestNotifications = onRequestNotifications,
                    )
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(model = model)
                }
            }
        }
    }
}

private fun androidx.navigation.NavDestination?.isOn(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

/** Переход по нижней панели: без стопки одинаковых экранов. */
private fun androidx.navigation.NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
