package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.jupydroid.app.syntax.SyntaxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The syntax half of the palettes: every kind styled but plain, the kinds one cell shows together
 * told apart, and each colour pinned by equality to the resolved 2026 theme it was transcribed from
 * (ADR-0041). The surfaces are `EditorPaletteTest`'s.
 */
class SyntaxPaletteTest {
  private val palettes = mapOf("dark" to DarkEditorPalette, "light" to LightEditorPalette)

  @Test
  fun everySyntaxKindButPlainIsStyled() {
    palettes.forEach { (name, palette) ->
      val styled = palette.syntax.styles().keys
      val expected = SyntaxKind.entries.toSet() - SyntaxKind.PLAIN
      assertEquals("$name must style every kind but PLAIN", expected, styled)
    }
  }

  /**
   * PLAIN is absent on purpose and must stay absent: unstyled text already draws in the field's
   * own colour, so a span for it would be one that changes nothing.
   */
  @Test
  fun plainIsNeverStyled() {
    palettes.forEach { (name, palette) ->
      assertEquals("$name must not style PLAIN", null, palette.syntax.styles()[SyntaxKind.PLAIN])
    }
  }

  /**
   * A span that paints text the colour it already had is a span that does nothing, and that is
   * the *shape* of the defect this file exists for - `NUMBER` on `colorScheme.secondary` was not
   * invisible, it was indistinguishable from the text around it.
   */
  @Test
  fun noSyntaxColourIsTheBodyTextColour() {
    palettes.forEach { (name, palette) ->
      palette.syntax.styles().forEach { (kind, style) ->
        assertNotEquals("$name $kind is the same colour as unstyled text", palette.syntax.plain, style.color)
      }
    }
  }

  /**
   * The five kinds Python emits appear in one cell together, so they have to be distinguishable
   * from each other. Markdown's do not get the same assertion, and deliberately: IntelliJ maps
   * `MARKDOWN_CODE_SPAN` and `MARKDOWN_BLOCK_QUOTE` both onto `DEFAULT_STRING`
   * (`MarkdownHighlighterColors.java:24-27`), so a uniqueness rule over every kind would be a
   * rule the reference itself breaks.
   */
  @Test
  fun theKindsPythonEmitsAreMutuallyDistinct() {
    palettes.forEach { (name, palette) ->
      val colours = PYTHON_KINDS.map { palette.syntax.styles().getValue(it).color }
      assertEquals("$name has duplicate Python syntax colours", colours.size, colours.toSet().size)
    }
  }

  /**
   * A magic line puts the sigil and the name side by side under different roles - `Operator`
   * against `Keyword` in `usr/lib/python3.12/site-packages/ipython_pygments_lexers.py` - so the
   * two colours have to differ or the split the kind exists for is invisible.
   */
  @Test
  fun theMagicSigilIsToldApartFromTheMagicName() {
    palettes.forEach { (name, palette) ->
      val styles = palette.syntax.styles()
      assertNotEquals(
        "$name sigil and name are indistinguishable",
        styles.getValue(SyntaxKind.MAGIC).color,
        styles.getValue(SyntaxKind.KEYWORD).color,
      )
    }
  }

  /** A shell script shows the Python five and its variables in one field, so all six must differ. */
  @Test
  fun theKindsShellEmitsAreMutuallyDistinct() {
    palettes.forEach { (name, palette) ->
      val colours = SHELL_KINDS.map { palette.syntax.styles().getValue(it).color }
      assertEquals("$name has duplicate shell syntax colours", colours.size, colours.toSet().size)
    }
  }

  /**
   * **Every syntax colour is the 2026 theme's own value, and equality is the gate.**
   *
   * This replaced a WCAG AA floor over the same values, and the swap is a decision rather than a
   * regression: with VS Code as the presentation reference, *parity* is what the palette owes, and
   * the floor and parity disagree in exactly one place - light's `number` `#098658`, which the
   * reference itself draws at **4.42:1** on the cell surface. ADR-0041 carries what that trades away.
   *
   * Equality is the stronger assertion of the two for everything else. A ratio lets a value be
   * swapped for any other legible colour; this lets it be swapped for nothing at all, which is
   * what *a value may not be nudged "to match VS Code more closely" without re-measuring* asks
   * for. The keys are the scopes, so a failure names the command that reproduces it:
   *
   * ```sh
   * python3 scripts/resolve_vscode_theme.py \
   *   <your vscode clone>/extensions/theme-defaults/themes/2026-dark.json --role number
   * ```
   *
   * The app's *own* foregrounds are still held to AA - see the two search-match tests below. Those
   * are values this app chose, not values it transcribed, and nothing about parity excuses them.
   */
  @Test
  fun everySyntaxColourIsTheReferenceValue() {
    assertEquals("dark is not 2026 Dark", DARK_2026, DarkEditorPalette.syntax.byScope())
    assertEquals("light is not 2026 Light", LIGHT_2026, LightEditorPalette.syntax.byScope())
  }

  /** Each value against the VS Code scope it was read from, so a failure says which rule moved. */
  private fun SyntaxPalette.byScope(): Map<String, Color> = mapOf(
    "editor.foreground" to plain,
    "keyword.control" to keyword,
    "string" to string,
    "constant.numeric" to number,
    "comment" to comment,
    "entity.name.function" to function,
    "markup.heading" to heading,
    "string.other.link" to link,
    "markup.inline.raw" to codeSpan,
    "markup.quote" to blockQuote,
    "punctuation.definition.list.begin.markdown" to listMarker,
    "variable" to variable,
    "meta.preprocessor" to magic,
  )

  /**
   * Emphasis carries no colour in IntelliJ - `MARKDOWN_BOLD` and `MARKDOWN_ITALIC` are declared
   * with no base attributes at all (`MarkdownHighlighterColors.java:20-23`), so they are font
   * style and nothing else. Asserted because the obvious mistake is to give them a colour to
   * make the map look complete.
   */
  @Test
  fun emphasisIsWeightAndSlantRatherThanColour() {
    palettes.forEach { (name, palette) ->
      val styles = palette.syntax.styles()
      assertEquals("$name STRONG must not be coloured", Color.Unspecified, styles.getValue(SyntaxKind.STRONG).color)
      assertEquals("$name EMPHASIS must not be coloured", Color.Unspecified, styles.getValue(SyntaxKind.EMPHASIS).color)
      assertNotEquals("$name STRONG must be bold", null, styles.getValue(SyntaxKind.STRONG).fontWeight)
      assertNotEquals("$name EMPHASIS must be italic", null, styles.getValue(SyntaxKind.EMPHASIS).fontStyle)
    }
  }

  /**
   * **Inline code, a block quote and a list marker each have a colour of their own.**
   *
   * They used to fold onto `string` and `keyword`, and the fold was IntelliJ's rather than an
   * economy of ours - `MarkdownHighlighterColors.java:24-41` maps `MARKDOWN_CODE_SPAN` and
   * `MARKDOWN_BLOCK_QUOTE` both onto `DEFAULT_STRING` and list markers onto `DEFAULT_KEYWORD`.
   * The VS Code re-base retired that citation and the reference it moved to has a rule for each:
   * `markup.inline.raw`, `markup.quote` and `punctuation.definition.list.begin.markdown`.
   *
   * Asserted as *not the folded value* rather than as a hex, because the hexes are already pinned
   * by [everySyntaxColourIsTheReferenceValue] - what this catches is the fold coming back.
   */
  @Test
  fun inlineCodeAndQuoteAndMarkerAreNotFoldedOntoStringAndKeyword() {
    palettes.forEach { (name, palette) ->
      val styles = palette.syntax.styles()
      val folds = mapOf(
        SyntaxKind.CODE to ("string" to palette.syntax.string),
        SyntaxKind.QUOTE to ("string" to palette.syntax.string),
        SyntaxKind.MARKER to ("keyword" to palette.syntax.keyword),
      )
      val refolded = folds.filter { (kind, onto) -> styles.getValue(kind).color == onto.second }
        .map { (kind, onto) -> "$kind folded onto ${onto.first}" }
      assertEquals("$name kinds that fell back onto the general palette", emptyList<String>(), refolded)
    }
  }

  /**
   * The three are distinct from each other too, since a markdown cell shows all three at once -
   * which the `palette` fixture is built to put in one frame.
   */
  @Test
  fun theMarkdownOnlyKindsAreMutuallyDistinct() {
    palettes.forEach { (name, palette) ->
      val colours = MARKDOWN_KINDS.map { palette.syntax.styles().getValue(it).color }
      assertEquals("$name has duplicate markdown syntax colours", colours.size, colours.toSet().size)
    }
  }

  /**
   * **A link takes the string colour, and the underline is the whole of what tells them apart.**
   *
   * That is the reference's structure rather than an economy of ours. VS Code paints a link's
   * title with `string.other.link`, which resolves to the same value as `string` in both 2026
   * themes - `#a5d6ff` dark, `#0a3069` light - and puts the signal in `markup.underline`, whose
   * rule sets `fontStyle` and no colour at all.
   *
   * Asserted as a pair because either half alone is a defect: the shared colour with the
   * decoration dropped makes a link indistinguishable from a string, and giving the link a colour
   * of its own would depart from the reference to solve a problem the reference does not have.
   */
  @Test
  fun aLinkTakesTheStringColourAndIsToldApartByItsUnderline() {
    palettes.forEach { (name, palette) ->
      val link = palette.syntax.styles().getValue(SyntaxKind.LINK)
      assertEquals("$name link must take the string colour", palette.syntax.string, link.color)
      assertEquals("$name link must be underlined", TextDecoration.Underline, link.textDecoration)
    }
  }

  private companion object {
    /** `2026-dark.json`, resolved through its four-deep `include` chain. */
    val DARK_2026 = mapOf(
      "editor.foreground" to Color(0xFFBBBEBF),
      "keyword.control" to Color(0xFFC586C0),
      "string" to Color(0xFFA5D6FF),
      "constant.numeric" to Color(0xFFB5CEA8),
      "comment" to Color(0xFF8B949E),
      "entity.name.function" to Color(0xFFD2A8FF),
      "markup.heading" to Color(0xFF79C0FF),
      "string.other.link" to Color(0xFFA5D6FF),
      "markup.inline.raw" to Color(0xFF79C0FF),
      "markup.quote" to Color(0xFF7EE787),
      "punctuation.definition.list.begin.markdown" to Color(0xFFFFA657),
      "variable" to Color(0xFFFFA657),
      "meta.preprocessor" to Color(0xFF569CD6),
    )

    /** `2026-light.json`, resolved. `constant.numeric` is the value that measures 4.42:1. */
    val LIGHT_2026 = mapOf(
      "editor.foreground" to Color(0xFF202020),
      "keyword.control" to Color(0xFFAF00DB),
      "string" to Color(0xFF0A3069),
      "constant.numeric" to Color(0xFF098658),
      "comment" to Color(0xFF6E7781),
      "entity.name.function" to Color(0xFF8250DF),
      "markup.heading" to Color(0xFF0550AE),
      "string.other.link" to Color(0xFF0A3069),
      "markup.inline.raw" to Color(0xFF0550AE),
      "markup.quote" to Color(0xFF116329),
      "punctuation.definition.list.begin.markdown" to Color(0xFF953800),
      "variable" to Color(0xFF953800),
      "meta.preprocessor" to Color(0xFF0000FF),
    )

    /** The kinds only a markdown cell emits and that carry a colour of their own. */
    val MARKDOWN_KINDS = listOf(SyntaxKind.CODE, SyntaxKind.QUOTE, SyntaxKind.MARKER)

    val PYTHON_KINDS = listOf(
      SyntaxKind.KEYWORD,
      SyntaxKind.STRING,
      SyntaxKind.NUMBER,
      SyntaxKind.COMMENT,
      SyntaxKind.FUNCTION,
    )

    val SHELL_KINDS = PYTHON_KINDS + SyntaxKind.VARIABLE
  }
}
