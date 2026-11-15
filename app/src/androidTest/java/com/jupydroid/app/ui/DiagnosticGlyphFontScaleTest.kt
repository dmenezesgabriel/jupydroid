package com.jupydroid.app.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.CellDiagnostic
import com.jupydroid.app.ui.editor.DETAIL_GLYPH_TAG
import com.jupydroid.app.ui.editor.DiagnosticDetailPopup
import com.jupydroid.app.ui.editor.SEVERITY_ERROR
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What the severity glyph on a diagnostic's detail card does as the system's font size grows: it keeps its
 * proportion to the message beside it, and the card still holds both.
 *
 * The card is a popup, a window composed from the activity's own resources, so `AtFontScale` never reaches
 * it and it stayed at 1.0 (the same reason as `RenameFileDialogKeyboardTest`). This sets the activity's font
 * scale instead and shows the popup afresh at each scale, since a popup reads it when its window is made.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticGlyphFontScaleTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var shown by mutableStateOf(false)

  /** The glyph, the message's line and the card, in dp, as the popup's own window measured them. */
  private data class CardAt(val glyph: Dp, val messageLine: Dp, val card: Dp) {
    val ratio: Float get() = glyph.value / messageLine.value
  }

  private fun showPopup() = compose.setContent {
    JupyterDroidTheme {
      if (shown) {
        DiagnosticDetailPopup(
          diagnostics = listOf(CellDiagnostic(1, 0, 1, SEVERITY_ERROR, code = null, source = null, message = "bad")),
          anchor = IntRect(left = 100, top = 100, right = 120, bottom = 120),
          onDismiss = {},
        )
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun useFontScale(scale: Float) {
    val resources = compose.activity.resources
    resources.updateConfiguration(
      Configuration(resources.configuration).apply {
        fontScale = scale
      },
      resources.displayMetrics,
    )
  }

  private fun cardAt(scale: Float): CardAt {
    shown = false
    compose.waitForIdle()
    useFontScale(scale)
    shown = true
    compose.waitForIdle()
    val density = Density(compose.activity)
    fun height(node: SemanticsNodeInteraction): Dp =
      with(density) { node.fetchSemanticsNode().boundsInRoot.height.toDp() }
    return CardAt(
      glyph = height(compose.onNode(hasTestTag(DETAIL_GLYPH_TAG), useUnmergedTree = true)),
      messageLine = height(compose.onNode(hasText(MESSAGE), useUnmergedTree = true)),
      card = height(compose.onNode(hasContentDescription(CARD))),
    )
  }

  @After
  fun restoreTheDefaultFontScale() = useFontScale(1f)

  @Test
  fun theSeverityGlyphKeepsItsRatioToItsMessageAtEveryLargerFontScale() {
    showPopup()
    val readings = (listOf(1f) + LARGER_SCALES).associateWith(::cardAt)
    val atDefault = readings.getValue(1f).ratio

    val drifted = readings.filterValues { abs(it.ratio - atDefault) > TOLERANCE }

    assertEquals("glyph over message not within $TOLERANCE of $atDefault at 1.0", emptyMap<Float, CardAt>(), drifted)
  }

  @Test
  fun theCardStillHoldsItsGrownGlyphAndMessageAtTwiceTheFontScale() {
    showPopup()

    val reading = cardAt(2f)

    assertTrue(
      "card ${reading.card} is shorter than its glyph or message: $reading",
      reading.card >= maxOf(reading.glyph, reading.messageLine),
    )
  }

  private companion object {
    const val CARD = "Diagnostic detail"
    const val MESSAGE = "error: bad"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over message ratio may move from its value at 1.0. Measured on `dev36` (API 36) with the
     * 12dp glyph grown by its message's own conversion: 0.762, 0.750 and 0.750 at 1.3, 1.5 and 2.0 against 0.750,
     * so at most 0.012. With no change the ratios fell to 0.571, 0.500 and 0.375.
     */
    const val TOLERANCE = 0.05f
  }
}
