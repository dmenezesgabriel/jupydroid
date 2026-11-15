package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What a glyph in the AI extension's Chat panel does as the system's font size grows: it keeps its proportion to
 * the label beside it, and the control that holds both still holds both. The model chip's drop-down arrow, the
 * Chat list's New chat and MCP servers buttons and a chat row's icon are here.
 */
@RunWith(AndroidJUnit4::class)
class AiGlyphFontScaleTest {
  @get:Rule val compose = createComposeRule()

  private var fontScale by mutableFloatStateOf(1f)
  private var scaled = Density(1f)

  private val panel = UntitledChatPanel(compose)

  /**
   * A glyph and the text beside it, found by tag and text, and the tagged node that holds both. [inChat] is
   * whether the row is drawn in an open chat, which a change of font scale closes.
   */
  private class GlyphRow(
    val name: String,
    val glyphTag: String,
    val label: String,
    val containerTag: String,
    val inChat: Boolean = false,
  ) {
    override fun toString() = name
  }

  /** What one row measured at one font scale, in dp: the glyph, its label's line and the container. */
  private data class RowAt(val glyph: Dp, val labelLine: Dp, val container: Dp) {
    val ratio: Float get() = glyph.value / labelLine.value
  }

  private val modelChip = GlyphRow("model chip arrow", "chat-chip-arrow", MODEL_NAME, "chat-model", inChat = true)
  private val newChat = GlyphRow("new chat plus", "chat-list-glyph-New chat", "New chat", "chat-list-button-New chat")
  private val mcpServers =
    GlyphRow("mcp servers icon", "chat-list-glyph-MCP servers", "MCP servers", "chat-list-button-MCP servers")
  private val chatRow = GlyphRow("chat row icon", "chat-row-glyph", "untitled", "chat-row")

  /** At the tablet's width, where each control is its own chip: at a phone's they fold into one. */
  private fun showChip() = show(width = TABLET_WIDTH)

  private fun showList() = show(width = Dp.Unspecified)

  private fun show(width: Dp) {
    val chat = SettableAgentChat(ChatTranscript(agentModels = MODELS))
    compose.setContent {
      JupyterDroidTheme {
        AtFontScale(fontScale) {
          scaled = LocalDensity.current
          Box(if (width.isSpecified) Modifier.requiredWidth(width) else Modifier) {
            ContributedSidePanel(panel.contribution(chat), UntitledChatPanel.HOST)
          }
        }
      }
    }
    compose.waitUntil(UntitledChatPanel.TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText("untitled")).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun rowAt(row: GlyphRow, scale: Float): RowAt {
    fontScale = scale
    compose.waitForIdle()
    if (row.inChat) panel.open()
    return RowAt(
      glyph = height(placed(hasTestTag(row.glyphTag) and hasAnySibling(hasText(row.label)))),
      labelLine = height(placed(hasText(row.label) and hasAnySibling(hasTestTag(row.glyphTag)))),
      container = height(compose.onNodeWithTag(row.containerTag).fetchSemanticsNode()),
    )
  }

  /**
   * The one node [matcher] finds that is drawn. The chips' row measures a copy of them that it never places, and a
   * test of the unmerged tree finds that copy too, as a node with no size.
   */
  private fun placed(matcher: SemanticsMatcher): SemanticsNode =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().single { it.size.width > 0 }

  private fun height(node: SemanticsNode): Dp = with(scaled) { node.boundsInRoot.height.toDp() }

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
  fun theModelChipArrowKeepsItsRatioToTheModelNameAtEveryLargerFontScale() {
    showChip()

    assertKeepsItsRatio(modelChip)
  }

  @Test
  fun theModelChipStillHoldsItsGrownArrowAtTwiceTheFontScale() {
    showChip()

    assertHoldsItsContent(modelChip)
  }

  @Test
  fun theNewChatPlusKeepsItsRatioToItsLabelAtEveryLargerFontScale() {
    showList()

    assertKeepsItsRatio(newChat)
  }

  @Test
  fun theMcpServersIconKeepsItsRatioToItsLabelAtEveryLargerFontScale() {
    showList()

    assertKeepsItsRatio(mcpServers)
  }

  @Test
  fun theChatListButtonsStillHoldTheirGrownIconsAtTwiceTheFontScale() {
    showList()

    listOf(newChat, mcpServers).forEach(::assertHoldsItsContent)
  }

  @Test
  fun aChatRowsIconKeepsItsRatioToItsNameAtEveryLargerFontScale() {
    showList()

    assertKeepsItsRatio(chatRow)
  }

  @Test
  fun aChatRowStillHoldsItsGrownIconAtTwiceTheFontScale() {
    showList()

    assertHoldsItsContent(chatRow)
  }

  private companion object {
    const val MODEL_NAME = "Big Pickle"
    val TABLET_WIDTH = 800.dp
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)
    val MODELS =
      AgentModels(current = "opencode/big-pickle", offered = listOf(AgentModel("opencode/big-pickle", MODEL_NAME)))

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) with each glyph
     * grown by its label's own conversion: the model chip's 24dp arrow 1.185, 1.188 and 1.184 at 1.3, 1.5 and 2.0
     * against 1.200, the two buttons' 18dp icon 0.889, 0.875 and 0.868 against 0.900, and a chat row's 24dp icon 0.968,
     * 1.000 and 1.000 against 1.000, so at most 0.032, which is whole-pixel rounding of a 27dp to 42dp line. With no
     * change they fell to 0.63 (arrow), 0.47 (icons) and 0.57 (row) at 2.0.
     */
    const val TOLERANCE = 0.05f
  }
}
