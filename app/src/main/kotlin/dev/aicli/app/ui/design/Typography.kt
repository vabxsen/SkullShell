package dev.aicli.app.ui.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val MaterialTypography = Typography()

@Immutable
data class SkullType(
    val display: TextStyle, val title: TextStyle, val heading: TextStyle,
    val body: TextStyle, val bodySm: TextStyle, val label: TextStyle,
    val labelSm: TextStyle, val mono: TextStyle, val monoSm: TextStyle,
)

val SkullTypography = SkullType(
    display = MaterialTypography.headlineLarge,
    title = MaterialTypography.headlineSmall,
    heading = MaterialTypography.titleMedium,
    body = MaterialTypography.bodyLarge,
    bodySm = MaterialTypography.bodyMedium,
    label = MaterialTypography.labelLarge,
    labelSm = MaterialTypography.labelMedium,
    mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
    monoSm = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
)
