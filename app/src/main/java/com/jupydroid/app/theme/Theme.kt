package com.jupydroid.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The editor tokens in scope. Static because the palette changes only with the system theme -
 * a whole-tree recomposition then is correct and is not something to optimise around.
 */
val LocalEditorPalette = staticCompositionLocalOf { DarculaEditorPalette }

/**
 * IntelliJ's New UI, dark and light, following the system.
 *
 * **Dynamic colour is deliberately not offered.** It was on before, which meant the wallpaper
 * chose the syntax colours: `NUMBER` resolved to `colorScheme.secondary` and on some wallpapers
 * landed close enough to the body text to be unreadable (`docs/PLAN.md`, "Known residual risks").
 * A palette whose contrast can be asserted is worth more here than one that matches the launcher,
 * because the thing being coloured is code.
 *
 * ```
 * JupyterDroidTheme { EditorScreen(...) }
 * ```
 */
@Composable
fun JupyterDroidTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val palette = if (darkTheme) DarculaEditorPalette else IntelliJLightEditorPalette
  CompositionLocalProvider(LocalEditorPalette provides palette) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      typography = Typography,
      content = content,
    )
  }
}

/** The palette in scope, for call sites that read one token and would rather not name the local. */
val editorPalette: EditorPalette
  @Composable @ReadOnlyComposable get() = LocalEditorPalette.current

/**
 * The Material roles, mapped onto the same ramp the editor tokens come from, so app chrome
 * (bars, menus, dialogs, FAB) sits in the same world as the cells rather than beside it.
 *
 * **Every reachable role is set, and that is not thoroughness for its own sake.** Whatever is
 * left out keeps `darkColorScheme()`'s default, and those defaults are the Material *baseline
 * purple* - a colour from a different design system. Setting most of them is what put a purple
 * FAB on Home beside a Darcula editor: `FloatingActionButton` reaches for `primaryContainer`,
 * which nothing had set. `IntelliJColorSchemeTest` enumerates them so the next omission fails a
 * test rather than waiting to be noticed in a screenshot.
 */
internal val DarkColorScheme: ColorScheme = with(IntelliJRamp) {
  darkColorScheme(
    primary = DarkBlue6,
    onPrimary = DarkGray14,
    primaryContainer = DarkBlue1,
    onPrimaryContainer = DarkBlue11,
    inversePrimary = LightBlue4,
    secondary = DarkGray9,
    onSecondary = DarkGray1,
    secondaryContainer = DarkGray3,
    onSecondaryContainer = DarkGray11,
    tertiary = DarkGreen7,
    onTertiary = DarkGray1,
    tertiaryContainer = DarkGreen1,
    onTertiaryContainer = DarkGreen11,
    background = DarkGray1,
    onBackground = DarkGray11,
    surface = DarkGray1,
    onSurface = DarkGray11,
    surfaceVariant = DarkGray2,
    onSurfaceVariant = DarkGray10,
    surfaceTint = DarkBlue6,
    inverseSurface = DarkGray12,
    inverseOnSurface = DarkGray1,
    surfaceDim = DarkGray1,
    surfaceBright = DarkGray4,
    surfaceContainerLowest = DarkGray1,
    surfaceContainerLow = DarkGray2,
    surfaceContainer = DarkGray2,
    surfaceContainerHigh = DarkGray3,
    surfaceContainerHighest = DarkGray4,
    outline = DarkGray5,
    outlineVariant = DarkGray3,
    scrim = DarkGray1,
    error = DarkRed7,
    onError = DarkGray14,
    errorContainer = DarkRed1,
    onErrorContainer = DarkRed10,
  )
}

internal val LightColorScheme: ColorScheme = with(IntelliJRamp) {
  lightColorScheme(
    primary = LightBlue4,
    onPrimary = LightGray14,
    primaryContainer = LightBlue11,
    onPrimaryContainer = LightBlue2,
    inversePrimary = DarkBlue11,
    secondary = LightGray6,
    onSecondary = LightGray14,
    secondaryContainer = LightGray12,
    onSecondaryContainer = LightGray2,
    tertiary = LightGreen5,
    onTertiary = LightGray14,
    tertiaryContainer = LightGreen10,
    onTertiaryContainer = LightGreen1,
    background = LightGray14,
    onBackground = LightGray1,
    surface = LightGray14,
    onSurface = LightGray2,
    surfaceVariant = LightGray13,
    onSurfaceVariant = LightGray5,
    surfaceTint = LightBlue4,
    inverseSurface = LightGray2,
    inverseOnSurface = LightGray14,
    surfaceDim = LightGray11,
    surfaceBright = LightGray14,
    surfaceContainerLowest = LightGray14,
    surfaceContainerLow = LightGray13,
    surfaceContainer = LightGray13,
    surfaceContainerHigh = LightGray12,
    surfaceContainerHighest = LightGray11,
    outline = LightGray8,
    outlineVariant = LightGray11,
    scrim = LightGray1,
    error = LightRed4,
    onError = LightGray14,
    errorContainer = LightRed10,
    onErrorContainer = LightRed1,
  )
}
