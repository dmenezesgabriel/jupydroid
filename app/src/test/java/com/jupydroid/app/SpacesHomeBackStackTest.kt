package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entering and leaving the workspace from the Spaces home, on the stack alone.
 *
 * The device half is `SpacesHomeNavigationTest`. This is the half that pins what the stack holds
 * on each side of the door.
 */
class SpacesHomeBackStackTest {
  @Test
  fun enteringFromTheHomePushesTheBrowser() {
    val backStack = mutableListOf<NavKey>(SpacesHome)

    backStack.enterWorkspace()

    assertEquals(listOf(SpacesHome, Explorer), backStack)
  }

  /** An import arrives while the workspace may already be open; it must not push a second browser. */
  @Test
  fun enteringAWorkspaceAlreadyEnteredChangesNothing() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer, Editor("nb-1"))

    backStack.enterWorkspace()

    assertEquals(listOf(SpacesHome, Explorer, Editor("nb-1")), backStack)
  }

  @Test
  fun leavingDropsEverythingAboveTheHome() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer, Launcher)

    backStack.leaveWorkspace()

    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  /**
   * The lone launcher is the workspace's resting state at two panes, so Back from it leaves the
   * workspace - popping it alone would only re-trigger the fill.
   */
  @Test
  fun backFromALoneLauncherAtTwoPanesLeavesTheWorkspace() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer, Launcher)

    backStack.popWorkspaceEntry(twoPane = true)

    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  @Test
  fun backFromALauncherOverADocumentPopsOnlyTheLauncher() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer, Editor("nb-1"), Launcher)

    backStack.popWorkspaceEntry(twoPane = true)

    assertEquals(listOf(SpacesHome, Explorer, Editor("nb-1")), backStack)
  }

  /** At one pane the launcher the first run pushed is a screen over the browser, and pops to it. */
  @Test
  fun backFromALauncherAtOnePanePopsToTheBrowser() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer, Launcher)

    backStack.popWorkspaceEntry(twoPane = false)

    assertEquals(listOf(SpacesHome, Explorer), backStack)
  }

  @Test
  fun backFromTheBrowserReturnsToTheHome() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer)

    backStack.popWorkspaceEntry(twoPane = false)

    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  @Test
  fun theWorkspaceIsEnteredExactlyWhileTheBrowserIsOnTheStack() {
    assertFalse(listOf<NavKey>(SpacesHome).isInWorkspace())
    assertTrue(listOf(SpacesHome, Explorer).isInWorkspace())
  }
}
