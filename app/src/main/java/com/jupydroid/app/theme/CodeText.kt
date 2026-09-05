package com.jupydroid.app.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The size code is drawn at, and the line height under it.
 *
 * **One size, because before this there were four for one thing.** Measured across the editor
 * before it was written: the cell's own source stated no size at all - `codeSourceStyle` reads
 * `LocalTextStyle`, whose Material default is `TextStyle.Default`, so an unspecified `fontSize`
 * resolved to Compose's `DefaultFontSize = 14.sp` and the line height to the font's own - while
 * outputs and the folded-cell placeholder were `bodySmall` (12sp/16sp) and the completion popup
 * `bodyMedium` (14sp/20sp). The reference has exactly one of each, read by the editor, the input
 * area, rendered text output and both completer selectors: `--jp-code-font-size: 13px` and
 * `--jp-code-line-height: 1.3077` (`packages/theme-light-extension/style/variables.css:196-197`,
 * `codemirror/base.css:8`, `inputarea.css:37`, `rendermime/base.css:28`,
 * `completer/base.css:75,118`).
 *
 * **The ratio is transcribed; the size is adapted, and the difference is deliberate.** 13px is a
 * CSS pixel on a desktop. `sp` is neither a pixel nor fixed - it scales with the reader's own font
 * setting - so copying the number would have shrunk the one surface that was already right, onto a
 * size Material does not name. 14sp is `TypeScaleTokens.BodyMediumSize` and it is what a cell's
 * source already resolves to, so what this changes is everything else joining it. The line height
 * is then the reference's ratio over that size, rounded: 14 x 1.3077 = 18.3 -> 18sp, which
 * `CodeTextTest` re-derives rather than restates.
 *
 * **Not the symbol row.** Its keys are monospace for the shape of `->` and `Tab`, not because they
 * are source being read, and JupyterLab has no such row to copy - it is a phone adaptation. They
 * are captions on touch targets, and 14sp would make the surface most used one-handed harder to
 * read for no parity gained. Recorded in `docs/DECISIONS.md` so it is not re-derived.
 *
 * ```
 * Text(text = output, style = codeTextStyle())
 * ```
 */
object CodeText {
  /** `--jp-code-font-size`, adapted to the nearest size Material's body scale names. */
  val size: TextUnit = 14.sp

  /** `--jp-code-line-height: 1.3077` over [size], rounded to whole sp. */
  val lineHeight: TextUnit = lineHeightFor(size)

  /**
   * [lineHeight]'s own ratio, exposed for a size other than [size] - the terminal's own font-size
   * setting (`docs/PLAN.md` P4j-3c) resizes its glyphs through this rather than restating
   * `1.3077` a second time undertested.
   */
  fun lineHeightFor(size: TextUnit): TextUnit = (size.value * REFERENCE_LINE_HEIGHT_RATIO).roundToInt().sp

  /** `--jp-code-line-height: 1.3077; /* 17px for 13px base */`. */
  private const val REFERENCE_LINE_HEIGHT_RATIO = 1.3077f
}
