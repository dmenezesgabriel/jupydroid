package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.model.BASE_SPACE
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
  /** The entered Space rides on the browser's key, so every workspace entry above it can read it. */
  @Test
  fun enteringFromTheHomePushesTheBrowserForThatSpace() {
    val backStack = mutableListOf<NavKey>(SpacesHome)

    backStack.enterWorkspace("space-1")

    assertEquals(listOf(SpacesHome, Explorer("space-1")), backStack)
  }

  /** An import arrives while the workspace may already be open; it must not push a second browser. */
  @Test
  fun enteringAWorkspaceAlreadyEnteredChangesNothing() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"), Editor("nb-1"))

    backStack.enterWorkspace(BASE_SPACE.id)

    assertEquals(listOf(SpacesHome, Explorer("space-1"), Editor("nb-1")), backStack)
  }

  /** Every workspace entry reads its Space off the browser's key beneath it. */
  @Test
  fun theEnteredSpaceIsTheOneOnTheBrowsersKey() {
    val backStack = listOf(SpacesHome, Explorer("space-1"), Editor("nb-1"))

    assertEquals("space-1", backStack.enteredSpaceId())
  }

  /** With nothing entered, what the home reaches is base's - where an import from the home lands. */
  @Test
  fun theHomeAloneReachesBase() {
    assertEquals(BASE_SPACE.id, listOf<NavKey>(SpacesHome).enteredSpaceId())
  }

  @Test
  fun leavingDropsEverythingAboveTheHome() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer(BASE_SPACE.id), Launcher)

    backStack.leaveWorkspace()

    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  /**
   * The lone launcher is the workspace's resting state at two panes, so Back from it leaves the
   * workspace - popping it alone would only re-trigger the fill.
   */
  @Test
  fun backFromALoneLauncherAtTwoPanesLeavesTheWorkspace() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer(BASE_SPACE.id), Launcher)

    backStack.popWorkspaceEntry(twoPane = true)

    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  @Test
  fun backFromALauncherOverADocumentPopsOnlyTheLauncher() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer(BASE_SPACE.id), Editor("nb-1"), Launcher)

    backStack.popWorkspaceEntry(twoPane = true)

    assertEquals(listOf(SpacesHome, Explorer(BASE_SPACE.id), Editor("nb-1")), backStack)
  }

  /** At one pane the launcher the first run pushed is a screen over the browser, and pops to it. */
  @Test
  fun backFromALauncherAtOnePanePopsToTheBrowser() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer(BASE_SPACE.id), Launcher)

    backStack.popWorkspaceEntry(twoPane = false)

    assertEquals(listOf(SpacesHome, Explorer(BASE_SPACE.id)), backStack)
  }

  @Test
  fun backFromTheBrowserReturnsToTheHome() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer(BASE_SPACE.id))

    backStack.popWorkspaceEntry(twoPane = false)

    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  @Test
  fun theWorkspaceIsEnteredExactlyWhileTheBrowserIsOnTheStack() {
    assertFalse(listOf<NavKey>(SpacesHome).isInWorkspace())
    assertTrue(listOf(SpacesHome, Explorer("space-1")).isInWorkspace())
  }

  /** One pane draws only the top entry, so a breadcrumb brings the browser up over the open tabs. */
  @Test
  fun bringingTheBrowserForwardKeepsItsSpaceAndTheTabsBeneath() {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"), Editor("nb-1"))

    backStack.bringBrowserForward()

    assertEquals(listOf(SpacesHome, Editor("nb-1"), Explorer("space-1")), backStack)
  }
}
