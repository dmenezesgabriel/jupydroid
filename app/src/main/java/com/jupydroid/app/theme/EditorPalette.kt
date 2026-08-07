package com.jupydroid.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jupydroid.app.syntax.SyntaxKind

/**
 * The colours a notebook is drawn with, named after what they are rather than after a Material
 * role.
 *
 * A named set rather than `MaterialTheme.colorScheme` lookups, because the roles could not carry
 * this: `colorScheme` has one `secondary`, and the editor needs a *number* colour, a *cell*
 * background and an *execution time* grey that are not the same decision. IntelliJ reached the
 * same shape - see `NotebookEditorAppearance.kt` in the reference tree, where every one of these
 * is its own `ColorKey` (`JUPYTER.CODE_CELL_BACKGROUND`, `JUPYTER.CELL_STRIPE_SELECTED_COLOR`,
 * `JUPYTER.EXECUTION_TIME_COLOR`, and so on).
 *
 * `@Immutable` so a cell composable holding one can be skipped during recomposition.
 *
 * ```
 * val palette = LocalEditorPalette.current
 * Text(color = palette.executionTime, text = "ran in 1.2 s")
 * ```
 */
@Immutable
data class EditorPalette(
  /** The page the cells sit on. IntelliJ's `JUPYTER.EDITOR_BACKGROUND`. */
  val editorBackground: Color,
  /** A cell's own surface, and deliberately not [editorBackground] - that is what makes a cell
   *  read as a cell. IntelliJ's `JUPYTER.CODE_CELL_BACKGROUND`. */
  val cellBackground: Color,
  /** The selected cell's surface. `JUPYTER.SELECTION_BACKGROUND`. */
  val cellSelectionBackground: Color,
  /** The left stripe of the cell holding the caret. `JUPYTER.CELL_STRIPE_SELECTED_COLOR`. */
  val stripeSelected: Color,
  /** The left stripe of any other cell. `JUPYTER.CELL_STRIPE_HOVERED_COLOR`. */
  val stripeResting: Color,
  /** The stripe of a cell whose last run raised. Has no IntelliJ key: the plugin tints the run
   *  gutter icon instead, which needs a pointer to be seen at all. */
  val stripeError: Color,
  /** The line the caret is on. `JUPYTER.CARET_ROW_COLOR`. */
  val caretRow: Color,
  /** The gutter's line numbers. `LINE_NUMBERS_COLOR`. */
  val lineNumber: Color,
  /** Cell output text. `JUPYTER.CELL_OUTPUT_COLOR`. */
  val outputForeground: Color,
  /** The "ran in 1.2 s" label. `JUPYTER.EXECUTION_TIME_COLOR`. */
  val executionTime: Color,
  /** Behind the bracket pair the caret is touching. `MATCHED_BRACE_ATTRIBUTES` background. */
  val matchedBracket: Color,
  val syntax: SyntaxPalette,
)

/**
 * What each colourable category of source is painted with.
 *
 * Every field names the IntelliJ attribute key it was read from, so a value can be traced back
 * rather than argued about.
 */
@Immutable
data class SyntaxPalette(
  /** `TEXT` foreground - the colour unstyled source already has. Never emitted as a span; it is
   *  here so the palette can be asserted against it. */
  val plain: Color,
  /** `DEFAULT_KEYWORD` */
  val keyword: Color,
  /** `DEFAULT_STRING` */
  val string: Color,
  /** `DEFAULT_NUMBER` */
  val number: Color,
  /** `DEFAULT_LINE_COMMENT` */
  val comment: Color,
  /** `DEFAULT_FUNCTION_DECLARATION` */
  val function: Color,
  /** `DEFAULT_CONSTANT`, which is what `MARKDOWN_HEADER_LEVEL_1..6` inherit from. */
  val heading: Color,
  /** `MARKDOWN_LINK_TEXT`, falling back to `HYPERLINK_ATTRIBUTES` where the scheme has no
   *  markdown override. */
  val link: Color,
)

/**
 * The span each [SyntaxKind] is drawn with.
 *
 * A `SpanStyle` rather than a `Color`, because two of the twelve carry no colour at all:
 * IntelliJ declares `MARKDOWN_BOLD` and `MARKDOWN_ITALIC` with **no base attributes**
 * (`MarkdownHighlighterColors.java:20-23`), so emphasis is weight and slant and nothing else.
 * Giving them a colour to round the map out would invent a decision the reference declined to
 * make.
 *
 * The markdown kinds fold onto the general ones exactly as IntelliJ folds them - code and quote
 * onto `DEFAULT_STRING`, list markers onto `DEFAULT_KEYWORD`, headings onto `DEFAULT_CONSTANT`
 * (same file, lines 24-41). One map serves both languages because a highlighter only ever emits
 * its own kinds: Python never asks for [SyntaxKind.HEADING], markdown never for
 * [SyntaxKind.KEYWORD].
 *
 * [SyntaxKind.PLAIN] is absent on purpose - unstyled text already draws in the field's colour,
 * so a span for it would change nothing.
 */
fun SyntaxPalette.styles(): Map<SyntaxKind, SpanStyle> = mapOf(
  SyntaxKind.KEYWORD to SpanStyle(color = keyword),
  SyntaxKind.STRING to SpanStyle(color = string),
  SyntaxKind.NUMBER to SpanStyle(color = number),
  SyntaxKind.COMMENT to SpanStyle(color = comment),
  SyntaxKind.FUNCTION to SpanStyle(color = function),
  SyntaxKind.HEADING to SpanStyle(color = heading, fontWeight = FontWeight.Bold),
  SyntaxKind.STRONG to SpanStyle(fontWeight = FontWeight.Bold),
  SyntaxKind.EMPHASIS to SpanStyle(fontStyle = FontStyle.Italic),
  SyntaxKind.CODE to SpanStyle(color = string),
  SyntaxKind.QUOTE to SpanStyle(color = string),
  SyntaxKind.MARKER to SpanStyle(color = keyword),
  SyntaxKind.LINK to SpanStyle(color = link, textDecoration = TextDecoration.Underline),
)
