package com.jupydroid.extension.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One extension: what it adds to the app, declared once and collected by `:app`'s composition root.
 *
 * JupyterLab's `JupyterFrontEndPlugin` in a compile-time shape (ADR-0104). There it is code that
 * calls `app.shell.add(widget, 'left', { rank })` when activated; here it is the list of what it
 * would have added, which the host places, so an extension never reaches into the shell.
 *
 * ```
 * class GitExtension : Extension {
 *   override val sidePanels = listOf(SidePanelContribution("git", "Source Control", icon) { host -> … })
 * }
 * ```
 */
interface Extension {
  /** The panels this extension puts on the side rail, in the order it wants them. */
  val sidePanels: List<SidePanelContribution>
}

/**
 * A panel for the shell's side area, which the host draws on its rail and, when picked, in the area.
 *
 * **A `@Composable`, not a model `:app` draws**: a model would have to grow a case for each thing an
 * extension shows, and every one of those cases would be code in `:app` (ADR-0105).
 *
 * @param id unique across every extension, and stable: it is what the shell saves to restore the
 *   open panel after the activity is recreated.
 * @param label the rail item's name, for a screen reader and a flow, and the panel's title bar.
 * @param content the panel's body. The host draws the title bar and the side bar's surface around it.
 */
class SidePanelContribution(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val content: @Composable (SidePanelHost) -> Unit,
)
