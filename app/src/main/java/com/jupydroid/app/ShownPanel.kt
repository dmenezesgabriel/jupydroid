package com.jupydroid.app

import java.io.Serializable

/**
 * What the shell's side area shows: one of the shell's own [SidePanel]s, or a panel an extension
 * contributed (ADR-0104), named by its `SidePanelContribution.id`.
 *
 * `Serializable` for the reason [SidePanel] is an enum: `rememberSaveable` keeps it across a
 * rotation with no `Saver`, and a contributed panel is restored by its id.
 *
 * ```
 * var shown by rememberSaveable { mutableStateOf<ShownPanel?>(ShownPanel.Shell(SidePanel.FILES)) }
 * ```
 */
sealed interface ShownPanel : Serializable {
  data class Shell(val panel: SidePanel) : ShownPanel

  data class Contributed(val id: String) : ShownPanel
}

/**
 * [SidePanel.showingAtOnePane] for whatever is shown: a contributed panel stays, since it is a row of
 * the modal rail the bar's More opens (ADR-0109), and the shell's own panels narrow by their rule.
 *
 * ```
 * ShownPanel.Contributed("git.sourceControl").showingAtOnePane() // stays Source Control
 * ```
 */
internal fun ShownPanel?.showingAtOnePane(): ShownPanel =
  this as? ShownPanel.Contributed ?: ShownPanel.Shell((this as? ShownPanel.Shell)?.panel.showingAtOnePane())

/**
 * The rail's items top to bottom: the shell's own panels in their order, with every contributed
 * panel after Search - VS Code's place for Source Control (`order: 2`, after Search's 1,
 * vscode@3f3380c `src/vs/workbench/contrib/scm/browser/scm.contribution.ts`), in the order the
 * extensions gave them (ADR-0105).
 *
 * ```
 * railOrder(listOf("git.sourceControl")) // Files, Sessions, Search, Source Control, Outline, Debugger
 * ```
 */
internal fun railOrder(contributedIds: List<String>): List<ShownPanel> {
  val shell = SidePanel.entries.map { ShownPanel.Shell(it) }
  val afterSearch = SidePanel.SEARCH.ordinal + 1
  return shell.take(afterSearch) + contributedIds.map { ShownPanel.Contributed(it) } + shell.drop(afterSearch)
}
