package dev.aicli.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A modal. Material's AlertDialog is a floating rounded card lifted by a shadow; with no
 * elevation in this system, separation has to come from the scrim and a hard [SkullColors.ink]
 * border instead - the dialog is the one surface that gets the strong border, which is what
 * makes it read as "on top of" rather than "part of" the page.
 */
@Composable
fun Modal(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SkullTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = modifier
                .padding(horizontal = Space.x6)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(Shapes.modal)
                .background(colors.bg)
                .border(Metrics.hairline, colors.lineStrong, Shapes.modal),
        ) {
            Column(Modifier.padding(Space.x6)) {
                Label(title, color = colors.inkMuted)
                Rule(Modifier.padding(top = Space.x3, bottom = Space.x4))
                content()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.x3, end = Space.x3, bottom = Space.x3),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * A bottom sheet, built from a full-screen [Dialog] rather than a Material ModalBottomSheet.
 * It is anchored to the bottom edge and rounded only where it leaves the screen, so the curve
 * reads as the sheet lifting off the page rather than as a floating card. There is no drag
 * handle because it is dismissed by its own action, not by a swipe.
 */
@Composable
fun Sheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SkullTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = dismissOnScrimTap,
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(Shapes.sheet)
                    .background(colors.bg)
                    .border(Metrics.hairline, colors.line, Shapes.sheet)
                    .navigationBarsPadding(),
            ) {
                Column(Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x5), content = content)
            }
        }
    }
}

/**
 * The overflow menu: a hairline panel anchored to its trigger. Uses a raw [Popup] because
 * DropdownMenu is a Material component, and its scale-from-corner entrance would be the one
 * piece of Material motion left in the app.
 *
 * The width is bounded at both ends, which is load-bearing rather than cosmetic: a popup is
 * measured against the *window*, so the `fillMaxWidth` inside each separating [Rule] would
 * otherwise stretch the whole menu across the screen. The vertical offset drops it clear of the
 * trigger it is anchored to.
 */
@Composable
fun Menu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItem>,
) {
    if (!expanded) return
    val colors = SkullTheme.colors
    val dropBelow = with(LocalDensity.current) { Metrics.touch.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, dropBelow),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 260.dp)
                .clip(Shapes.small)
                .background(colors.bg)
                .border(Metrics.hairline, colors.lineStrong, Shapes.small),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) Rule()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable { onDismiss(); item.onClick() }
                        .padding(horizontal = Space.x4, vertical = Space.x3),
                ) {
                    Label(item.label, color = colors.ink)
                }
            }
        }
    }
}

/** One row of a [Menu]. */
data class MenuItem(val label: String, val onClick: () -> Unit)
