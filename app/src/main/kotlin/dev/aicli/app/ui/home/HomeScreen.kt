package dev.aicli.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aicli.app.BuildConfig
import dev.aicli.app.ui.components.DotMatrixText
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text

/**
 * Deliberately a title page rather than a dashboard. Current project / providers / recent
 * projects each have their own destination now, and nothing has been moved back here to fill
 * the space: an app whose entire job is "open a terminal" is better served by one unmistakable
 * action than by a wall of cards duplicating the other tabs.
 *
 * The dot-matrix wordmark is the app's only piece of ornament, and it earns its place - an LED
 * signage grid is the one graphic device that is *natively* monochrome, so it belongs to this
 * palette instead of being tinted into it. The tagline set between two rules is the same
 * typographic move as [dev.aicli.app.ui.design.SectionHeader], scaled up.
 */
@Composable
fun HomeScreen(onOpenTerminal: () -> Unit) {
    Screen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Metrics.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f))

            DotMatrixText(text = "SkullShell", modifier = Modifier.fillMaxWidth(0.78f))

            Row(
                modifier = Modifier.fillMaxWidth(0.86f).padding(top = Space.x8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Rule(Modifier.weight(1f))
                Label(
                    "Terminal + AI agents",
                    color = SkullTheme.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = Space.x4),
                )
                Rule(Modifier.weight(1f))
            }

            PrimaryButton(
                label = "Open terminal",
                onClick = onOpenTerminal,
                glyph = Glyphs.Terminal,
                modifier = Modifier.padding(top = Space.x8),
            )

            Box(Modifier.weight(1f))

            Text(
                "v" + BuildConfig.VERSION_NAME,
                style = SkullTheme.type.monoSm,
                color = SkullTheme.colors.inkFaint,
                modifier = Modifier.padding(bottom = Space.x5),
            )
        }
    }
}
