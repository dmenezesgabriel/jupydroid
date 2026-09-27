package com.jupydroid.app.ui.shell

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The line a workbench part draws on the side facing the next part.
 *
 * **A part's own edge, not a separator between two parts**, because that is how the reference
 * builds it: the side bar sets `borderRightWidth = '1px'` on its own container
 * (`vscode/…/parts/sidebar/sidebarPart.ts:175-180`) and the activity bar draws a `border-right` on
 * its own (`parts/activitybar/media/activitybarpart.css:30-34`). JupyterLab budgets the same line
 * into the tab strip's width rather than beside it -
 * `calc(var(--jp-private-sidebar-tab-width) + var(--jp-border-width))`
 * (`packages/application/style/sidepanel.css:41-44`). So the border costs the layout nothing and
 * the part keeps the width it had, which is what a CSS `border-right` under `box-sizing:
 * border-box` does too - the property `CellEditorBoxTest` already pins for the cell's own border.
 *
 * **`drawWithContent` rather than `drawBehind`, and that is not a preference.** Every part this is
 * used on paints its own background - a `NavigationRail`'s `containerColor`, a side panel's
 * `Scaffold` - so a line drawn *behind* the content would be painted over and never seen.
 *
 * **Trailing, resolved against the layout direction.** The reference branches the same way rather
 * than hardcoding a side (`sidebarPart.ts:176`, `isPositionLeft` choosing `borderRight` or
 * `borderLeft`); here the shell's `Row` mirrors in a right-to-left locale and the border has to
 * mirror with the part it belongs to.
 *
 * Which colour is the caller's to say, because the reference states two ids for the two seams -
 * `ChromePalette.activityBarBorder` and `ChromePalette.sideBarBorder`.
 *
 * ```
 * SidePanelRail(modifier = Modifier.trailingBorder(chromePalette.activityBarBorder), …)
 * ```
 */
internal fun Modifier.trailingBorder(colour: Color): Modifier = drawWithContent {
  drawContent()
  val thickness = STROKE_THICKNESS.toPx()
  val left = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width - thickness
  drawRect(colour, topLeft = Offset(left, 0f), size = Size(thickness, size.height))
}

/** [trailingBorder] for a part's bottom edge - `editorGroupHeader.tabsBorder`, the strip's own. */
internal fun Modifier.bottomBorder(colour: Color): Modifier = drawWithContent {
  drawContent()
  val thickness = STROKE_THICKNESS.toPx()
  drawRect(colour, topLeft = Offset(0f, size.height - thickness), size = Size(size.width, thickness))
}

/**
 * [trailingBorder] for the line across the top of the current tab - `tab.activeBorderTop`,
 * which JupyterLab draws as the same inset brand line (`tabs.css`'s `jp-mod-current::before`).
 */
internal fun Modifier.topBorder(colour: Color, thickness: Dp = STROKE_THICKNESS): Modifier = drawWithContent {
  drawContent()
  val height = thickness.toPx()
  drawRect(colour, topLeft = Offset.Zero, size = Size(size.width, height))
}

/**
 * `strokeThickness`, the reference's registered base thickness for borders and outlines - 1px
 * (`vscode/src/vs/platform/theme/common/sizes/baseSizes.ts:115-118`), which is also the literal
 * `'1px'` both parts above write.
 *
 * **Not the same number as `CellEditorBox`'s `BORDER_WIDTH`**, which is JupyterLab's
 * `--jp-border-width` and happens to measure the same. Two references, two citations: this file
 * draws chrome and that one draws a document (`docs/DECISIONS.md`, the P2t split).
 */
private val STROKE_THICKNESS = 1.dp
