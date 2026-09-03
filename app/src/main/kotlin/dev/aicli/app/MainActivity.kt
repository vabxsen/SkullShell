package dev.aicli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import dev.aicli.app.ui.settings.SettingsScreen
import dev.aicli.app.ui.settings.SettingsViewModel
import dev.aicli.app.ui.terminal.TerminalScreen
import dev.aicli.app.ui.terminal.TerminalViewModel
import dev.aicli.app.ui.theme.AiCliTheme

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
            AiCliTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(factory)
                }
            }
        }
    }
}

private data class TopLevelDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Destinations.HOME, "Home", Icons.Filled.Home),
    TopLevelDestination(Destinations.SETTINGS, "Settings", Icons.Filled.Settings),
    TopLevelDestination(Destinations.DIAGNOSTICS, "Diagnostics", Icons.Filled.MonitorHeart),
)

@Composable
private fun AppRoot(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == null || topLevelDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destinations.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = Destinations.HOME, modifier = Modifier.padding(padding)) {
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
}
