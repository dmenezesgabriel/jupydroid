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
import com.jupydroid.app.theme.IntelliJRamp.DarkBlue6
import com.jupydroid.app.theme.IntelliJRamp.DarkGray1
import com.jupydroid.app.theme.IntelliJRamp.DarkGray10
import com.jupydroid.app.theme.IntelliJRamp.DarkGray11
import com.jupydroid.app.theme.IntelliJRamp.DarkGray2
import com.jupydroid.app.theme.IntelliJRamp.DarkGray3
import com.jupydroid.app.theme.IntelliJRamp.DarkGray4
import com.jupydroid.app.theme.IntelliJRamp.DarkGray5
import com.jupydroid.app.theme.IntelliJRamp.DarkGray9
import com.jupydroid.app.theme.IntelliJRamp.DarkGreen7
import com.jupydroid.app.theme.IntelliJRamp.DarkRed1
import com.jupydroid.app.theme.IntelliJRamp.DarkRed10
import com.jupydroid.app.theme.IntelliJRamp.DarkRed7
import com.jupydroid.app.theme.IntelliJRamp.LightBlue4
import com.jupydroid.app.theme.IntelliJRamp.LightGray1
import com.jupydroid.app.theme.IntelliJRamp.LightGray11
import com.jupydroid.app.theme.IntelliJRamp.LightGray12
import com.jupydroid.app.theme.IntelliJRamp.LightGray13
import com.jupydroid.app.theme.IntelliJRamp.LightGray14
import com.jupydroid.app.theme.IntelliJRamp.LightGray2
import com.jupydroid.app.theme.IntelliJRamp.LightGray5
import com.jupydroid.app.theme.IntelliJRamp.LightGray6
import com.jupydroid.app.theme.IntelliJRamp.LightGray8
import com.jupydroid.app.theme.IntelliJRamp.LightGreen5
import com.jupydroid.app.theme.IntelliJRamp.LightRed1
import com.jupydroid.app.theme.IntelliJRamp.LightRed10
import com.jupydroid.app.theme.IntelliJRamp.LightRed4

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
 */
private val DarkColorScheme: ColorScheme =
  darkColorScheme(
    primary = DarkBlue6,
    onPrimary = LightGray14,
    secondary = DarkGray9,
    onSecondary = DarkGray1,
    tertiary = DarkGreen7,
    onTertiary = DarkGray1,
    background = DarkGray1,
    onBackground = DarkGray11,
    surface = DarkGray1,
    onSurface = DarkGray11,
    surfaceVariant = DarkGray2,
    onSurfaceVariant = DarkGray10,
    surfaceContainer = DarkGray2,
    surfaceContainerHigh = DarkGray3,
    surfaceContainerHighest = DarkGray4,
    outline = DarkGray5,
    outlineVariant = DarkGray3,
    error = DarkRed7,
    onError = LightGray14,
    errorContainer = DarkRed1,
    onErrorContainer = DarkRed10,
  )

private val LightColorScheme: ColorScheme =
  lightColorScheme(
    primary = LightBlue4,
    onPrimary = LightGray14,
    secondary = LightGray6,
    onSecondary = LightGray14,
    tertiary = LightGreen5,
    onTertiary = LightGray14,
    background = LightGray14,
    onBackground = LightGray1,
    surface = LightGray14,
    onSurface = LightGray2,
    surfaceVariant = LightGray13,
    onSurfaceVariant = LightGray5,
    surfaceContainer = LightGray13,
    surfaceContainerHigh = LightGray12,
    surfaceContainerHighest = LightGray11,
    outline = LightGray8,
    outlineVariant = LightGray11,
    error = LightRed4,
    onError = LightGray14,
    errorContainer = LightRed10,
    onErrorContainer = LightRed1,
  )
