package dev.aicli.app.ui.theme

import androidx.compose.ui.graphics.Color

// The M3 ColorScheme itself comes from Compose's own official Material 3 Baseline tokens
// (androidx.compose.material3.lightColorScheme()/darkColorScheme() with no overrides) or, when
// enabled, the platform's dynamic (wallpaper-derived) palette — see Theme.kt. This file only
// holds the two semantic roles Material 3 doesn't define (success/warning), tinted to read as
// authentically Google: the same green/amber used across Android system UI and Google's own
// apps (Play Store install states, Drive sync status, etc.), not an invented brand hue.
val SuccessLight = Color(0xFF146C2E)
val OnSuccessLight = Color(0xFFFFFFFF)
val SuccessContainerLight = Color(0xFFB6F2AF)
val OnSuccessContainerLight = Color(0xFF002204)

val WarningLight = Color(0xFF825500)
val OnWarningLight = Color(0xFFFFFFFF)
val WarningContainerLight = Color(0xFFFFDDB1)
val OnWarningContainerLight = Color(0xFF291800)

val SuccessDark = Color(0xFF88D982)
val OnSuccessDark = Color(0xFF00390A)
val SuccessContainerDark = Color(0xFF005313)
val OnSuccessContainerDark = Color(0xFFA4F69C)

val WarningDark = Color(0xFFFFB955)
val OnWarningDark = Color(0xFF452B00)
val WarningContainerDark = Color(0xFF633F00)
val OnWarningContainerDark = Color(0xFFFFDDB1)
