package dev.aicli.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.components.DotMatrixText
import dev.aicli.app.ui.theme.Dimens

/**
 * Intentionally minimal — Current project / AI providers / Recent projects previously lived
 * here but now have their own dedicated screens (Projects, Providers); nothing has replaced
 * them on Home yet by deliberate choice, not oversight. The app name lives here as a centered
 * dot-matrix mark instead of a top-bar title. No Settings action either — that's already a
 * permanent bottom-nav/rail destination, so a duplicate top-bar shortcut would be redundant.
 */
@Composable
fun HomeScreen(onOpenTerminal: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = {}) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotMatrixText(
                    text = "SkullShell",
                    modifier = Modifier.fillMaxWidth(0.7f).padding(horizontal = 24.dp),
                )
                Button(onClick = onOpenTerminal, modifier = Modifier.padding(top = Dimens.space32)) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.padding(end = Dimens.space8))
                    Text("Open Terminal")
                }
            }
        }
    }
}
