package com.jupydroid.app.ui.editor

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
 * The markdown source as it is being written.
 *
 * The colour is explicit and that is the point: a `BasicTextField` handed `Color.Unspecified`
 * draws black rather than falling back to `LocalContentColor` the way `Text` does, so leaving
 * it off made the source black in both themes - fine on light, nearly invisible on dark.
 */
@Composable
@ReadOnlyComposable
fun markdownSourceStyle(): TextStyle =
  MaterialTheme.typography.bodyMedium.copy(color = editorPalette.syntax.plain)
