package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every Space on the device, managed before any of them is entered - the app's first screen.
 *
 * Winlator makes its containers list the main screen for the same reason
 * (`winlator@fb66541 app/src/main/java/com/winlator/ContainersFragment.java`): a Space is chosen
 * and managed from outside it, never from a panel inside one.
 */
@Serializable data object SpacesHome : NavKey

/**
 * Whether a Space has been entered, which is whether its browser is on the stack - every
 * workspace entry sits above it.
 *
 * ```
 * if (backStack.isInWorkspace()) backStack.openLauncher()
 * ```
 */
fun List<NavKey>.isInWorkspace(): Boolean = any { it is Explorer }

/**
 * Enters [spaceId]'s workspace from the home. Already entered is a no-op, so an import that may
 * arrive either side of the door can call it unconditionally - and lands in the Space already
 * entered rather than switching, which the workspace never does in place.
 *
 * ```
 * backStack.enterWorkspace("space-1")   // [SpacesHome] -> [SpacesHome, Explorer("space-1")]
 * ```
 */
fun MutableList<NavKey>.enterWorkspace(spaceId: String) {
  if (!isInWorkspace()) add(Explorer(spaceId))
}

/**
 * Moves the browser to the top, keeping its Space and every entry beneath it: at one pane only the
 * top entry is drawn, and the entries under the browser are still-open tabs.
 *
 * ```
 * // [SpacesHome, Explorer("base"), Editor("nb-1")] -> [SpacesHome, Editor("nb-1"), Explorer("base")]
 * backStack.bringBrowserForward()
 * ```
 */
fun MutableList<NavKey>.bringBrowserForward() {
  val browser = firstOrNull { it is Explorer } ?: return
  remove(browser)
  add(browser)
}

/**
 * Leaves the workspace for the home, dropping every entry above it.
 *
 * ```
 * backStack.leaveWorkspace()   // [SpacesHome, Explorer, Launcher] -> [SpacesHome]
 * ```
 */
fun MutableList<NavKey>.leaveWorkspace() {
  retainAll { it == SpacesHome }
}

/**
 * Back inside the workspace: one entry popped, except from the lone launcher at two panes. That
 * launcher is the workspace's resting state there - it has no close (`launcherIsClosable`), and
 * popping it alone would only re-trigger the fill - so Back from it leaves the workspace, as Back
 * from the browser does at one pane.
 *
 * The rule `NavDisplay`'s `onBack` runs, rather than a `BackHandler` racing the display's own: one
 * composed after `NavDisplay` was measured losing that race once the launcher's fill had changed
 * the scene - the display popped the launcher and the fill put it straight back
 * (`LauncherAutoCreateTest.aLoneLauncherCannotBeClosedAndBackLeavesTheWorkspaceAtTwoPanes`).
 *
 * ```
 * NavDisplay(backStack = backStack, onBack = { backStack.popWorkspaceEntry(twoPane) }, …)
 * ```
 */
fun MutableList<NavKey>.popWorkspaceEntry(twoPane: Boolean) {
  if (twoPane && lastOrNull() == Launcher && !launcherIsClosable()) {
    leaveWorkspace()
  } else {
    removeLastOrNull()
  }
}
