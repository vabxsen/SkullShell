package dev.aicli.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.aicli.app.di.ViewModelFactory
import dev.aicli.app.ui.design.LocalOpenSettings
import dev.aicli.app.ui.design.SkullTheme
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
import dev.aicli.core.settings.AppearanceSettings
import dev.aicli.core.settings.ThemeMode

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AiCliApplication).container
        val factory = ViewModelFactory(container)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.sessionManager.runningCount.collect { count ->
                    if (count > 0 && Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        val preferences = getSharedPreferences("notification_permission", MODE_PRIVATE)
                        if (!preferences.getBoolean("requested", false)) {
                            preferences.edit().putBoolean("requested", true).apply()
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }

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
            SkullTheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColorEnabled) {
                Box(Modifier.fillMaxSize().background(SkullTheme.colors.bg)) {
                    AppRoot(factory)
                }
            }
        }
    }
}

/** Reuse the existing Settings entry so switching sections does not build a navigation loop. */
private fun openSettings(navController: NavHostController) {
    if (navController.currentDestination?.route == Destinations.SETTINGS) return
    if (!navController.popBackStack(Destinations.SETTINGS, inclusive = false)) {
        navController.navigate(Destinations.SETTINGS) { launchSingleTop = true }
    }
}

private fun openScreen(navController: NavHostController, route: String) {
    navController.navigate(route) { launchSingleTop = true }
}

private fun openHome(navController: NavHostController) {
    if (!navController.popBackStack(Destinations.HOME, inclusive = false)) {
        openScreen(navController, Destinations.HOME)
    }
}

@Composable
private fun AppRoot(factory: ViewModelFactory) {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalOpenSettings provides { openSettings(navController) }) {
        AppNavHost(navController, factory, Modifier.fillMaxSize())
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, factory: ViewModelFactory, modifier: Modifier) {
    val onBack: () -> Unit = { if (!navController.popBackStack()) openHome(navController) }
    NavHost(navController = navController, startDestination = Destinations.HOME, modifier = modifier) {
        composable(Destinations.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onOpenTerminal = { openScreen(navController, Destinations.terminal("new")) },
                onOpenProjects = { openScreen(navController, Destinations.PROJECTS) },
                onOpenSession = { openScreen(navController, Destinations.terminal("resume:" + it)) },
                onOpenProviders = { openScreen(navController, Destinations.PROVIDERS) },
                onOpenSettings = { openSettings(navController) },
                onOpenProject = { openScreen(navController, Destinations.terminal("project:" + it.id)) },
            )
        }
        composable(Destinations.PROJECTS) {
            val vm: ProjectsViewModel = viewModel(factory = factory)
            ProjectsScreen(
                viewModel = vm,
                onOpenProject = { project -> openScreen(navController, Destinations.terminal("project:" + project.id)) },
                onBack = onBack,
            )
        }
        composable(Destinations.TERMINAL) { entry ->
            val vm: TerminalViewModel = viewModel(factory = factory)
            TerminalScreen(viewModel = vm, sessionArg = entry.arguments?.getString("sessionId").orEmpty(),
                onBack = onBack, onOpenSettings = { openSettings(navController) })
        }
        composable(Destinations.PROVIDERS) {
            val vm: ProvidersViewModel = viewModel(factory = factory)
            ProvidersScreen(
                viewModel = vm,
                onLaunchProvider = { openScreen(navController, Destinations.terminal("provider:" + it)) },
                onAuthenticate = { openScreen(navController, Destinations.authenticate(it)) },
                onOpenDiagnostics = { openScreen(navController, Destinations.DIAGNOSTICS) },
                onOpenSettings = { openSettings(navController) },
                onBack = onBack,
            )
        }
        composable(Destinations.AUTHENTICATE) { entry ->
            val vm: AuthenticationViewModel = viewModel(factory = factory)
            AuthenticationScreen(viewModel = vm, providerId = entry.arguments?.getString("providerId").orEmpty(), onDone = onBack)
        }
        composable(Destinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = vm,
                onBack = onBack,
                onOpenHome = { openHome(navController) },
                onOpenProjects = { openScreen(navController, Destinations.PROJECTS) },
                onOpenTerminal = { openScreen(navController, Destinations.terminal("session")) },
                onOpenProviders = { openScreen(navController, Destinations.PROVIDERS) },
                onOpenDiagnostics = { openScreen(navController, Destinations.DIAGNOSTICS) },
            )
        }
        composable(Destinations.DIAGNOSTICS) {
            val vm: DiagnosticsViewModel = viewModel(factory = factory)
            DiagnosticsScreen(viewModel = vm, onBack = onBack)
        }
    }
}
