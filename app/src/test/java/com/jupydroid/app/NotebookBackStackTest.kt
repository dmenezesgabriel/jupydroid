package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reveal-don't-reopen rule, on its own, where the cases are cheap to state.
 *
 * The device half is `NotebookNavigationTest.tappingTheSameNotebookTwiceOpensOneEditor`; this is
 * the half that says what "already open" means when it is not the notebook on top.
 */
class NotebookBackStackTest {
  @Test
  fun openingANotebookPushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Home)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Home, Editor("nb-1")), backStack)
  }

  @Test
  fun openingTheNotebookAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Home, Editor("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Home, Editor("nb-1")), backStack)
  }

  @Test
  fun openingADifferentNotebookPushesIt() {
    val backStack = mutableListOf<NavKey>(Home, Editor("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Home, Editor("nb-1"), Editor("nb-2")), backStack)
  }
}
