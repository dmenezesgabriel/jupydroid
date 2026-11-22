package com.jupydroid.app.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.extension.api.LocalFocusRingColor

/**
 * The editor tokens in scope. Static because the palette changes only with the system theme -
 * a whole-tree recomposition then is correct and is not something to optimise around.
 */
val LocalEditorPalette = staticCompositionLocalOf { DarkEditorPalette }

/**
 * The workbench parts in scope - the rail's and the side panels' own surfaces.
 *
 * Beside [LocalEditorPalette] rather than folded into it, and static for the same reason: a part's
 * colour changes only with the system theme.
 */
val LocalChromePalette = staticCompositionLocalOf { DarkChromePalette }

/**
 * The sixteen ANSI colours, and the 256 around them, that a cell's output paints its escapes with -
 * VS Code's `terminal.ansi*` ramp, which its notebook renderer uses for the same job
 * (vscode@3f3380c6 `extensions/notebook-renderers/src/colorMap.ts`).
 *
 * The ramp itself stays in `terminal/` (ADR-0020); only the choice of light or dark is made here,
 * from the same `darkTheme` as every other palette, so it follows the app's theme override.
 */
val LocalAnsiPalette = staticCompositionLocalOf { TerminalPalette.VS_CODE_DARK }

/**
 * The app's theme: the editor palette and the Material scheme that matches it, dark or light,
 * following the system.
 *
 * **Dynamic colour is deliberately not offered.** It was on before, which meant the wallpaper
 * chose the syntax colours: `NUMBER` resolved to `colorScheme.secondary` and on some wallpapers
 * landed close enough to the body text to be unreadable. The palette states its colours instead,
 * and `EditorPaletteTest` pins their contrast (ADR-0042).
 * A palette whose contrast can be asserted is worth more here than one that matches the launcher,
 * because the thing being coloured is code.
 *
 * **Both halves answer to VS Code.** The Material scheme below is read from the resolved 2026
 * themes through [ColorRamp]; the editor palette is transcribed from the same two files. VS Code
 * answers for presentation (ADR-0040).
 *
 * ```
 * JupydroidTheme { EditorScreen(...) }
 * ```
 */
@Composable
fun JupydroidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val palette = if (darkTheme) DarkEditorPalette else LightEditorPalette
  val chrome = if (darkTheme) DarkChromePalette else LightChromePalette
  val ansi = if (darkTheme) TerminalPalette.VS_CODE_DARK else TerminalPalette.VS_CODE_LIGHT
  CompositionLocalProvider(
    LocalEditorPalette provides palette,
    LocalChromePalette provides chrome,
    LocalFocusRingColor provides chrome.focusBorder,
    LocalAnsiPalette provides ansi,
  ) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      typography = JupydroidTypography,
    ) {
      // Inside MaterialTheme, which provides its own ripple as `LocalIndication`: this one wraps it.
      CompositionLocalProvider(
        LocalIndication provides FocusRingIndication(chrome.focusBorder, ripple()),
        content = content,
      )
    }
  }
}

/** The palette in scope, for call sites that read one token and would rather not name the local. */
val editorPalette: EditorPalette
  @Composable @ReadOnlyComposable
  get() = LocalEditorPalette.current

/** The parts in scope, the same convenience [editorPalette] is for the document's own colours. */
val chromePalette: ChromePalette
  @Composable @ReadOnlyComposable
  get() = LocalChromePalette.current

/**
 * The Material roles, mapped onto the same ramp the app's chrome comes from, so bars, menus,
 * dialogs and the explorer sit in the same world as the cells rather than beside them.
 *
 * **Every reachable role is set, and that is not thoroughness for its own sake.** Whatever is left
 * out keeps `darkColorScheme()`'s default, and those defaults are the Material *baseline purple* -
 * a colour from a different design system. Setting most of them is what put a purple FAB on Home
 * beside a Darcula editor: `FloatingActionButton` reaches for `primaryContainer`, which nothing
 * had set. `ColorSchemeTest` enumerates them so the next omission fails a test rather than waiting
 * to be noticed in a screenshot.
 *
 * **Two mappings depart from the id first named for them, and measurement is why** - both are
 * recorded in ADR-0058:
 *
 * - `primary` is `textLink.foreground`, not `button.background`. VS Code splits what Material
 *   fuses - one value is a fill under `button.foreground`, the other is the accent drawn *on* a
 *   surface - and this app draws `primary` as text, an icon tint or a caret in all nine of its
 *   sites and as a filled button in none. `button.background` sits at 3.88:1 there; this is
 *   6.31:1. The fill pair is kept intact on `primaryContainer` / `onPrimaryContainer`.
 * - `outline` is `checkbox.border`, not `panel.border`. Material's `outline` bounds a component,
 *   which WCAG 1.4.11 asks 3:1 of; `panel.border` is a separator and measures 1.31:1. VS Code has
 *   both kinds of border, so each takes the Material role that means the same thing and
 *   `outlineVariant` - the decorative divider - is where `panel.border` goes.
 */
internal val DarkColorScheme: ColorScheme = with(ColorRamp) {
  darkColorScheme(
    primary = DarkBlue2,
    onPrimary = DarkGray1,
    primaryContainer = DarkBlue1,
    onPrimaryContainer = DarkGray10,
    inversePrimary = LightBlue1,
    secondary = DarkGray8,
    onSecondary = DarkGray1,
    secondaryContainer = DarkGray6,
    onSecondaryContainer = DarkGray9,
    tertiary = DarkGreen1,
    onTertiary = DarkGray1,
    tertiaryContainer = DarkGray6,
    onTertiaryContainer = DarkGray9,
    background = DarkGray1,
    onBackground = DarkGray9,
    surface = DarkGray1,
    onSurface = DarkGray9,
    surfaceVariant = DarkGray2,
    onSurfaceVariant = DarkGray8,
    surfaceTint = DarkBlue2,
    inverseSurface = DarkGray9,
    inverseOnSurface = DarkGray1,
    surfaceDim = DarkGray1,
    surfaceBright = DarkGray5,
    surfaceContainerLowest = DarkGray1,
    surfaceContainerLow = DarkGray2,
    surfaceContainer = DarkGray2,
    surfaceContainerHigh = DarkGray3,
    surfaceContainerHighest = DarkGray4,
    outline = DarkGray7,
    outlineVariant = DarkGray5,
    scrim = Black,
    error = DarkRed2,
    onError = DarkGray1,
    errorContainer = DarkRed1,
    onErrorContainer = DarkRed2,
  )
}

/**
 * Light's accent is one value where dark's is two: `button.background` and `textLink.foreground`
 * are both `#0069CC` here, so `primary` and `primaryContainer` would collapse onto each other.
 * The container role takes `inputValidation.infoBackground` - the theme's own pale blue - so that
 * a container is still a container.
 */
internal val LightColorScheme: ColorScheme = with(ColorRamp) {
  lightColorScheme(
    primary = LightBlue1,
    onPrimary = LightGray8,
    primaryContainer = LightBlue2,
    onPrimaryContainer = LightBlue1,
    inversePrimary = DarkBlue2,
    secondary = LightGray2,
    onSecondary = LightGray8,
    secondaryContainer = LightGray4,
    onSecondaryContainer = LightGray1,
    tertiary = LightGreen1,
    onTertiary = LightGray8,
    tertiaryContainer = LightGray5,
    onTertiaryContainer = LightGray1,
    background = LightGray8,
    onBackground = LightGray1,
    surface = LightGray8,
    onSurface = LightGray1,
    surfaceVariant = LightGray7,
    onSurfaceVariant = LightGray2,
    surfaceTint = LightBlue1,
    inverseSurface = LightGray1,
    inverseOnSurface = LightGray8,
    surfaceDim = LightGray5,
    surfaceBright = LightGray8,
    surfaceContainerLowest = LightGray8,
    surfaceContainerLow = LightGray7,
    surfaceContainer = LightGray7,
    surfaceContainerHigh = LightGray6,
    surfaceContainerHighest = LightGray5,
    outline = LightGray3,
    outlineVariant = LightGray6,
    scrim = Black,
    error = LightRed2,
    onError = LightGray8,
    errorContainer = LightRed1,
    onErrorContainer = LightRed2,
  )
}
