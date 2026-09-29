package com.jupydroid.app.ui.editor

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.jupydroid.app.richtext.RichInlineStyles
import com.jupydroid.app.syntax.SyntaxKind
import com.jupydroid.app.theme.CodeText
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.theme.styles

/**
 * How each syntax category is drawn.
 *
 * From [com.jupydroid.app.theme.EditorPalette] rather than from `MaterialTheme.colorScheme`, and
 * that change is the point: roles gave `NUMBER` whatever `secondary` happened to be, which on the
 * dark scheme sat almost on the body text and could not be asserted anywhere, because the value
 * came from the user's wallpaper. The palette's contrast is pinned by `EditorPaletteTest`.
 */
@Composable
@ReadOnlyComposable
fun syntaxStyles(): Map<SyntaxKind, SpanStyle> = editorPalette.syntax.styles()

/**
 * How the bracket pair under the cursor is marked.
 *
 * A tinted box and a heavier weight rather than a colour swap, which is IntelliJ's answer too -
 * `MATCHED_BRACE_ATTRIBUTES` carries a background and no foreground, because a bracket already
 * sits inside coloured code and a sixth colour would have to be told apart from the five there.
 */
@Composable
@ReadOnlyComposable
fun bracketMatchStyle(): SpanStyle = SpanStyle(
  background = editorPalette.matchedBracket,
  fontWeight = FontWeight.Bold,
)

/**
 * How the find bar's matches are drawn - the ones it found, and the one it is standing on.
 *
 * **Neither states a foreground, since P2u-3e.** Both used to, which was JupyterLab's structure;
 * VS Code registers `editor.findMatchForeground` and `editor.findMatchHighlightForeground` as
 * `null` and makes the backgrounds translucent instead, so the syntax colour under a match shows
 * through rather than being replaced.
 *
 * **The bold on the current match is this app's, and it now carries the difference.** Measured,
 * the reference separates current from found by alpha alone - `#214555` against `#20414F` once
 * composited on the dark cell, **1.06:1** apart, with no border id set in either theme. On a
 * phone, at a glance, that is not a signal. The weight was here before P2u-3e for its own reason
 * and is kept for a stronger one; `docs/DECISIONS.md` records it as a departure rather than
 * letting it read as parity.
 */
@Composable
@ReadOnlyComposable
fun searchMatchStyles(): SearchMatchStyles = SearchMatchStyles(
  match = SpanStyle(background = editorPalette.searchMatch),
  current = SpanStyle(
    background = editorPalette.currentSearchMatch,
    // The one the arrows are on is also the one being read, and a weight reads at a glance where a
    // hue alone has to be compared against its neighbours.
    fontWeight = FontWeight.Bold,
  ),
)

/**
 * How each inline mark in **rendered** prose is drawn.
 *
 * Emphasis is weight and slant with no colour, which both references agree on. Code and links do
 * **not** take the source palette's values, and that is the correction P2u-3d made: a rendered
 * markdown cell is a webview in the reference, and the webview's base stylesheet is what themes it
 * (`contrib/webview/browser/pre/index.html:125-155`): `code` takes the `textPreformat` pair and
 * `a` takes `textLink.foreground`, which is `MaterialTheme`'s `primary` here.
 *
 * Rendered prose and the source behind it therefore *disagree* on purpose. A link is
 * `string.other.link` in the source, where it is a string like any other, and the accent in the
 * rendered cell, where it is something to press.
 */
@Composable
@ReadOnlyComposable
fun richInlineStyles(): RichInlineStyles = RichInlineStyles(
  emphasis = SpanStyle(fontStyle = FontStyle.Italic),
  strong = SpanStyle(fontWeight = FontWeight.Bold),
  code = SpanStyle(
    fontFamily = FontFamily.Monospace,
    color = editorPalette.preformatForeground,
    background = editorPalette.preformatBackground,
  ),
  link = SpanStyle(
    color = MaterialTheme.colorScheme.primary,
    textDecoration = TextDecoration.Underline,
  ),
)

/**
 * How code is drawn, wherever it is drawn: the editor's monospace at [CodeText]'s one size and
 * line height.
 *
 * **Every surface takes it from here, because the reference has one variable for all of them.**
 * `--jp-code-font-size` and `--jp-code-line-height` are read by the editor, the input area,
 * rendered text output and both completer selectors (`codemirror/base.css:8`,
 * `inputarea.css:37`, `rendermime/base.css:28`, `completer/base.css:75,118`); this app had four
 * different sizes across the same surfaces, one of which was no size at all. What each was, and
 * why 14sp rather than the reference's 13px, is [CodeText].
 *
 * Built on `LocalTextStyle.current` rather than a Material role: the body roles carry tracking
 * (`bodySmall` 0.4sp), and letter-spacing on a monospace column is the one thing that stops it
 * lining up.
 *
 * ```
 * Text(text = output.text, style = codeTextStyle(), color = MaterialTheme.colorScheme.onSurface)
 * ```
 *
 * @param fontSize [CodeText.size] for every surface above; the terminal's own font-size setting
 * is the one caller that overrides it, so its glyphs resize without a
 *   second copy of this function.
 */
@Composable
@ReadOnlyComposable
fun codeTextStyle(fontSize: TextUnit = CodeText.size): TextStyle = LocalTextStyle.current.copy(
  fontFamily = FontFamily.Monospace,
  fontSize = fontSize,
  lineHeight = CodeText.lineHeightFor(fontSize),
)

/**
 * The prose of *rendered* markdown and rendered `text/html`, which is what code inside them
 * inherits its size from - see [richTextCodeStyle].
 *
 * Named rather than repeated: `RichText` draws a paragraph, a list item and a code block, and
 * before this the first two said `bodyMedium` in two places while the third said something else
 * entirely. One name is what makes "inherit" structural instead of a coincidence.
 */
@Composable
@ReadOnlyComposable
fun richTextProseStyle(): TextStyle = MaterialTheme.typography.bodyMedium

/**
 * Code inside rendered markdown, which is the reference's **second** rule and not the one
 * [codeTextStyle] implements.
 *
 * `.jp-RenderedHTMLCommon pre, code` takes `var(--jp-code-font-family)` and
 * `var(--jp-code-line-height)` but **`font-size: inherit`** - the prose size
 * (`packages/rendermime/style/base.css:407-417`). It wins over `:24-29`, which sets
 * `--jp-code-font-size` for the same `.jp-RenderedHTMLCommon pre`, by being later at equal
 * specificity; `.jp-RenderedText pre` in that earlier rule is stream output, which is *not*
 * rendered markdown and does take the code size.
 *
 * So a fenced block reads at the size of the prose around it and only its *shape* is code. The two
 * sizes happen to coincide today - prose is `bodyMedium` at 14sp and [CodeText.size] is 14sp - and
 * that is exactly why this is derived from [richTextProseStyle] rather than written as 14sp: the
 * rule has to survive prose moving.
 */
@Composable
@ReadOnlyComposable
fun richTextCodeStyle(): TextStyle =
  richTextProseStyle().copy(fontFamily = FontFamily.Monospace, lineHeight = CodeText.lineHeight)

/**
 * The symbol row's keys, which are monospace and deliberately **not** code.
 *
 * Here so that `FontFamily.Monospace` has one home and a reader asking "what does code look like"
 * finds the exception beside the rule - `scripts/check_monospace_home.py` keeps it that way. The
 * keys are monospace for the shape of `->` and `Tab`, not because they are source being read;
 * JupyterLab has no symbol row to copy, it being a phone adaptation; and they are captions on
 * touch targets, where [CodeText]'s 14sp would make the surface most used one-handed harder to
 * read for nothing gained.
 */
@Composable
@ReadOnlyComposable
fun symbolKeyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)

/**
 * **Any** cell's source, which is [codeTextStyle] in the editor's own foreground colour.
 *
 * The colour is the editor's `TEXT` foreground, so text the highlighter leaves alone is the colour
 * IntelliJ leaves it - and so the palette's contrast assertions describe what is actually drawn.
 * It is explicit rather than inherited, and that is a regression rather than a preference: a
 * `BasicTextField` handed `Color.Unspecified` draws black rather than falling back to
 * `LocalContentColor` the way `Text` does, which made a markdown cell's source black in both
 * themes - fine on light, nearly invisible on dark (audit A4, `EditorThemeTest`).
 *
 * **All three fields, since P2s** - [CodeCellField], [RawCellField] and [MarkdownCellField]. A
 * markdown cell drew its source in `bodyMedium` until then, which is prose, and both references
 * say the editor is one font whatever the cell holds:
 *
 * - VS Code builds a markup cell's editor as a plain `CodeEditorWidget` over
 *   `getBaseCellEditorOptions(language)` - a code cell's own options - with no font override in
 *   the file (`view/cellParts/markupCell.ts:66,331`). Its one markdown-specific font setting,
 *   `markupFontFamily`, is returned from `computeWebviewOptions()`
 *   (`notebookOptions.ts:748-772`) and styles the **rendered** preview.
 * - JupyterLab's `.cm-editor` takes `--jp-code-font-family`
 *   (`packages/codemirror/style/base.css:5-9`) with no per-cell-type override.
 *
 * **Not to be confused with [richTextCodeStyle]**, which is the opposite direction about the
 * opposite surface: code inside *rendered* markdown inherits the **prose** size. Source is code
 * that looks like code; rendered prose is prose that happens to contain code. P2l-3b settled the
 * second and P2s the first.
 *
 * @param fontSize [CodeText.size] for every cell; `docs/PLAN.md` P6c-4's own font-size setting
 *   (`EditorSettings.codeFontSizeSp`) is the one caller that overrides it, resizing
 *   [CodeCellField], [RawCellField] and [MarkdownCellField] together the way `notebook:toggle-all-
 *   cell-line-numbers` already moves all three.
 */
@Composable
@ReadOnlyComposable
fun codeSourceStyle(fontSize: TextUnit = CodeText.size): TextStyle =
  codeTextStyle(fontSize).copy(color = editorPalette.syntax.plain)
