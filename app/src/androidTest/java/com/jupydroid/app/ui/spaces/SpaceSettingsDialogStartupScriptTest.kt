package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.theme.DarkEditorPalette
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.math.abs

/**
 * The Space's startup script in its Settings: what is typed is saved verbatim, a stored one is shown, and
 * it is edited in a code cell's own field - numbered, and coloured as bash.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogStartupScriptTest {
  @get:Rule val compose = createComposeRule()

  private var saved: SpaceSettings? = null

  @Test
  fun aTypedScriptIsSavedVerbatim() {
    show(thesis)

    compose.onNodeWithTag(STARTUP_SCRIPT_FIELD_TAG).performScrollTo().performTextInput("echo started > ~/startup.txt")
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(startupScript = "echo started > ~/startup.txt"), saved)
  }

  @Test
  fun aStoredScriptIsShownAndClearingItSavesNone() {
    show(thesis.copy(settings = SpaceSettings(startupScript = "exit 3")))
    compose.onNodeWithTag(STARTUP_SCRIPT_FIELD_TAG).performScrollTo().assertTextContains("exit 3")

    compose.onNodeWithTag(STARTUP_SCRIPT_FIELD_TAG).performTextClearance()
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(startupScript = ""), saved)
  }

  /**
   * Read off the pixels, since the gutter draws its digits on a canvas with no semantics node
   * (`LineNumberGutterTest`'s finding) and a colour has none either. A gutter is line-number ink
   * left of the code's first ink - `export`, a keyword - so the grey must be *there*: the text area
   * this replaced has a near-grey of its own elsewhere, and drew no keyword at all.
   *
   * The field alone, in a box of its own that is what gets captured: the tagged node is the text
   * field's, whose bounds start past the gutter, so capturing it shows the code and never a digit.
   */
  @Test
  fun aScriptIsNumberedAndColouredAsBash() {
    compose.setContent {
      JupydroidTheme(darkTheme = true) {
        Box(Modifier.testTag(SCRIPT_BOX)) { StartupScriptField(TextFieldState("export X=\$HOME")) }
      }
    }

    val pixels = compose.onNodeWithTag(SCRIPT_BOX).captureToImage().toPixelMap()
    val numbers = inkColumns(pixels, DarkEditorPalette.lineNumber)
    val keywords = inkColumns(pixels, DarkEditorPalette.syntax.keyword)

    assertTrue("no keyword is coloured", keywords.isNotEmpty())
    assertTrue("no variable is coloured", inkColumns(pixels, DarkEditorPalette.syntax.variable).isNotEmpty())
    assertTrue("no line number left of the code", numbers.any { it < keywords.min() })
  }

  private fun show(space: SpaceConfig) {
    compose.setContent {
      JupydroidTheme { SpaceSettingsDialog(space, onSave = { saved = it }, onDismiss = {}) }
    }
  }

  /** The x of every pixel within a rounding of [colour]: a glyph's core is its colour, its edges blended. */
  private fun inkColumns(pixels: PixelMap, colour: Color): List<Int> =
    (0 until pixels.width).filter { x -> (0 until pixels.height).any { y -> pixels[x, y].isNear(colour) } }

  private fun Color.isNear(colour: Color): Boolean =
    abs(red - colour.red) + abs(green - colour.green) + abs(blue - colour.blue) < INK_TOLERANCE

  private companion object {
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
    const val INK_TOLERANCE = 0.03f
    const val SCRIPT_BOX = "startup-script-box"
  }
}
