package com.jupydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.editor.CELL_TYPE_CARET_TAG
import com.jupydroid.app.ui.editor.CellTypeControl
import com.jupydroid.app.ui.editor.cellTypeDescription
import com.jupydroid.app.ui.editor.cellTypeLabel
import com.jupydroid.app.ui.settings.CHOICE_CHECK_TAG
import com.jupydroid.app.ui.settings.ChoiceSegments
import com.jupydroid.app.ui.spaces.ADD_VARIABLE
import com.jupydroid.app.ui.spaces.ADD_VARIABLE_GLYPH_TAG
import com.jupydroid.app.ui.spaces.DEFAULT_KERNEL_CARET_TAG
import com.jupydroid.app.ui.spaces.SpaceDefaultKernelField
import com.jupydroid.app.ui.spaces.SpaceVariablesEditor
import com.jupydroid.app.ui.spaces.defaultKernelDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What a glyph inside a Material button does as the system's font size grows: it keeps its proportion to the
 * label beside it, and the button that holds both still holds both. The drop-down arrows of the cell type
 * control and the Space's default kernel, a selected segment's check and the variables editor's plus are here.
 */
@RunWith(AndroidJUnit4::class)
class ButtonGlyphFontScaleTest {
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

  private val cellType = GlyphRow(
    "cell type caret",
    CELL_TYPE_CARET_TAG,
    cellTypeLabel(CellKind.MARKDOWN),
    hasContentDescription(cellTypeDescription(CellKind.MARKDOWN)),
  )
  private val defaultKernel = GlyphRow(
    "default kernel caret",
    DEFAULT_KERNEL_CARET_TAG,
    KERNEL_NAME,
    hasContentDescription(defaultKernelDescription(KERNEL_NAME)),
  )
  private val choice = GlyphRow(
    "choice check",
    CHOICE_CHECK_TAG,
    CHOICE_LABEL,
    hasText(CHOICE_LABEL) and isSelectable(),
  )
  private val addVariable = GlyphRow(
    "add variable plus",
    ADD_VARIABLE_GLYPH_TAG,
    ADD_VARIABLE,
    hasText(ADD_VARIABLE) and hasClickAction(),
  )

  private fun show(content: @Composable () -> Unit) = compose.setContent {
    JupydroidTheme {
      AtFontScale(fontScale) {
        scaled = LocalDensity.current
        content()
      }
    }
  }

  private fun showCellType() = show { CellTypeControl(kind = CellKind.MARKDOWN, onPick = {}) }

  private fun showDefaultKernel() = show {
    SpaceDefaultKernelField(listOf(KernelSpec("python3", KERNEL_NAME, "python")), selected = "python3", onPick = {})
  }

  private fun showChoice() = show {
    ChoiceSegments(listOf(CHOICE_LABEL, "Light", "Dark"), selected = CHOICE_LABEL, label = { it }, onSelect = {})
  }

  private fun showVariables() = show { SpaceVariablesEditor(rows = emptyList(), onRowsChange = {}) }

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
  fun theCellTypeCaretKeepsItsRatioToTheTypeNameAtEveryLargerFontScale() {
    showCellType()

    assertKeepsItsRatio(cellType)
  }

  @Test
  fun theCellTypeButtonStillHoldsItsGrownCaretAtTwiceTheFontScale() {
    showCellType()

    assertHoldsItsContent(cellType)
  }

  @Test
  fun theDefaultKernelCaretKeepsItsRatioToTheKernelNameAtEveryLargerFontScale() {
    showDefaultKernel()

    assertKeepsItsRatio(defaultKernel)
  }

  @Test
  fun theDefaultKernelButtonStillHoldsItsGrownCaretAtTwiceTheFontScale() {
    showDefaultKernel()

    assertHoldsItsContent(defaultKernel)
  }

  @Test
  fun aSelectedSegmentsCheckKeepsItsRatioToItsLabelAtEveryLargerFontScale() {
    showChoice()

    assertKeepsItsRatio(choice)
  }

  @Test
  fun aSelectedSegmentStillHoldsItsGrownCheckAtTwiceTheFontScale() {
    showChoice()

    assertHoldsItsContent(choice)
  }

  @Test
  fun theAddVariablePlusKeepsItsRatioToItsLabelAtEveryLargerFontScale() {
    showVariables()

    assertKeepsItsRatio(addVariable)
  }

  @Test
  fun theAddVariableButtonStillHoldsItsGrownPlusAtTwiceTheFontScale() {
    showVariables()

    assertHoldsItsContent(addVariable)
  }

  private companion object {
    const val KERNEL_NAME = "Python 3"
    const val CHOICE_LABEL = "System"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) with each
     * glyph grown by its label's own conversion: the three 24dp glyphs 1.185, 1.188 and 1.184 at 1.3, 1.5 and 2.0
     * against 1.200, and a segment's 18dp check 0.889, 0.875 and 0.868 against 0.900, so at most 0.032, which is
     * whole-pixel rounding of a 27dp to 38dp line. With no change they fell to 0.63 and 0.47 at 2.0.
     */
    const val TOLERANCE = 0.05f
  }
}
