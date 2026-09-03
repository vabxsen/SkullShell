package dev.aicli.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * extraSmall = chips/tags/compact items, small = buttons/controls/terminal keycaps,
 * medium = cards/dialogs/input containers, large = bottom sheets/major surfaces,
 * extraLarge = rare prominent surfaces (FAB).
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
