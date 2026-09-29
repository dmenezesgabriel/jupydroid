package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three dynamic slots (foreground, background, cursor) [TerminalPalette.VS_CODE_DARK] and
 * [TerminalPalette.VS_CODE_LIGHT] answer for their own surface, rather than
 * [TerminalPalette.REFERENCE]'s black-on-white placeholders, `TerminalPalette.kt`'s own
 * KDoc: "P4i replaces them once the screen exists to answer for its own surface."
 *
 * Values are VS Code's own, walked through its runtime fallback rather than a single theme file:
 * `terminal.foreground` is registered non-null (`terminalColorRegistry.ts:19-24`) and neither 2026
 * theme overrides it, so each resolves to its include chain's value - `dark_modern.json:114`
 * (`#CCCCCC`, unchanged through `2026-dark.json`) and `light_modern.json:133` (`#3B3B3B`, unchanged
 * through `2026-light.json`). `terminal.background` and `terminalCursor.foreground` are registered
 * `null` (`:18,25`) and resolved by `TerminalInstanceColorProvider.getBackgroundColor` /
 * `xtermTerminal.ts:1095-1096` (`vscode@3f3380c6`): a cursor foreground falls back
 * to the terminal's own foreground, and a background - this app's terminal being its own screen
 * rather than an embedded panel, so the reference's "Editor location" branch is the fitting one -
 * falls back to `editor.background`. Dark states its own `terminal.background`/`terminalCursor.foreground`
 * outright (`2026-dark.json:241,244`, `#191A1B`/`#bfbfbf`); light states only the cursor
 * (`2026-light.json:249`, `#202020`) and never `terminal.background` anywhere in its own include
 * chain, so its background is [com.jupydroid.app.theme.LightEditorPalette.editorBackground].
 */
class TerminalPaletteTest {
  @Test
  fun darkResolvesItsOwnSurfaceRatherThanReferencesPlaceholders() {
    assertEquals(0xFFCCCCCC.toInt(), TerminalPalette.VS_CODE_DARK.at(CellStyle.COLOR_INDEX_FOREGROUND))
    assertEquals(0xFF191A1B.toInt(), TerminalPalette.VS_CODE_DARK.at(CellStyle.COLOR_INDEX_BACKGROUND))
    assertEquals(0xFFBFBFBF.toInt(), TerminalPalette.VS_CODE_DARK.at(CellStyle.COLOR_INDEX_CURSOR))
  }

  @Test
  fun lightResolvesItsOwnSurfaceRatherThanReferencesPlaceholders() {
    assertEquals(0xFF3B3B3B.toInt(), TerminalPalette.VS_CODE_LIGHT.at(CellStyle.COLOR_INDEX_FOREGROUND))
    assertEquals(0xFFFFFFFF.toInt(), TerminalPalette.VS_CODE_LIGHT.at(CellStyle.COLOR_INDEX_BACKGROUND))
    assertEquals(0xFF202020.toInt(), TerminalPalette.VS_CODE_LIGHT.at(CellStyle.COLOR_INDEX_CURSOR))
  }

  /** [TerminalPalette.REFERENCE] is the differential's own scheme and must stay untouched. */
  @Test
  fun referenceKeepsItsOwnPlaceholders() {
    assertEquals(0xFFFFFFFF.toInt(), TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_FOREGROUND))
    assertEquals(0xFF000000.toInt(), TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_BACKGROUND))
    assertEquals(0xFFFFFFFF.toInt(), TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_CURSOR))
  }
}
