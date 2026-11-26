package dev.opendungeon.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPaperColors = staticCompositionLocalOf { PaperColors.Light }
private val LocalPaperType = staticCompositionLocalOf { PaperType.Default }

/** Reads the paper theme inside [PaperTheme]. */
public object Paper {
    public val colors: PaperColors
        @Composable @ReadOnlyComposable
        get() = LocalPaperColors.current

    public val type: PaperType
        @Composable @ReadOnlyComposable
        get() = LocalPaperType.current
}

/**
 * The app's theme: light paper first, with a dark variant. Material components underneath get a
 * matching colour scheme and the sans, so screens built from them sit on the same page.
 */
@Composable
public fun PaperTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) PaperColors.Dark else PaperColors.Light
    CompositionLocalProvider(LocalPaperColors provides colors, LocalPaperType provides PaperType.Default) {
        MaterialTheme(colorScheme = colors.toColorScheme(darkTheme), typography = SansTypography, content = content)
    }
}

private fun PaperColors.toColorScheme(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = ink,
    onPrimary = onInk,
    secondary = muted,
    onSecondary = onInk,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = card,
    onSurfaceVariant = muted,
    surfaceContainer = card,
    surfaceContainerLow = card,
    surfaceContainerHigh = card,
    outline = hairline,
    outlineVariant = divider,
)

private val SansTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PaperFonts.Serif),
        displayMedium = displayMedium.copy(fontFamily = PaperFonts.Serif),
        displaySmall = displaySmall.copy(fontFamily = PaperFonts.Serif),
        headlineLarge = headlineLarge.copy(fontFamily = PaperFonts.Serif),
        headlineMedium = headlineMedium.copy(fontFamily = PaperFonts.Serif),
        headlineSmall = headlineSmall.copy(fontFamily = PaperFonts.Serif),
        titleLarge = titleLarge.copy(fontFamily = PaperFonts.Sans),
        titleMedium = titleMedium.copy(fontFamily = PaperFonts.Sans),
        titleSmall = titleSmall.copy(fontFamily = PaperFonts.Sans),
        bodyLarge = bodyLarge.copy(fontFamily = PaperFonts.Sans),
        bodyMedium = bodyMedium.copy(fontFamily = PaperFonts.Sans),
        bodySmall = bodySmall.copy(fontFamily = PaperFonts.Sans),
        labelLarge = labelLarge.copy(fontFamily = PaperFonts.Sans),
        labelMedium = labelMedium.copy(fontFamily = PaperFonts.Sans),
        labelSmall = labelSmall.copy(fontFamily = PaperFonts.Sans),
    )
}
