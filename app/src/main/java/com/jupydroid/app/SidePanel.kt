package com.jupydroid.app

/**
 * Which panel the shell's side area is showing - and **it takes both references to say in what
 * order** (ADR-0062).
 *
 * JupyterLab has **one** side area with an activity bar down its edge, and every panel is added to
 * it with a rank: File Browser 100 (`packages/filebrowser-extension/src/index.ts:676`), Sessions 200
 * (`packages/running-extension/src/index.ts:121`), Command Palette 300
 * (`packages/apputils-extension/src/palette.ts:115`), **Table of Contents 400**
 * (`packages/toc-extension/src/index.ts:309`) and Extension Manager 1000
 * (`packages/extensionmanager-extension/src/index.ts:78`). Those five are every
 * `shell.add(…, 'left', …)` in its tree, and **none of them is a search** - `documentsearch` is
 * scoped to `app.shell.currentWidget` (`documentsearch-extension/src/index.ts:227`), which is the
 * find bar, not a panel.
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
 *
 *
 * **One panel at a time**, which is the area's own behaviour rather than a simplification of it:
 * `SideBarHandler._onCurrentChanged` hides the outgoing widget and shows the incoming one
 * (`packages/application/src/shell.ts:2630-2643`).
 *
 * The Launcher is deliberately not here: `shell.add(main, 'main', …)`
 * (`packages/launcher-extension/src/index.ts:118`) puts it in the document area, so a Launcher
 * would not collide with this rail.
 */
enum class SidePanel {
  /** `filebrowser:show-panel` - the panel that opens with the shell, and this app's first screen. */
  FILES,

  /**
   * `running:show-panel` - "Sessions and Tabs", the open documents beside the kernels and shells
   * the server still holds (`packages/running-extension/src/index.ts:98-122`).
   *
   * Rank **200** puts it directly behind the file browser's 100 - the reference's own ranking,
   * chosen "somewhat arbitrarily to give priority" to it (`index.ts:119-120`), and the reason it
   * sits ahead of [SEARCH], which only VS Code ranks and at a looser granularity (sidebar order 1).
   */
  RUNNING,

  /**
   * `workbench.view.search` - the workspace searched, where the find bar searches one document.
   *
   * Its query box, option toggles and results tree live in `ui/shell/`.
   */
  SEARCH,

  /** `toc:show-panel`, whose whole body is `labShell.activateById(toc.id)` (`index.ts:161-172`). */
  TABLE_OF_CONTENTS,

  /**
   * The call stack and variables of wherever the open notebook's debugger last stopped.
   *
   * **A departure from both references' own placement, forced by this app having one side area.**
   * JupyterLab's own Debugger is not among the ranked panels above at all - it registers on the
   * shell's **right** area, `shell.add(sidebar, 'right', { type: 'Debugger' })`
   * (`packages/debugger-extension/src/index.ts:1357`), which is a second side area this app does
   * not have (`docs/architecture.md`, "The shell has one side area"). VS Code's own "Run and
   * Debug" container is left-side but ranks after Source Control, at order 3 - which this app also
   * does not model - so neither reference actually orders this panel against [FILES]/[SEARCH]/
   * [TABLE_OF_CONTENTS]. Placed last for that reason: newest, most specialised, and the one
   * ranking either reference does offer (VS Code's 3) sits nearer the end of its own list than the
   * start.
   */
  DEBUGGER,
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

/**
 * The panels a **one-pane** window offers on its bottom bar, in the rail's own rank order.
 *
 * [SidePanel.TABLE_OF_CONTENTS] is deliberately absent, and the reason is what the bar is: Files is
 * a screen you navigate *to* and the table of contents is a surface over the document you are
 * already *in*, which is what `toc:show-panel` opens from the notebook's menu at this width
 * (`TableOfContentsPanel`). A destination for it would be a destination for something that has no
 * meaning where the bar is drawn - the bar shows only when a panel *is* the window, and there is no
 * open document for an outline to belong to. [SidePanel.DEBUGGER] is out for the same reason -
 * its call stack is a stopped document's, not a destination of its own.
 *
 * Three *panels* - the bar's fourth and fifth items are the Launcher and a new terminal, which
 * this list cannot carry: documents, not [SidePanel]s (see this enum's own doc), so `SidePanelBar`
 * draws them beside these rather than inside them. Five items is Material's own ceiling for a
 * navigation bar - *"three to five destinations of equal importance"*
 * (`developer.android.com/develop/ui/compose/components/navigation-bar`) - and [RUNNING] is what
 * brought the panel count up to the floor: Files and Search alone were two, below it.
 */
internal val onePaneDestinations: List<SidePanel> = listOf(SidePanel.FILES, SidePanel.RUNNING, SidePanel.SEARCH)

/**
 * What a window that draws **one pane** is left showing, given the panel the rail was last on.
 *
 * The rule is *"can the bar leave it again"*, and it is the narrowed form of an earlier, wider
 * override: a tablet rotated from 1280dp to 800dp used to keep drawing whatever panel it had with no
 * rail to switch away from it, and the browser could not be reached again without killing the app.
 * With a bar there, only a panel the bar has no item for still has to fall back - which is the
 * table of contents, and `null`, since a collapsed area over an empty window is not a state.
 *
 * An override rather than a reset: [SidePanel] itself is untouched, so rotating back to two panes
 * restores the panel the user had chosen.
 *
 * ```
 * val showingPanel = if (twoPane) sidePanel else sidePanel.showingAtOnePane()
 * ```
 */
internal fun SidePanel?.showingAtOnePane(): SidePanel = this?.takeIf { it in onePaneDestinations } ?: SidePanel.FILES
