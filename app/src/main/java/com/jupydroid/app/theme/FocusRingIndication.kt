package com.jupydroid.app.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The ring a control that takes keyboard focus draws on its edge, over whatever [base] draws for
 * a press or a hover.
 *
 * **Why an [IndicationNodeFactory] and not a modifier at each site.** Every `clickable` and
 * `selectable` that takes the default indication reads `LocalIndication`, so providing this one
 * there reaches them all and leaves the next control a ring by default. Before it, the mark was
 * Material's state layer: a rounded plate behind the control at 1.14:1 against what was there,
 * below the 3:1 WCAG 1.4.11 asks of a focus indicator.
 *
 * **It does not reach a control that names its own ripple.** material3 1.4.0's `IconButton` calls
 * `ripple` itself and never reads `LocalIndication`, and a Tab on the tablet draws only the plate
 * on it, on `TextButton` and on the rail's items; the explorer's file row, a plain clickable, draws
 * this ring at 3.17:1 over the side bar.
 *
 * **A ring, because every reference draws one**: VS Code an outline of `focusBorder` at 1px
 * (`vscode@3f3380c6 src/vs/workbench/browser/media/style.css:257-272`), JupyterLab one at 2px on
 * `:focus-visible` (`jupyterlab@7f0e6906 packages/theme-light-extension/style/variables.css:459-460`).
 *
 * **Inside the control's bounds**, as VS Code's `outline-offset: -1px` is, so a control that fills
 * its row never has its ring clipped by the row's neighbour.
 *
 * [FocusInteraction] is what `clickable` emits when the platform gives the control focus, which in
 * touch mode it does not - so a finger tap draws no ring.
 *
 * ```
 * CompositionLocalProvider(LocalIndication provides FocusRingIndication(chrome.focusBorder, ripple()))
 * ```
 */
internal data class FocusRingIndication(val ring: Color, val base: IndicationNodeFactory) : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode =
    FocusRingNode(interactionSource, ring, base)
}

/**
 * The ring's width: JupyterLab's `--jp-focus-outline-width` (`jupyterlab@7f0e6906
 * packages/theme-light-extension/style/variables.css:460`). VS Code draws 1px; a touch tablet at
 * one pane is read at both before this is settled.
 */
private val RING_WIDTH = 2.dp

private class FocusRingNode(
  private val interactionSource: InteractionSource,
  private val ring: Color,
  private val base: IndicationNodeFactory,
) : DelegatingNode(),
  DrawModifierNode {
  private var focused = false

  override fun onAttach() {
    delegate(base.create(interactionSource))
    coroutineScope.launch { interactionSource.interactions.collect(::follow) }
  }

  private fun follow(interaction: Interaction) {
    when (interaction) {
      is FocusInteraction.Focus -> focused = true
      is FocusInteraction.Unfocus -> focused = false
      else -> return
    }
    invalidateDraw()
  }

  override fun ContentDrawScope.draw() {
    drawContent()
    if (!focused) return
    val stroke = RING_WIDTH.toPx()
    drawRect(
      color = ring,
      topLeft = Offset(stroke / 2, stroke / 2),
      size = Size(size.width - stroke, size.height - stroke),
      style = Stroke(stroke),
    )
  }
}
