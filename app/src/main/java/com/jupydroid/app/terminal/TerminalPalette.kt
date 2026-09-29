package com.jupydroid.app.terminal

/**
 * The 259 colours a terminal starts with and resets to: the sixteen ANSI entries, the 216-colour
 * cube, the 24 greys, and the three the shell calls foreground, background and cursor.
 *
 * Transcribed from `TerminalColorScheme.DEFAULT_COLORSCHEME` (termux-app `3df69d1d`, Apache 2.0 -
 * see [TerminalGrid]), with **two departures**.
 *
 * **It is a value, not a process-global static.** Upstream `TerminalColors.COLOR_SCHEME` is one
 * mutable array shared by every emulator in the process, which `updateWith(Properties)` rewrites
 * from a theme file. Here the palette is a constructor argument, because this app's ANSI ramp is
 * **theme-dependent** - VS Code registers `terminal.ansiGreen` as `#107C10` light and `#0DBC79`
 * dark - and `terminal/` may not reach into `theme/` to ask which. [VS_CODE_DARK] and
 * [VS_CODE_LIGHT] are that ramp, resolved; [REFERENCE] is what the differential compares
 * against and stays Termux's own scheme.
 *
 * **The cube and the greys are generated, not transcribed.** They are arithmetic - six levels per
 * channel and an even ramp - and 240 literals would be 240 chances to mistype one. [CUBE_LEVELS]
 * and the grey step are checked against the reference's own table at its corners and seams in
 * `TerminalColorsTest`. That is the call P4e-1 made for the East Asian width ranges.
 *
 * ```
 * TerminalPalette.REFERENCE.at(1)   // 0xFFCD0000, the reference's dim red
 * ```
 */
class TerminalPalette(private val defaults: IntArray) {
  init {
    require(defaults.size == CellStyle.INDEXED_COLOR_COUNT) {
      "a palette is ${CellStyle.INDEXED_COLOR_COUNT} colours, was ${defaults.size}"
    }
  }

  /** The default colour at [index], as opaque `0xFFRRGGBB`. */
  fun at(index: Int): Int = defaults[index]

  /** Fill [target] with these defaults; it must be the same length. */
  fun copyInto(target: IntArray) {
    defaults.copyInto(target)
  }

  companion object {
    /** The six levels each channel of the 216-colour cube takes, in order. */
    private val CUBE_LEVELS = intArrayOf(0, 0x5F, 0x87, 0xAF, 0xD7, 0xFF)

    /**
     * The sixteen ANSI entries, dim then bright. Termux's own choice - a palette value rather
     * than arithmetic - and what the differential in `TerminalColorsTest` compares against, so
     * it stays exactly this.
     */
    private val ANSI = intArrayOf(
      0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
      0xFF6495ED.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
      0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
      0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt(),
    )

    /**
     * VS Code's sixteen `terminal.ansi*` registered defaults, dark theme
     * (`terminalColorRegistry.ts:102-247`, `ansiColorMap`'s `dark` field). None of the default
     * themes override any of them - `grep -rn 'ansi' extensions/theme-defaults/themes/` is empty
     * across all ten JSON files, dark and light both - so the registered default *is* the
     * resolved value, with no `resolve_vscode_theme.py` include-chain to walk.
     */
    private val ANSI_DARK = intArrayOf(
      0xFF000000.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
      0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
      0xFF666666.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
      0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFE5E5E5.toInt(),
    )

    /** VS Code's sixteen `terminal.ansi*` registered defaults, light theme. Same citation. */
    private val ANSI_LIGHT = intArrayOf(
      0xFF000000.toInt(), 0xFFCD3131.toInt(), 0xFF107C10.toInt(), 0xFF949800.toInt(),
      0xFF0451A5.toInt(), 0xFFBC05BC.toInt(), 0xFF0598BC.toInt(), 0xFF555555.toInt(),
      0xFF666666.toInt(), 0xFFCD3131.toInt(), 0xFF14CE14.toInt(), 0xFFB5BA00.toInt(),
      0xFF0451A5.toInt(), 0xFFBC05BC.toInt(), 0xFF0598BC.toInt(), 0xFFA5A5A5.toInt(),
    )

    private const val CUBE_FIRST = 16
    private const val GREY_FIRST = 232
    private const val GREY_COUNT = 24
    private const val GREY_START = 8
    private const val GREY_STEP = 10
    private const val OPAQUE = 0xFF shl 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /**
     * The 259 defaults for one sixteen-entry ANSI ramp: the cube and greys are shared arithmetic,
     * identical for every palette, and only [ansi] and the three dynamic slots vary.
     *
     * **The dynamic slots (256-258) are [foreground]/[background]/[cursor], not [ansi]'s to
     * answer.** [REFERENCE] passes its own placeholders; [VS_CODE_DARK]/[VS_CODE_LIGHT] pass P4i-2a's
     * resolved values - see their own KDoc for the citation.
     */
    private fun defaultsWithAnsi(
      ansi: IntArray,
      foreground: Int = 0xFFFFFFFF.toInt(),
      background: Int = OPAQUE,
      cursor: Int = 0xFFFFFFFF.toInt(),
    ): IntArray {
      val colors = IntArray(CellStyle.INDEXED_COLOR_COUNT)
      ansi.copyInto(colors)
      for (level in 0 until CUBE_LEVELS.size * CUBE_LEVELS.size * CUBE_LEVELS.size) {
        colors[CUBE_FIRST + level] = cubeEntry(level)
      }
      for (step in 0 until GREY_COUNT) colors[GREY_FIRST + step] = grey(GREY_START + GREY_STEP * step)
      colors[CellStyle.COLOR_INDEX_FOREGROUND] = foreground
      colors[CellStyle.COLOR_INDEX_BACKGROUND] = background
      colors[CellStyle.COLOR_INDEX_CURSOR] = cursor
      return colors
    }

    /** Red is the outermost channel, so entry 22 is `(0, 1, 0)` and entry 21 is `(0, 0, 5)`. */
    private fun cubeEntry(level: Int): Int {
      val sides = CUBE_LEVELS.size
      val red = CUBE_LEVELS[level / (sides * sides)]
      val green = CUBE_LEVELS[(level / sides) % sides]
      val blue = CUBE_LEVELS[level % sides]
      return OPAQUE or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    private fun grey(value: Int): Int = OPAQUE or (value shl RED_SHIFT) or (value shl GREEN_SHIFT) or value

    /**
     * Termux's own scheme - *"the xterm 256-colour chart, but with blue brighter"*. The
     * differential compares against this, so it must stay byte-for-byte the reference's.
     *
     * **Declared after everything it reads.** A companion's properties initialise in source order,
     * so a `REFERENCE` written at the top of the block runs `defaultsWithAnsi()` while [ANSI] and
     * [CUBE_LEVELS] are still null - which is an `ExceptionInInitializerError` on first touch, not
     * a compile error.
     */
    val REFERENCE: TerminalPalette = TerminalPalette(defaultsWithAnsi(ANSI))

    /**
     * VS Code's ANSI ramp, dark theme, with its own foreground/background/cursor.
     * Picking this over [VS_CODE_LIGHT] for the active theme is the composition root's decision,
     * not `terminal/`'s - see the class KDoc for why `terminal/` cannot make that choice itself.
     *
     * **The three dynamic slots, resolved rather than left [REFERENCE]'s placeholders.**
     * `terminal.foreground` is registered non-null (`terminalColorRegistry.ts:19-24`,
     * `vscode@3f3380c6`) and neither `2026-dark.json` nor its `dark_modern.json`
     * include overrides it, so it stays that file's own `#CCCCCC` (`dark_modern.json:114`).
     * `terminal.background` and `terminalCursor.foreground` register `null` and are resolved at
     * runtime by `TerminalInstanceColorProvider.getBackgroundColor` /
     * `xtermTerminal.ts:1095-1096` - a cursor falls back to the terminal's own foreground, and a
     * background falls back to `editor.background` for a terminal in the reference's own "Editor
     * location", the fitting branch since this app's terminal is its own screen rather than an
     * embedded panel. `2026-dark.json` states both outright rather than needing that fallback:
     * `terminal.background: #191A1B` (`:241`), `terminalCursor.foreground: #bfbfbf` (`:244`).
     */
    val VS_CODE_DARK: TerminalPalette = TerminalPalette(
      defaultsWithAnsi(
        ANSI_DARK,
        foreground = 0xFFCCCCCC.toInt(),
        background = 0xFF191A1B.toInt(),
        cursor = 0xFFBFBFBF.toInt(),
      ),
    )

    /**
     * VS Code's ANSI ramp, light theme. Same mechanism as [VS_CODE_DARK], and the same three ids,
     * resolved differently: `terminal.foreground` is `light_modern.json`'s own `#3B3B3B` (`:133`,
     * unchanged through `2026-light.json`'s include); `terminalCursor.foreground` is
     * `2026-light.json`'s own `#202020` (`:249`); `terminal.background` is set **nowhere** in the
     * light include chain - the one asymmetry with dark - so it takes the fallback,
     * `editor.background`, which is [com.jupydroid.app.theme.LightEditorPalette.editorBackground].
     */
    val VS_CODE_LIGHT: TerminalPalette = TerminalPalette(
      defaultsWithAnsi(
        ANSI_LIGHT,
        foreground = 0xFF3B3B3B.toInt(),
        background = 0xFFFFFFFF.toInt(),
        cursor = 0xFF202020.toInt(),
      ),
    )
  }
}
