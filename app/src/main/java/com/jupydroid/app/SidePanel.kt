package com.jupydroid.app

/**
 * Which panel the shell's side area is showing, in the reference's own rank order.
 *
 * JupyterLab has **one** side area with an activity bar down its edge, and every panel is added to
 * it with a rank: File Browser 100 (`packages/filebrowser-extension/src/index.ts:676`), Sessions 200
 * (`packages/running-extension/src/index.ts:121`), Command Palette 300
 * (`packages/apputils-extension/src/palette.ts:115`), **Table of Contents 400**
 * (`packages/toc-extension/src/index.ts:309`) and Extension Manager 1000
 * (`packages/extensionmanager-extension/src/index.ts:78`). Two of those five exist in this app, and
 * they keep the order they have there so the rail does not have to invent one.
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
