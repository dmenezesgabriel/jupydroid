package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.syntax.SyntaxKind
import com.jupydroid.app.theme.CodeText
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for audit A4, re-pointed at the palette.
 *
 * The markdown editor drew its source in black in *both* themes, because a `BasicTextField` given
 * `Color.Unspecified` does not fall back to `LocalContentColor`. On light that reads fine, which
 * is exactly why a one-theme check missed it - so this asserts both, and that they differ.
 *
 * It now drives [JupyterDroidTheme] rather than a bare `MaterialTheme`, because the editor's
 * colours come from `LocalEditorPalette` and a `MaterialTheme` alone no longer changes any of
 * them. Asserting through the real theme is also the stronger test: it is the composable the app
 * actually installs.
 */
@RunWith(AndroidJUnit4::class)
class EditorThemeTest {
  @get:Rule val compose = createComposeRule()

  /**
   * P2l-3b: code inside *rendered* markdown is the reference's second rule, not its first.
   *
   * `.jp-RenderedHTMLCommon pre, code` takes the code family and the code line height but
   * **`font-size: inherit`** - the prose size (`packages/rendermime/style/base.css:407-417`,
   * which wins over `:24-29`'s `--jp-code-font-size` by being later at equal specificity).
   *
   * **Asserted against the prose role rather than against a number**, because 14sp is the prose
   * size *and* [CodeText.size] today: a test naming the number would pass whichever rule was
   * implemented, and the whole point of this step is which one it is. Comparing to
   * `bodyMedium` fails the moment prose moves and code does not follow it.
   */
  @Test
  fun renderedMarkdownCodeInheritsTheProseSizeAndTakesTheCodeLineHeight() {
    lateinit var code: TextStyle
    lateinit var prose: TextStyle
    compose.setContent {
      JupyterDroidTheme {
        code = richTextCodeStyle()
        prose = richTextProseStyle()
      }
    }

    assertEquals("the size is inherited from prose", prose.fontSize, code.fontSize)
    assertEquals("the line height is the code one", CodeText.lineHeight, code.lineHeight)
    assertEquals(FontFamily.Monospace, code.fontFamily)
  }

  /**
   * A4's regression, now covering **every** cell type rather than markdown alone.
   *
   * It used to name `markdownSourceStyle`, which P2s deleted: a markdown cell's source is drawn in
   * the same style a code and a raw cell's is, so there is one function left to guard and one
   * place the colour can go missing from.
   */
  @Test
  fun theCellSourceColourFollowsTheTheme() {
    lateinit var dark: TextStyle
    lateinit var light: TextStyle
    // Both in one composition: a test rule accepts setContent exactly once.
    compose.setContent {
      JupyterDroidTheme(darkTheme = true) { dark = codeSourceStyle() }
      JupyterDroidTheme(darkTheme = false) { light = codeSourceStyle() }
    }

    assertNotEquals("dark theme source must have a colour", Color.Unspecified, dark.color)
    assertNotEquals("light theme source must have a colour", Color.Unspecified, light.color)
    assertNotEquals("the same colour in both themes is the bug", dark.color, light.color)
  }

  /**
   * P2s, at the level of the style rather than the field: source is code, rendered prose is prose.
   *
   * [MarkdownCellFieldTest.aMarkdownSourceLaysOutLikeARawCellsSource] measures the layout the two
   * fields actually produce; this pins the rule they produce it from, and pins the pair that is
   * easy to confuse - `codeSourceStyle` takes the **code** size, `richTextCodeStyle` inherits the
   * **prose** size (P2l-3b). The two happen to coincide at 14sp today, which is exactly why the
   * line height is what this asserts on.
   */
  @Test
  fun aCellsSourceTakesTheCodeLineHeightWhereRenderedCodeTakesTheProseSize() {
    lateinit var source: TextStyle
    lateinit var renderedCode: TextStyle
    lateinit var prose: TextStyle
    compose.setContent {
      JupyterDroidTheme {
        source = codeSourceStyle()
        renderedCode = richTextCodeStyle()
        prose = richTextProseStyle()
      }
    }

    assertEquals(FontFamily.Monospace, source.fontFamily)
    assertEquals("a source line is the code line height", CodeText.lineHeight, source.lineHeight)
    assertEquals("a source line is the code size", CodeText.size, source.fontSize)
    assertEquals("rendered code is still the prose size", prose.fontSize, renderedCode.fontSize)
  }

  /**
   * The same failure shape one layer down: syntax spans that do not change with the theme would
   * mean the palette is not reaching the transformation at all, which a screenshot in one theme
   * cannot show.
   */
  @Test
  fun theSyntaxStylesFollowTheTheme() {
    lateinit var dark: Map<SyntaxKind, SpanStyle>
    lateinit var light: Map<SyntaxKind, SpanStyle>
    compose.setContent {
      JupyterDroidTheme(darkTheme = true) { dark = syntaxStyles() }
      JupyterDroidTheme(darkTheme = false) { light = syntaxStyles() }
    }

    assertEquals("both themes must style the same kinds", dark.keys, light.keys)
    assertNotEquals(
      "keywords must not be the same colour in both themes",
      dark.getValue(SyntaxKind.KEYWORD).color,
      light.getValue(SyntaxKind.KEYWORD).color,
    )
  }
}
