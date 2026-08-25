package com.jupydroid.app

/**
 * Which panel the shell's side area is showing - and **it takes both references to say in what
 * order**, which is P2x-2's correction to its own row in `docs/PLAN.md`.
 *
 * JupyterLab has **one** side area with an activity bar down its edge, and every panel is added to
 * it with a rank: File Browser 100 (`packages/filebrowser-extension/src/index.ts:676`), Sessions 200
 * (`packages/running-extension/src/index.ts:121`), Command Palette 300
 * (`packages/apputils-extension/src/palette.ts:115`), **Table of Contents 400**
 * (`packages/toc-extension/src/index.ts:309`) and Extension Manager 1000
 * (`packages/extensionmanager-extension/src/index.ts:78`). Those five are every
 * `shell.add(…, 'left', …)` in its tree, and **none of them is a search** - `documentsearch` is
 * scoped to `app.shell.currentWidget` (`documentsearch-extension/src/index.ts:227`), which is the
 * find bar P2i shipped, not a panel.
 *
 * So [SEARCH] comes from VS Code, whose sidebar containers carry an `order`: Explorer 0
 * (`files/browser/explorerViewlet.ts:265`), **Search 1**
 * (`search/browser/search.contribution.ts:59`), Source Control 2, Run and Debug 3, Extensions 4.
 * VS Code answers nothing about [TABLE_OF_CONTENTS] in return - its Outline is a view *inside* the
 * explorer's container rather than a container of its own
 * (`outline/browser/outline.contribution.ts:26-38`).
 *
 * The two therefore answer different halves and agree where they overlap: the file browser is
 * first in both, search is second, the table of contents last. Which is the split
 * `docs/DECISIONS.md` records under P2x-1e - P2x is where JupyterLab stops having an opinion.
 *
 * **One panel at a time**, which is the area's own behaviour rather than a simplification of it:
 * `SideBarHandler._onCurrentChanged` hides the outgoing widget and shows the incoming one
 * (`packages/application/src/shell.ts:2630-2643`).
 *
 * The Launcher is deliberately not here: `shell.add(main, 'main', …)`
 * (`packages/launcher-extension/src/index.ts:118`) puts it in the document area, so P6d and this
 * rail do not collide.
 */
enum class SidePanel {
  /** `filebrowser:show-panel` - the panel that opens with the shell, and this app's first screen. */
  FILES,

  /**
   * `workbench.view.search` - the workspace searched, where the find bar searches one document.
   *
   * Wired and empty as of P2x-2; the query box, the option toggles and the results tree are P2x-3.
   */
  SEARCH,

  /** `toc:show-panel`, whose whole body is `labShell.activateById(toc.id)` (`index.ts:161-172`). */
  TABLE_OF_CONTENTS,
}

/**
 * What picking [panel] on the rail leaves showing - `null` being the collapsed area.
 *
 * **Picking the panel already showing collapses the area**, which is the reference's own toggle
 * rather than a no-op: `collapse()` sets `this._sideBar.currentTitle = null` and, in `'side'` mode,
 * *"only deselects the active tab, leaving the activity bar visible"*
 * (`packages/application/src/shell.ts:2383-2394`) - the behaviour
 * `application:toggle-left-area` drives (`packages/application-extension/src/index.tsx:514-534`).
 *
 * A pure function rather than a holder, because `SidePanel?` round-trips through `rememberSaveable`
 * on its own: the state needs no `Saver` and the one rule that is not Compose's gets a JVM test.
 *
 * ```
 * var panel by rememberSaveable { mutableStateOf<SidePanel?>(SidePanel.FILES) }
 * NavigationRailItem(onClick = { panel = panel.chosen(SidePanel.TABLE_OF_CONTENTS) }, …)
 * ```
 */
internal fun SidePanel?.chosen(panel: SidePanel): SidePanel? = if (this == panel) null else panel
