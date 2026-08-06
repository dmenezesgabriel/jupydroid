package com.jupydroid.app.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for audit A4. The markdown editor drew its source in black in *both* themes,
 * because a `BasicTextField` given `Color.Unspecified` does not fall back to
 * `LocalContentColor`. On light that reads fine, which is exactly why a one-theme check missed
 * it - so this asserts both, and that they differ.
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
      MaterialTheme(colorScheme = darkColorScheme()) { dark = markdownSourceStyle() }
      MaterialTheme(colorScheme = lightColorScheme()) { light = markdownSourceStyle() }
    }

    assertNotEquals("dark theme source must have a colour", Color.Unspecified, dark.color)
    assertNotEquals("light theme source must have a colour", Color.Unspecified, light.color)
    assertNotEquals("the same colour in both themes is the bug", dark.color, light.color)
  }
}
