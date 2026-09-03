package dev.aicli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.aicli.app.ui.diagnostics.DiagnosticsScreen
import dev.aicli.app.ui.diagnostics.DiagnosticsViewModel
import dev.aicli.app.ui.home.HomeScreen
import dev.aicli.app.ui.home.HomeViewModel
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
import dev.aicli.app.ui.theme.AiCliTheme
import dev.aicli.core.settings.AppearanceSettings
import dev.aicli.core.settings.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AiCliApplication).container
        val factory = ViewModelFactory(container)

        // TerminalSessionService is started by SessionManager.createSession once an actual
        // session exists — not eagerly here, which would show a misleading "0 active sessions"
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
            AiCliTheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColorEnabled) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(factory)
                }
            }
        }
    }
}

/** Width at/above which a [NavigationRail] replaces the bottom [NavigationBar] (large phone
 *  landscape, foldable unfolded, tablet). Matches the common Material breakpoint for "expanded". */
private val RAIL_BREAKPOINT = 600.dp

private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector)

// Phone bottom bar: Terminal is deliberately excluded — it's reached by action (opening a
// project/provider/session), not a permanent tab, so it can claim the screen's full height
// instead of permanently losing a strip to a 5th tab item. Diagnostics stays reachable from
// Settings/Home rather than taking a 6th slot.
private val phoneDestinations = listOf(
    TopLevelDestination(Destinations.HOME, "Home", Icons.Filled.Home),
    TopLevelDestination(Destinations.PROJECTS, "Projects", Icons.Filled.Folder),
    TopLevelDestination(Destinations.PROVIDERS, "Providers", Icons.Filled.Apps),
    TopLevelDestination(Destinations.SETTINGS, "Settings", Icons.Filled.Settings),
)

// Wide-screen rail: a rail coexists with content instead of consuming height the terminal
// needs, so Terminal earns a permanent slot here that it doesn't get on phone width.
private val railDestinations = listOf(
    TopLevelDestination(Destinations.HOME, "Home", Icons.Filled.Home),
    TopLevelDestination(Destinations.PROJECTS, "Projects", Icons.Filled.Folder),
    TopLevelDestination(Destinations.TERMINAL, "Terminal", Icons.Filled.Terminal),
    TopLevelDestination(Destinations.PROVIDERS, "Providers", Icons.Filled.Apps),
    TopLevelDestination(Destinations.SETTINGS, "Settings", Icons.Filled.Settings),
)

private fun navigateTopLevel(navController: NavHostController, destination: TopLevelDestination) {
    val route = if (destination.route == Destinations.TERMINAL) Destinations.terminal("session") else destination.route
    navController.navigate(route) {
        popUpTo(Destinations.HOME) { saveState = true }
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
            Scaffold(
                bottomBar = {
                    if (currentRoute == null || phoneDestinations.any { it.route == currentRoute }) {
                        NavigationBar {
                            phoneDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { navigateTopLevel(navController, destination) },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                AppNavHost(navController, factory, Modifier.padding(padding))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    railDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigateTopLevel(navController, destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
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
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onOpenProjects = { navController.navigate(Destinations.PROJECTS) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onOpenDiagnostics = { navController.navigate(Destinations.DIAGNOSTICS) },
                onLaunchProvider = { providerId ->
                    // Launch against the app's default workspace root; a project-scoped launch
                    // happens from ProjectsScreen instead.
                    navController.navigate(Destinations.terminal("provider:$providerId"))
                },
                onOpenProject = { navController.navigate(Destinations.PROJECTS) },
            )
        }
        composable(Destinations.PROJECTS) {
            val vm: ProjectsViewModel = viewModel(factory = factory)
            ProjectsScreen(
                viewModel = vm,
                onOpenProject = { project -> navController.navigate(Destinations.terminal("project:${project.id}")) },
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
                onLaunchProvider = { providerId -> navController.navigate(Destinations.terminal("provider:$providerId")) },
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
            DiagnosticsScreen(viewModel = vm)
        }
    }
}
