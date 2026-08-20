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
 * Both carry a foreground as well as a background, which is JupyterLab's structure rather than
 * IntelliJ's and is measured rather than chosen - see
 * [com.jupydroid.app.theme.EditorPalette.searchMatchText].
 */
@Composable
@ReadOnlyComposable
fun searchMatchStyles(): SearchMatchStyles = SearchMatchStyles(
  match = SpanStyle(background = editorPalette.searchMatch, color = editorPalette.searchMatchText),
  current = SpanStyle(
    background = editorPalette.currentSearchMatch,
    color = editorPalette.currentSearchMatchText,
    // The one the arrows are on is also the one being read, and a weight reads at a glance where a
    // hue alone has to be compared against its neighbours.
    fontWeight = FontWeight.Bold,
  ),
)

/**
 * How each inline mark in rendered prose is drawn.
 *
 * Emphasis is weight and slant with no colour, the way IntelliJ declares `MARKDOWN_BOLD` and
 * `MARKDOWN_ITALIC`; inline code and links take the palette's own values so a rendered cell and
 * the source behind it agree.
 */
@Composable
@ReadOnlyComposable
fun richInlineStyles(): RichInlineStyles = RichInlineStyles(
  emphasis = SpanStyle(fontStyle = FontStyle.Italic),
  strong = SpanStyle(fontWeight = FontWeight.Bold),
  code = SpanStyle(
    fontFamily = FontFamily.Monospace,
    color = editorPalette.syntax.string,
    background = editorPalette.cellSelectionBackground,
  ),
  link = SpanStyle(
    color = editorPalette.syntax.link,
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
 */
@Composable
@ReadOnlyComposable
fun codeTextStyle(): TextStyle = LocalTextStyle.current.copy(
  fontFamily = FontFamily.Monospace,
  fontSize = CodeText.size,
  lineHeight = CodeText.lineHeight,
)

/**
 * A cell's source, which is [codeTextStyle] in the editor's own foreground colour.
 *
 * The colour is the editor's `TEXT` foreground, so text the highlighter leaves alone is the colour
 * IntelliJ leaves it - and so the palette's contrast assertions describe what is actually drawn.
 * Explicit for the reason [markdownSourceStyle]'s is: a `BasicTextField` handed
 * `Color.Unspecified` draws black rather than falling back to `LocalContentColor`.
 *
 * Shared by the two fields that draw code-shaped text - [CodeCellField] and [RawCellField]. A raw
 * cell holds LaTeX, reST or an HTML fragment, which is closer to code than to prose, and the
 * reference draws every cell type in its one editor font.
 */
@Composable
@ReadOnlyComposable
fun codeSourceStyle(): TextStyle = codeTextStyle().copy(color = editorPalette.syntax.plain)

/**
 * The markdown source as it is being written.
 *
 * The colour is explicit and that is the point: a `BasicTextField` handed `Color.Unspecified`
 * draws black rather than falling back to `LocalContentColor` the way `Text` does, so leaving
 * it off made the source black in both themes - fine on light, nearly invisible on dark.
 */
@Composable
@ReadOnlyComposable
fun markdownSourceStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(color = editorPalette.syntax.plain)
