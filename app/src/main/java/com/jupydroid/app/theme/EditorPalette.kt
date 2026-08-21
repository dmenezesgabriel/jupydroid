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
  /**
   * Behind a cell that is part of a marked *run* - JupyterLab's `--jp-notebook-multiselected-color`
   * (`packages/notebook/style/base.css:141-143`).
   *
   * The one colour here that is JupyterLab's rather than IntelliJ's, because multi-select is a
   * notebook idea and the `JUPYTER.*` keys that would answer it are in the closed plugin. Only
   * drawn when the run holds more than one cell, which is the reference's rule too: the active
   * cell is tinted only once `jp-mod-multiSelected` is on it, and that is added at `count > 1`
   * (`packages/notebook/src/widget.ts:2763-2765`, `:146-148`).
   */
  val cellMultiSelected: Color,
  /** The left stripe of the cell holding the caret. `JUPYTER.CELL_STRIPE_SELECTED_COLOR`. */
  val stripeSelected: Color,
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
  /**
   * Behind a match the find bar found but is not standing on, with [searchMatchText] over it.
   *
   * IntelliJ's `SEARCH_RESULT_ATTRIBUTES` background
   * (`DefaultColorSchemesManager.xml:688` light, `expUI_darkScheme.xml:734` dark).
   */
  val searchMatch: Color,
  /**
   * The text of a found match - stated rather than left to the syntax colour underneath, and that
   * is JupyterLab's structure rather than IntelliJ's.
   *
   * **Measured, and the measurement is why.** IntelliJ's key declares `FOREGROUND` empty, leaving
   * the code its own colours - but on this app's dark palette its own green puts `DEFAULT_KEYWORD`
   * at **3.16:1**, under the 4.5:1 floor `EditorPaletteTest` holds every other piece of source to,
   * and every background dark enough to fix that measures 1.08:1 against the cell and so is not
   * visibly a highlight at all. JupyterLab resolves the same squeeze the same way: `.cm-searchMatch`
   * sets `color` as well as `background-color` (`packages/codemirror/src/theme.ts:106-109`), losing
   * the syntax colour inside a match on purpose. The value is whichever of black or white clears
   * the floor on [searchMatch]; the test is what says which.
   */
  val searchMatchText: Color,
  /**
   * Behind the one match the find bar is standing on, with [currentSearchMatchText] over it.
   *
   * **JupyterLab's value rather than IntelliJ's, and Compose is why.** IntelliJ marks the current
   * match with no background at all - a rounded box outlined in `CARET_COLOR`
   * (`EditorLivePreviewPresentation.kt:27-29`) - and a `SpanStyle` can carry a background, a
   * weight and a decoration but not a border, so the outline is not expressible where these are
   * applied. So this one comes from `--jp-search-selected-match-background-color`
   * (`packages/theme-{light,dark}-extension/style/variables.css:413,416`), which answers the same
   * question by contrast instead - the second colour here that is JupyterLab's, and for the same
   * kind of reason as [cellMultiSelected].
   */
  val currentSearchMatch: Color,
  /** The text of the current match - `--jp-search-selected-match-color`, black in both themes. */
  val currentSearchMatchText: Color,
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
