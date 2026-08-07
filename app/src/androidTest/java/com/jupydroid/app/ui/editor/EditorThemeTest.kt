package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.syntax.SyntaxKind
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

  @Test
  fun theMarkdownSourceColourFollowsTheTheme() {
    lateinit var dark: TextStyle
    lateinit var light: TextStyle
    // Both in one composition: a test rule accepts setContent exactly once.
    compose.setContent {
      JupyterDroidTheme(darkTheme = true) { dark = markdownSourceStyle() }
      JupyterDroidTheme(darkTheme = false) { light = markdownSourceStyle() }
    }

    assertNotEquals("dark theme source must have a colour", Color.Unspecified, dark.color)
    assertNotEquals("light theme source must have a colour", Color.Unspecified, light.color)
    assertNotEquals("the same colour in both themes is the bug", dark.color, light.color)
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
