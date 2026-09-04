package dev.aicli.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * True when the app shell is already drawing a bottom navigation bar underneath the current
 * screen. [Screen] reads it to decide whether *it* owes the content a navigation-bar inset:
 * exactly one of the two applies the inset, never both (which is how you get a dead strip above
 * the gesture bar) and never neither (content under the system bar).
 */
val LocalBottomBar = staticCompositionLocalOf { false }

/**
 * The app-wide screen frame, replacing Material's Scaffold. Deliberately much smaller than one:
 * a top bar slot, a content area, and the window-inset rule above. No FAB slot, no snackbar
 * host, no elevation - this design has neither.
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val bottomBarPresent = LocalBottomBar.current
    Column(modifier.fillMaxSize().background(SkullTheme.colors.bg)) {
        topBar()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .then(if (bottomBarPresent) Modifier else Modifier.navigationBarsPadding()),
            content = content,
        )
    }
}

/**
 * A quiet header. The screen's real title is set large in the content by [PageTitle]; the bar
 * carries only a breadcrumb in tracked uppercase mono ("SKULLSHELL / PROJECTS"), the back
 * affordance, and actions. Keeping the bar this light is what lets the content own the page.
 */
@Composable
fun TopBar(
    crumb: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(SkullTheme.colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.topBar)
                .padding(start = if (onBack != null) Space.x2 else Metrics.gutter, end = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconAction(
                    icon = Glyphs.ChevronLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    tint = SkullTheme.colors.ink,
                    modifier = Modifier.padding(end = Space.x1),
                )
            }
            Label(crumb, color = SkullTheme.colors.inkMuted, modifier = Modifier.weight(1f))
            actions()
        }
        Rule()
    }
}

/**
 * Phone navigation. Selection is shown twice - full-strength ink and a round-ended marker above
 * the glyph - because in a palette with no accent colour, one signal is not enough to survive a
 * glance.
 */
@Composable
fun NavBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(SkullTheme.colors.bg).navigationBarsPadding()) {
        Rule()
        Row(
            modifier = Modifier.fillMaxWidth().height(Metrics.navBar),
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
    }
}

@Composable
fun RowScope.NavBarItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = SkullTheme.colors
    val tint by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.inkFaint,
        animationSpec = tween(160),
        label = "navTint",
    )
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight().pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = Space.x1)
                .width(26.dp)
                .height(3.dp)
                .clip(Shapes.pill)
                .background(if (selected) colors.ink else Color.Transparent),
        )
        Spacer(Modifier.weight(1f))
        Glyph(icon, label, size = Metrics.glyphMd, tint = tint)
        Label(
            label,
            style = SkullTheme.type.labelSm,
            color = tint,
            modifier = Modifier.padding(top = Space.x1),
        )
        Spacer(Modifier.weight(1f))
    }
}

/** Wide-screen navigation: the same rules turned 90 degrees, marker on the leading edge. */
@Composable
fun NavRail(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Row(modifier.fillMaxHeight().background(SkullTheme.colors.bg)) {
        Column(
            modifier = Modifier
                .width(Metrics.rail)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = Space.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        VRule(Modifier.fillMaxHeight())
    }
}

@Composable
fun ColumnScope.NavRailItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = SkullTheme.colors
    val tint by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.inkFaint,
        animationSpec = tween(160),
        label = "railTint",
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(Metrics.touch + Space.x4).pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(Shapes.pill)
                .background(if (selected) colors.ink else Color.Transparent),
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Glyph(icon, label, size = Metrics.glyphMd, tint = tint)
            Label(
                label,
                style = SkullTheme.type.labelSm,
                color = tint,
                modifier = Modifier.padding(top = Space.x1),
            )
        }
    }
}
