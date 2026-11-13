package com.jupydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugVariableGroup
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.DISCLOSURE_TAG
import com.jupydroid.app.ui.editor.DebuggerVariablesSheet
import com.jupydroid.app.ui.editor.KernelIndicator
import com.jupydroid.app.ui.editor.KernelStatus
import com.jupydroid.app.ui.editor.STATUS_GLYPH_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What the editor's glyphs beside text do as the system's font size grows: each keeps its proportion to the label
 * beside it, and the row that holds both still holds both. The breadcrumb's separator, the kernel's status glyph
 * and the debugger's disclosure are here; a diagnostic's glyph is in a popup window, which needs
 * [DiagnosticGlyphFontScaleTest].
 */
@RunWith(AndroidJUnit4::class)
class EditorGlyphFontScaleTest {
  @get:Rule val compose = createComposeRule()

  private var fontScale by mutableFloatStateOf(1f)
  private var scaled = Density(1f)

  /** A glyph and the text it sits beside, found by what each is tagged or says, and the node that holds both. */
  private class GlyphRow(val name: String, val glyphTag: String, val label: String, val container: SemanticsMatcher) {
    override fun toString() = name
  }

  /** What one row measured at one font scale, in dp: the glyph, its label's line and the container. */
  private data class RowAt(val glyph: Dp, val labelLine: Dp, val container: Dp) {
    val ratio: Float get() = glyph.value / labelLine.value
  }

  private val crumbs = GlyphRow("breadcrumb", CRUMB_SEPARATOR_TAG, CRUMB_LABEL, hasTestTag(CRUMB_ROW_TAG))
  private val kernel = GlyphRow("kernel status", STATUS_GLYPH_TAG, KERNEL_NAME, hasContentDescription(KERNEL_BUTTON))
  private val variable = GlyphRow("variable disclosure", DISCLOSURE_TAG, VARIABLE_TEXT, hasText(VARIABLE_TEXT))

  private fun show(content: @Composable () -> Unit) = compose.setContent {
    JupyterDroidTheme {
      AtFontScale(fontScale) {
        scaled = LocalDensity.current
        content()
      }
    }
  }

  private fun showBreadcrumb() = show {
    Breadcrumb(
      crumbs = listOf("" to "workspace", "a" to CRUMB_LABEL),
      onGoTo = {},
      modifier = Modifier.testTag(CRUMB_ROW_TAG),
    )
  }

  private fun showKernelIndicator(nameShown: Boolean) = show {
    KernelIndicator(status = KernelStatus.IDLE, nameShown = nameShown, kernelName = KERNEL_NAME)
  }

  private fun showVariables() = show {
    val frame = DebugStackFrame(id = 6, name = "helper", line = 10, column = 1)
    val compound = DebugVariable("n", "1", "int", variablesReference = 5)
    DebuggerVariablesSheet(
      stack = DebugStack(listOf(frame), listOf(DebugVariableGroup("Locals", listOf(compound)))),
      onExpand = { emptyList() },
      onSelectFrame = {},
      onEvaluate = { null },
    )
  }

  private fun rowAt(row: GlyphRow, scale: Float): RowAt {
    fontScale = scale
    compose.waitForIdle()
    return RowAt(
      glyph = height(compose.onNode(hasTestTag(row.glyphTag), useUnmergedTree = true)),
      labelLine = height(compose.onNode(hasText(row.label), useUnmergedTree = true)),
      container = height(compose.onNode(row.container)),
    )
  }

  private fun height(node: SemanticsNodeInteraction): Dp =
    with(scaled) { node.fetchSemanticsNode().boundsInRoot.height.toDp() }

  private fun assertKeepsItsRatio(row: GlyphRow) {
    val readings = (listOf(1f) + LARGER_SCALES).associateWith { rowAt(row, it) }
    val atDefault = readings.getValue(1f).ratio
    val drifted = readings.filterValues { abs(it.ratio - atDefault) > TOLERANCE }

    assertEquals("$row: glyph over label not within $TOLERANCE of $atDefault at 1.0", emptyMap<Float, RowAt>(), drifted)
  }

  private fun assertHoldsItsContent(row: GlyphRow) {
    val reading = rowAt(row, 2f)

    assertTrue(
      "$row ${reading.container} is shorter than its glyph or label: $reading",
      reading.container >= maxOf(reading.glyph, reading.labelLine),
    )
  }

  @Test
  fun theCrumbSeparatorKeepsItsRatioToTheCrumbAtEveryLargerFontScale() {
    showBreadcrumb()

    assertKeepsItsRatio(crumbs)
  }

  @Test
  fun theBreadcrumbRowStillHoldsItsGrownSeparatorAtTwiceTheFontScale() {
    showBreadcrumb()

    assertHoldsItsContent(crumbs)
  }

  @Test
  fun theKernelStatusGlyphKeepsItsRatioToTheKernelNameAtEveryLargerFontScale() {
    showKernelIndicator(nameShown = true)

    assertKeepsItsRatio(kernel)
  }

  @Test
  fun theKernelButtonStillHoldsItsGrownGlyphAndNameAtTwiceTheFontScale() {
    showKernelIndicator(nameShown = true)

    assertHoldsItsContent(kernel)
  }

  @Test
  fun aKernelStatusGlyphWithNoNameBesideItKeepsItsSizeAtTwiceTheFontScale() {
    showKernelIndicator(nameShown = false)
    fontScale = 2f
    compose.waitForIdle()

    assertEquals(24.dp, height(compose.onNode(hasTestTag(STATUS_GLYPH_TAG), useUnmergedTree = true)))
  }

  @Test
  fun theDisclosureKeepsItsRatioToTheVariableTextAtEveryLargerFontScale() {
    showVariables()

    assertKeepsItsRatio(variable)
  }

  @Test
  fun theVariableRowStillHoldsItsGrownDisclosureAndTextAtTwiceTheFontScale() {
    showVariables()

    assertHoldsItsContent(variable)
  }

  private companion object {
    const val CRUMB_ROW_TAG = "crumb-row"
    const val CRUMB_LABEL = "reports"
    const val KERNEL_NAME = "Python 3"
    const val KERNEL_BUTTON = "Kernel Python 3: Idle"
    const val VARIABLE_TEXT = "n: int = 1"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) with each
     * glyph grown by its label's own conversion: a crumb separator 0.778, 0.781 and 0.789 at 1.3, 1.5 and 2.0
     * against 0.800, the kernel's glyph 1.185, 1.188 and 1.184 against 1.200, and a disclosure 0.840, 0.862 and
     * 0.882 against 0.889, so at most 0.049, which is whole-pixel rounding of a 25dp line of monospace text. With
     * no change the ratios fell to 0.42, 0.63 and 0.47 at 2.0.
     */
    const val TOLERANCE = 0.06f
  }
}
