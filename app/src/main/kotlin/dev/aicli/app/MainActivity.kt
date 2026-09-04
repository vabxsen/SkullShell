package dev.aicli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.aicli.app.di.ViewModelFactory
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.LocalBottomBar
import dev.aicli.app.ui.design.NavBar
import dev.aicli.app.ui.design.NavBarItem
import dev.aicli.app.ui.design.NavRail
import dev.aicli.app.ui.design.NavRailItem
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.diagnostics.DiagnosticsScreen
import dev.aicli.app.ui.diagnostics.DiagnosticsViewModel
import dev.aicli.app.ui.home.HomeScreen
import dev.aicli.app.ui.nav.Destinations
import dev.aicli.app.ui.projects.ProjectsScreen
import dev.aicli.app.ui.projects.ProjectsViewModel
import dev.aicli.app.ui.providers.AuthenticationScreen
import dev.aicli.app.ui.providers.AuthenticationViewModel
import dev.aicli.app.ui.providers.ProvidersScreen
import dev.aicli.app.ui.providers.ProvidersViewModel
import dev.aicli.app.ui.settings.SettingsScreen
import dev.aicli.app.ui.settings.SettingsViewModel
import dev.aicli.app.ui.terminal.TerminalScreen
import dev.aicli.app.ui.terminal.TerminalViewModel
import dev.aicli.core.settings.AppearanceSettings
import dev.aicli.core.settings.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AiCliApplication).container
        val factory = ViewModelFactory(container)

        // TerminalSessionService is started by SessionManager.createSession once an actual
        // session exists - not eagerly here, which would show a misleading "0 active sessions"
        // notification on every app launch (see SessionManager.kt).

        setContent {
            val appearance by container.settingsRepository.appearanceSettings
                .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SkullTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize().background(SkullTheme.colors.bg)) {
                    AppRoot(factory)
                }
            }
        }
    }
}

/** Width at/above which a [NavRail] replaces the bottom [NavBar] (large phone landscape,
 *  foldable unfolded, tablet). */
private val RAIL_BREAKPOINT = 600.dp

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val glyph: ImageVector,
)

// Phone bottom bar: Terminal is deliberately excluded - it is reached by action (opening a
// project/provider/session), not a permanent tab, so it can claim the screen's full height
// instead of permanently losing a strip to a fifth item. Diagnostics stays reachable from
// Providers rather than taking a sixth slot.
//
// There is one glyph per destination rather than a filled/outlined pair: this icon set is drawn
// as a single stroke weight, so selection is carried by ink level and the slot marker instead of
// by swapping in a heavier shape.
private val phoneDestinations = listOf(
    TopLevelDestination(Destinations.HOME, "Home", Glyphs.Home),
    TopLevelDestination(Destinations.PROJECTS, "Projects", Glyphs.Folder),
    TopLevelDestination(Destinations.PROVIDERS, "Providers", Glyphs.Grid),
    TopLevelDestination(Destinations.SETTINGS, "Settings", Glyphs.Sliders),
)

// Wide-screen rail: a rail coexists with content instead of consuming height the terminal
// needs, so Terminal earns a permanent slot here that it does not get at phone width.
private val railDestinations = listOf(
    TopLevelDestination(Destinations.HOME, "Home", Glyphs.Home),
    TopLevelDestination(Destinations.PROJECTS, "Projects", Glyphs.Folder),
    TopLevelDestination(Destinations.TERMINAL, "Terminal", Glyphs.Terminal),
    TopLevelDestination(Destinations.PROVIDERS, "Providers", Glyphs.Grid),
    TopLevelDestination(Destinations.SETTINGS, "Settings", Glyphs.Sliders),
)

private fun navigateTopLevel(navController: NavHostController, destination: TopLevelDestination) {
    val route = if (destination.route == Destinations.TERMINAL) Destinations.terminal("session") else destination.route
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppRoot(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < RAIL_BREAKPOINT) {
            val showNavBar = currentRoute == null || phoneDestinations.any { it.route == currentRoute }
            // Exactly one of the shell and the screen owns the navigation-bar inset; see
            // LocalBottomBar. When the bar is hidden (terminal, sign-in, diagnostics) the screen
            // insets itself instead.
            CompositionLocalProvider(LocalBottomBar provides showNavBar) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().weight(1f)) {
                        AppNavHost(navController, factory, Modifier.fillMaxSize())
                    }
                    if (showNavBar) {
                        NavBar {
                            phoneDestinations.forEach { destination ->
                                NavBarItem(
                                    selected = currentRoute == destination.route,
                                    icon = destination.glyph,
                                    label = destination.label,
                                    onClick = { navigateTopLevel(navController, destination) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavRail {
                    railDestinations.forEach { destination ->
                        NavRailItem(
                            selected = currentRoute == destination.route,
                            icon = destination.glyph,
                            label = destination.label,
                            onClick = { navigateTopLevel(navController, destination) },
                        )
                    }
                }
                AppNavHost(navController, factory, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, factory: ViewModelFactory, modifier: Modifier) {
    NavHost(navController = navController, startDestination = Destinations.HOME, modifier = modifier) {
        composable(Destinations.HOME) {
            HomeScreen(onOpenTerminal = { navController.navigate(Destinations.terminal("session")) })
        }
        composable(Destinations.PROJECTS) {
            val vm: ProjectsViewModel = viewModel(factory = factory)
            ProjectsScreen(
                viewModel = vm,
                onOpenProject = { project -> navController.navigate(Destinations.terminal("project:" + project.id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.TERMINAL) { backStackEntry2 ->
            val vm: TerminalViewModel = viewModel(factory = factory)
            val sessionArg = backStackEntry2.arguments?.getString("sessionId").orEmpty()
            TerminalScreen(viewModel = vm, sessionArg = sessionArg, onBack = { navController.popBackStack() })
        }
        composable(Destinations.PROVIDERS) {
            val vm: ProvidersViewModel = viewModel(factory = factory)
            ProvidersScreen(
                viewModel = vm,
                onLaunchProvider = { providerId -> navController.navigate(Destinations.terminal("provider:" + providerId)) },
                onAuthenticate = { providerId -> navController.navigate(Destinations.authenticate(providerId)) },
                onOpenDiagnostics = { navController.navigate(Destinations.DIAGNOSTICS) },
            )
        }
        composable(Destinations.AUTHENTICATE) { backStackEntry3 ->
            val vm: AuthenticationViewModel = viewModel(factory = factory)
            val providerId = backStackEntry3.arguments?.getString("providerId").orEmpty()
            AuthenticationScreen(viewModel = vm, providerId = providerId, onDone = { navController.popBackStack() })
        }
        composable(Destinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(viewModel = vm)
        }
        composable(Destinations.DIAGNOSTICS) {
            val vm: DiagnosticsViewModel = viewModel(factory = factory)
            DiagnosticsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
