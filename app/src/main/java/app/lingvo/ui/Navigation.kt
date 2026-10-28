package app.lingvo.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
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
import app.lingvo.ui.screens.AchievementsScreen
import app.lingvo.ui.screens.AddEntryScreen
import app.lingvo.ui.screens.BulkAddScreen
import app.lingvo.ui.screens.CelebrationHost
import app.lingvo.ui.screens.CourseEditScreen
import app.lingvo.ui.screens.CoursesScreen
import app.lingvo.ui.screens.CreatureScreen
import app.lingvo.ui.screens.CreaturesScreen
import app.lingvo.ui.screens.DictionaryScreen
import app.lingvo.ui.screens.EntryScreen
import app.lingvo.ui.screens.HomeScreen
import app.lingvo.ui.screens.IconsScreen
import app.lingvo.ui.screens.ImportExportScreen
import app.lingvo.ui.screens.LanguageScreen
import app.lingvo.ui.screens.LearnNewScreen
import app.lingvo.ui.screens.OnboardingScreen
import app.lingvo.ui.screens.ReviewScreen
import app.lingvo.ui.screens.SettingsAppearanceScreen
import app.lingvo.ui.screens.SettingsDataScreen
import app.lingvo.ui.screens.SettingsLearningScreen
import app.lingvo.ui.screens.SettingsRemindersScreen
import app.lingvo.ui.screens.SettingsScreen
import app.lingvo.ui.screens.StatsScreen
import app.lingvo.ui.screens.StreakScreen
import app.lingvo.ui.screens.WorldScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val HOME = "home"
    const val DICTIONARY = "dictionary?lang={lang}"
    const val WORLD = "world"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    const val ONBOARDING = "onboarding"
    const val COURSE_NEW = "course/new"
    const val COURSES = "courses"
    const val COURSE_EDIT = "course/edit/{id}"
    const val LANGUAGE = "language/{lang}"
    const val LEARN = "learn/{lang}"
    const val REVIEW = "review/{lang}"
    const val ENTRY = "entry/{id}"
    const val ADD = "add/{lang}"
    const val BULK = "bulk/{lang}"
    const val IMPORT = "import?lang={lang}"
    const val ACHIEVEMENTS = "achievements"
    const val STREAK = "streak"
    const val CREATURES = "creatures"
    const val CREATURE = "creature/{id}"
    const val ICONS = "icons"
    const val SETTINGS_LEARNING = "settings/learning"
    const val SETTINGS_REMINDERS = "settings/reminders"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_DATA = "settings/data"

    fun dictionary(lang: String? = null) = if (lang == null) "dictionary" else "dictionary?lang=$lang"
    fun language(lang: String) = "language/$lang"
    fun learn(lang: String) = "learn/$lang"
    fun review(lang: String) = "review/$lang"
    fun entry(id: String) = "entry/${android.net.Uri.encode(id)}"
    fun add(lang: String) = "add/$lang"
    fun bulk(lang: String) = "bulk/$lang"
    fun import(lang: String? = null) = if (lang == null) "import" else "import?lang=$lang"
    fun courseEdit(id: String) = "course/edit/${android.net.Uri.encode(id)}"
    fun creature(id: String) = "creature/$id"
}

/** Основные разделы нижней панели: у каждого — иконка и подпись. */
enum class Tab(val route: String, val base: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "home", "Главная", Icons.Filled.Home),
    DICTIONARY(Routes.DICTIONARY, "dictionary", "Словарь", Icons.AutoMirrored.Filled.MenuBook),
    WORLD(Routes.WORLD, "world", "Мир", Icons.Filled.Public),
    STATS(Routes.STATS, "stats", "Статистика", Icons.Filled.BarChart),
    SETTINGS(Routes.SETTINGS, "settings", "Настройки", Icons.Filled.Settings),
}

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
    val state by rememberAppState(container)

    // Первый запуск: курсов нет — пользователь сам выбирает языки (ничего не создаётся автоматически).
    LaunchedEffect(state?.courses?.isEmpty()) {
        if (state?.courses?.isEmpty() == true && nav.currentDestination?.route != Routes.ONBOARDING) {
            nav.navigate(Routes.ONBOARDING) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } }
        }
    }
    LaunchedEffect(route) {
        route?.let {
            if (it == Routes.HOME) nav.goHome() else nav.navigate(it) { launchSingleTop = true }
            onRouteConsumed()
        }
    }
    LaunchedEffect(incoming) {
        if (incoming != null) nav.navigate(Routes.import()) { launchSingleTop = true }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = Tab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Остров занятия — над вкладками на любом экране, кроме самого занятия и знакомства со словами.
            if (destination?.route !in setOf(Routes.REVIEW, Routes.LEARN, Routes.ONBOARDING, null)) {
                InAppIsland(container, nav, if (currentTab == null) Modifier.navigationBarsPadding() else Modifier)
            }
            if (currentTab != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { nav.navigateTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
          }
        },
    ) { padding ->
        NavHost(
            nav, startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) { HomeScreen(container, nav) }
            composable(Routes.DICTIONARY, arguments = listOf(navArgument("lang") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                DictionaryScreen(container, nav, it.arguments?.getString("lang"))
            }
            composable(Routes.WORLD) { WorldScreen(container, nav) }
            composable(Routes.STATS) { StatsScreen(container, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(container, nav) }

            composable(Routes.ONBOARDING) { OnboardingScreen(container, nav, firstRun = true) }
            composable(Routes.COURSE_NEW) { OnboardingScreen(container, nav, firstRun = false) }
            composable(Routes.COURSES) { CoursesScreen(container, nav) }
            composable(Routes.COURSE_EDIT) { CourseEditScreen(container, nav, android.net.Uri.decode(it.arguments?.getString("id").orEmpty())) }
            composable(Routes.LANGUAGE) { LanguageScreen(container, nav, it.arguments?.getString("lang").orEmpty()) }
            composable(Routes.LEARN) { LearnNewScreen(container, nav, it.arguments?.getString("lang").orEmpty()) }
            composable(Routes.REVIEW) { ReviewScreen(container, nav, it.arguments?.getString("lang").orEmpty()) }
            composable(Routes.ENTRY) { EntryScreen(container, nav, android.net.Uri.decode(it.arguments?.getString("id").orEmpty())) }
            composable(Routes.ADD) { AddEntryScreen(container, nav, it.arguments?.getString("lang").orEmpty()) }
            composable(Routes.BULK) { BulkAddScreen(container, nav, it.arguments?.getString("lang").orEmpty()) }
            composable(Routes.IMPORT, arguments = listOf(navArgument("lang") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                ImportExportScreen(container, nav, it.arguments?.getString("lang"), incoming, onImportConsumed)
            }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(container, nav) }
            composable(Routes.STREAK) { StreakScreen(container, nav) }
            composable(Routes.CREATURES) { CreaturesScreen(container, nav) }
            composable(Routes.CREATURE) { CreatureScreen(container, nav, it.arguments?.getString("id").orEmpty()) }
            composable(Routes.ICONS) { IconsScreen(container, nav) }
            composable(Routes.SETTINGS_LEARNING) { SettingsLearningScreen(container, nav) }
            composable(Routes.SETTINGS_REMINDERS) { SettingsRemindersScreen(container, nav) }
            composable(Routes.SETTINGS_APPEARANCE) { SettingsAppearanceScreen(container, nav) }
            composable(Routes.SETTINGS_DATA) { SettingsDataScreen(container, nav) }
        }
    }
    // Праздничные окна для новых достижений, областей, существ и иконок — поверх любого экрана.
    if (currentTab != null || destination?.route == Routes.REVIEW) CelebrationHost(container, nav)
}

/**
 * Переход к главной — всегда возврат к стартовому экрану (без restoreState).
 *
 * Причина старой ошибки: переход по вкладке делал popUpTo(главная){saveState} + restoreState.
 * Navigation при неинклюзивном popUpTo привязывает сохранённый стек вкладки к ID самой главной,
 * поэтому navigate(главная){restoreState} восстанавливал вкладку, и главная открывалась только «Назад».
 */
fun NavHostController.goHome() {
    if (!popBackStack(Routes.HOME, inclusive = false)) {
        navigate(Routes.HOME) { popUpTo(graph.findStartDestination().id) { inclusive = true }; launchSingleTop = true }
    }
}

/** Вкладки хранят своё состояние; главная всегда открывается сразу; повторное нажатие не плодит копии. */
fun NavHostController.navigateTab(tab: Tab) {
    if (tab == Tab.HOME) { goHome(); return }
    navigate(tab.base) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
