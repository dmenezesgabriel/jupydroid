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
  /**
   * Behind `` `code` `` in **rendered** prose, inline or fenced - `textPreformat.background`.
   *
   * **It was called `cellSelectionBackground` and described as "the selected cell's surface", and
   * nothing had drawn a selected cell with it for a long time** (P2u-3d). It covers a fenced block
   * as well as an inline span, which is why it is not called `inlineCode…`: the reference's rule is
   * on `code`, and `pre code` resets only its padding.
   *
   * **The citation is the webview's base stylesheet**, `contrib/webview/browser/pre/index.html:145-155`,
   * which is what actually themes a rendered markdown cell. `webviewThemeMapping.ts` exports the
   * same ids under `theme-code-*` names, but nothing in the tree consumes those - they are a
   * compatibility surface for third-party renderers, and P2u-3d cited them by mistake.
   */
  val preformatBackground: Color,
  /** `` `code` `` in rendered prose - `textPreformat.foreground`, the other half of the pair. */
  val preformatForeground: Color,
  /**
   * Behind a **rendered** block quote - `textBlockQuote.background`.
   *
   * New in P2u-3f, and the reason the rule beside it can be as faint as it is: the reference marks
   * a quote with a wash *and* a 5px rule (`blockquote { background; border-color }` in the webview
   * base, `border-left-width: 5px` in the notebook's own renderer). This app drew the rule only, so
   * it needed a rule strong enough to carry the whole job.
   */
  val blockQuoteBackground: Color,
  /**
   * The rule down the left of a rendered block quote - `textBlockQuote.border`.
   *
   * **Faint on purpose, and measured**: 1.08:1 against [blockQuoteBackground] dark. It replaced
   * `colorScheme.outline` at 3.76:1, which was this app carrying a two-part signal on one part.
   */
  val blockQuoteBorder: Color,
  val cellMultiSelected: Color,
  /**
   * The line around a cell's editor, drawn whether or not the cell is active - the reference's
   * `.jp-InputArea-editor { border: var(--jp-border-width) solid var(--jp-cell-editor-border-color) }`
   * (`packages/cells/style/inputarea.css:18-26`).
   *
   * IntelliJ's `Component.borderColor` - Gray5 dark, Gray9 light - the value it gives every input
   * component (`expUI_{dark,light}.theme.json`). Held to a boundary rule rather than to the 3:1
   * non-text floor, and `EditorPaletteTest` carries the measurement that decided which.
   */
  val cellEditorBorder: Color,
  /**
   * The left stripe of the cell holding the caret, **and the border of the editor being typed
   * into**. `JUPYTER.CELL_STRIPE_SELECTED_COLOR`.
   *
   * One field for both because the reference has one variable for both: the active collapser and
   * the active editor border are each `var(--jp-brand-color1)`
   * (`packages/notebook/style/base.css:108-110`, `:160-164`). Two fields here could drift apart in
   * a way the reference cannot.
   */
  val stripeSelected: Color,
  /** The stripe of a cell whose last run raised. Has no IntelliJ key: the plugin tints the run
   *  gutter icon instead, which needs a pointer to be seen at all. */
  val stripeError: Color,
  /** The gutter's line numbers. `LINE_NUMBERS_COLOR`. */
  val lineNumber: Color,
  /**
   * A breakpoint debugpy has armed - `debugIcon.breakpointForeground`
   * (`workbench/contrib/debug/browser/breakpointEditorContribution.ts:874`). Unset in both 2026
   * themes, so this is the registered default, one value for both.
   */
  val breakpointVerified: Color,
  /**
   * A breakpoint set in the gutter but not yet confirmed by a kernel - `debugIcon.
   * breakpointUnverifiedForeground` (`breakpointEditorContribution.ts:876`). Every breakpoint
   * P6a-6 can create is this colour, since nothing calls `setBreakpoints` until P6a-7a.
   */
  val breakpointUnverified: Color,
  /**
   * Behind the line the kernel is paused on - `editor.stackFrameHighlightBackground`
   * (`callStackEditorContribution.ts:24`), the **top** stack frame's own id, not
   * `editor.focusedStackFrameHighlightBackground` - this port never has more than one frame to
   * highlight until P6a-8's call stack lets a user focus another. Unset in both 2026 themes, so
   * this is the registered default, one value for both like [breakpointVerified].
   *
   * **Full line, edge to edge, the way JupyterLab's own `Decoration.line({ class:
   * 'jp-DebuggerEditor-highlight' })` (`packages/debugger/src/handlers/editor.ts:154`) and VS
   * Code's `isWholeLine` decoration both draw it** - not a gutter marker: P6a-6's breakpoint dot
   * reuses the gutter because a *set* breakpoint is a fact about one column, where a *stop* is a
   * fact about the whole line the kernel is standing on, which is what both references draw it
   * as. Translucent so the syntax colours under it stay legible, the same reasoning
   * [searchMatch]'s own doc carries.
   */
  val debugStoppedLine: Color,
  /** The "ran in 1.2 s" label. `JUPYTER.EXECUTION_TIME_COLOR`. */
  val executionTime: Color,
  /** Behind the bracket pair the caret is touching. `MATCHED_BRACE_ATTRIBUTES` background. */
  val matchedBracket: Color,
  /**
   * Behind a match the find bar found but is not standing on -
   * `editor.findMatchHighlightBackground`.
   *
   * **Translucent, and that is the mechanism rather than a detail.** The reference states no
   * foreground for a match at all - `editor.findMatchHighlightForeground` is registered `null` -
   * so the syntax colour underneath has to survive the wash. Its own registration says as much:
   * *"must not be opaque so as not to hide underlying decorations"*
   * (`platform/theme/common/colors/editorColors.ts:156-158`).
   */
  val searchMatch: Color,
  /**
   * Behind the one match the find bar is standing on - `editor.findMatchBackground`.
   *
   * **The reference separates this from [searchMatch] by alpha alone**, and barely: `#27678290`
   * against `#27678280` composites to `#214555` against `#20414F` on the dark cell, which is
   * **1.06:1** apart. No border id is set in either 2026 theme, so there is nothing else carrying
   * the difference. `docs/DECISIONS.md` records what is left doing that work - the find bar's own
   * counter and its arrows, which this app has and which VS Code's find widget also has.
   */
  val currentSearchMatch: Color,
  val syntax: SyntaxPalette,
)

/**
 * What each colourable category of source is painted with.
 *
 * Every field names the **VS Code scope** it was read from, resolved through the theme's
 * `include` chain, so a value can be checked against its origin instead of argued about:
 *
 * ```sh
 * python3 scripts/resolve_vscode_theme.py \
 *   reference/research_repos/vscode/extensions/theme-defaults/themes/2026-light.json --role number
 * ```
 *
 * A *scope* rather than a colour id, because a token colour is not a workbench colour. VS Code
 * matches a token by longest dotted prefix, with the last rule of equal length winning
 * (`src/vs/editor/common/languages/supports/tokenization.ts:106-151`) - so `constant.numeric`
 * beats `constant`, and the file that states it last beats the one it includes.
 */
@Immutable
data class SyntaxPalette(
  /**
   * `editor.foreground` - the colour unstyled source already has, and the one role here that no
   * token rule claims.
   *
   * Never emitted as a span, because a span painting text the colour it already had does nothing.
   * It is a field so the palette can be asserted against it - both for distinctness and, since
   * most of a cell is drawn in it, for contrast.
   */
  val plain: Color,
  /** `keyword.control`. Not `keyword`, which VS Code reserves for the redder operator family. */
  val keyword: Color,
  /** `string` */
  val string: Color,
  /** `constant.numeric` */
  val number: Color,
  /** `comment` */
  val comment: Color,
  /** `entity.name.function` */
  val function: Color,
  /** `markup.heading`, whose rule also sets `bold` - which is why [styles] bolds it. */
  val heading: Color,
  /**
   * `string.other.link`, which resolves to the same value as [string] in both themes.
   *
   * That is the reference's own doing and not a gap here: VS Code carries a link's signal in
   * `markup.underline`, a rule that sets `fontStyle` and no colour, so [styles] underlines it.
   */
  val link: Color,
  /**
   * `markup.inline.raw`.
   *
   * The lexer folds fenced and indented blocks onto the same [SyntaxKind.CODE] as an inline span,
   * where VS Code has no `markup.fenced_code` rule at all and colours a block by its embedded
   * grammar. That is a lexer granularity gap, not a palette one; this is the closest value the
   * reference offers for the kind as emitted.
   */
  val codeSpan: Color,
  /** `markup.quote` */
  val blockQuote: Color,
  /** `punctuation.definition.list.begin.markdown` - the bullet or number, not the item's text. */
  val listMarker: Color,
)

/**
 * The span each [SyntaxKind] is drawn with.
 *
 * A `SpanStyle` rather than a `Color`, because two of the twelve carry no colour at all.
 * [SyntaxKind.STRONG] and [SyntaxKind.EMPHASIS] are weight and slant and nothing else - VS Code
 * *does* give `markup.bold` and `markup.italic` a foreground, but it measures **1.21:1** dark and
 * **1.03:1** light against [SyntaxPalette.plain], so the colour carries no information and
 * emitting it would be a span that changes nothing anyone can see.
 *
 * **Every markdown kind has a rule of its own, and that is what changed at P2u-2b.** They used to
 * fold onto the general kinds the way IntelliJ folds them - code and quote onto `DEFAULT_STRING`,
 * markers onto `DEFAULT_KEYWORD` (`MarkdownHighlighterColors.java:24-41`). The re-base retired
 * that citation, and the reference it moved to states each one separately.
 *
 * One map serves both languages because a highlighter only ever emits its own kinds: Python never
 * asks for [SyntaxKind.HEADING], markdown never for [SyntaxKind.KEYWORD].
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
  SyntaxKind.CODE to SpanStyle(color = codeSpan),
  SyntaxKind.QUOTE to SpanStyle(color = blockQuote),
  SyntaxKind.MARKER to SpanStyle(color = listMarker),
  SyntaxKind.LINK to SpanStyle(color = link, textDecoration = TextDecoration.Underline),
)
