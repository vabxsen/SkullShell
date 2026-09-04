@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.aicli.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/** Shared Settings entry point for the app's screen toolbars. */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun Screen(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {},
           floatingActionButton: @Composable () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = topBar, floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.surface, contentWindowInsets = WindowInsets.navigationBars) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), content = content)
    }
}

@Composable
fun TopBar(crumb: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null,
           showSettings: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    val title = crumb.substringAfterLast("/ ").let { if (it == "Home") "SkullShell" else it }
    val openSettings = LocalOpenSettings.current
    TopAppBar(modifier = modifier, title = {
        androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }, navigationIcon = {
        if (onBack != null) IconAction(Glyphs.ChevronLeft, "Back", onBack)
    }, actions = {
        actions()
        if (showSettings && openSettings != null) IconAction(Glyphs.Sliders, "Settings", openSettings)
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
}
