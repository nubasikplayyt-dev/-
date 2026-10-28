package app.lingvo.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lingvo.AppContainer
import app.lingvo.IncomingImport
import app.lingvo.ui.screens.AddEntryScreen
import app.lingvo.ui.screens.BulkAddScreen
import app.lingvo.ui.screens.DictionaryScreen
import app.lingvo.ui.screens.EntryScreen
import app.lingvo.ui.screens.HomeScreen
import app.lingvo.ui.screens.ImportExportScreen
import app.lingvo.ui.screens.LanguageScreen
import app.lingvo.ui.screens.LearnNewScreen
import app.lingvo.ui.screens.ReviewScreen
import app.lingvo.ui.screens.SettingsScreen
import app.lingvo.ui.screens.StatsScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val HOME = "home"
    const val DICTIONARY = "dictionary?lang={lang}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val LANGUAGE = "language/{lang}"
    const val LEARN = "learn/{lang}"
    const val REVIEW = "review/{lang}"
    const val ENTRY = "entry/{id}"
    const val ADD = "add/{lang}"
    const val BULK = "bulk/{lang}"
    const val IMPORT = "import?lang={lang}"

    fun dictionary(lang: String? = null) = if (lang == null) "dictionary" else "dictionary?lang=$lang"
    fun language(lang: String) = "language/$lang"
    fun learn(lang: String) = "learn/$lang"
    fun review(lang: String) = "review/$lang"
    fun entry(id: String) = "entry/${android.net.Uri.encode(id)}"
    fun add(lang: String) = "add/$lang"
    fun bulk(lang: String) = "bulk/$lang"
    fun import(lang: String? = null) = if (lang == null) "import" else "import?lang=$lang"
}

private data class Tab(val route: String, val base: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "home", "Главная", Icons.Filled.Home),
    Tab(Routes.DICTIONARY, "dictionary", "Словарь", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Routes.STATS, "stats", "Статистика", Icons.Filled.BarChart),
    Tab(Routes.SETTINGS, "settings", "Настройки", Icons.Filled.Settings),
)

@Composable
fun LingvoNavHost(
    container: AppContainer,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
    pendingImport: StateFlow<IncomingImport?>,
    onImportConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val route by pendingRoute.collectAsStateWithLifecycle()
    val incoming by pendingImport.collectAsStateWithLifecycle()

    LaunchedEffect(route) {
        route?.let {
            if (it != Routes.HOME) nav.navigate(it) { launchSingleTop = true }
            onRouteConsumed()
        }
    }
    LaunchedEffect(incoming) {
        if (incoming != null) nav.navigate(Routes.import()) { launchSingleTop = true }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        // Отступы системных панелей обрабатывают сами экраны.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { nav.navigateTab(tab.base) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.HOME) { HomeScreen(container, nav) }
            composable(Routes.DICTIONARY, arguments = listOf(navArgument("lang") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                DictionaryScreen(container, nav, it.arguments?.getString("lang"))
            }
            composable(Routes.STATS) { StatsScreen(container) }
            composable(Routes.SETTINGS) { SettingsScreen(container, nav) }
            composable(Routes.LANGUAGE) { LanguageScreen(container, nav, it.arguments?.getString("lang") ?: "es") }
            composable(Routes.LEARN) { LearnNewScreen(container, nav, it.arguments?.getString("lang") ?: "es") }
            composable(Routes.REVIEW) { ReviewScreen(container, nav, it.arguments?.getString("lang") ?: "es") }
            composable(Routes.ENTRY) { EntryScreen(container, nav, android.net.Uri.decode(it.arguments?.getString("id").orEmpty())) }
            composable(Routes.ADD) { AddEntryScreen(container, nav, it.arguments?.getString("lang") ?: "es") }
            composable(Routes.BULK) { BulkAddScreen(container, nav, it.arguments?.getString("lang") ?: "es") }
            composable(Routes.IMPORT, arguments = listOf(navArgument("lang") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                ImportExportScreen(container, nav, it.arguments?.getString("lang"), incoming, onImportConsumed)
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
