package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.AxtraThemeStyle
import com.example.core.UiLanguage

val LocalAxtraPalette = compositionLocalOf { CyberBluePalette }

private val AxtraShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AxtraTheme(
    themeStyle: AxtraThemeStyle = AxtraThemeStyle.CYBER_BLUE,
    uiLanguage: UiLanguage = UiLanguage.ENGLISH,
    fontScaleMultiplier: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val palette = when (themeStyle) {
        AxtraThemeStyle.CYBER_BLUE -> CyberBluePalette
        AxtraThemeStyle.ROYAL_GOLD -> RoyalGoldPalette
        AxtraThemeStyle.MATRIX_GREEN -> MatrixGreenPalette
        AxtraThemeStyle.PURE_AMOLED -> PureAmoledPalette
    }

    val colorScheme = darkColorScheme(
        primary = palette.primaryNeon,
        onPrimary = palette.backgroundTop,
        secondary = palette.secondaryAccent,
        onSecondary = palette.textPrimary,
        tertiary = palette.goldAccent,
        background = palette.backgroundTop,
        onBackground = palette.textPrimary,
        surface = palette.surfaceGlass,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        error = palette.errorRed
    )

    val layoutDirection = if (uiLanguage == UiLanguage.URDU) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAxtraPalette provides palette,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildAxtraTypography(fontScaleMultiplier),
            shapes = AxtraShapes,
            content = content
        )
    }
}
