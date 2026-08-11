package com.jupydroid.app

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which entries end up beside each other, and which one back returns to.
 *
 * The rule is pure - a list of entries and a boolean in, a scene or null out - so it belongs
 * here rather than on a device. What a JVM test cannot see is whether the two panes actually
 * *draw*; `TwoPaneSceneTest` answers that.
 */
class TwoPaneSceneStrategyTest {
  @Test
  fun aNarrowWindowGetsNoSceneAndSoFallsBackToOnePane() {
    assertNull(sceneFor(twoPane = false, Home, EDITOR_A))
  }

  @Test
  fun oneEntryIsNotTwoPanesEvenOnAWideWindow() {
    assertNull(sceneFor(twoPane = true, Home))
  }

  @Test
  fun aWideWindowPutsTheLastTwoEntriesSideBySide() {
    val scene = sceneFor(twoPane = true, Home, EDITOR_A)

    assertEquals(listOf(Home.toString(), EDITOR_A.toString()), scene?.entries?.map { it.contentKey })
  }

  /**
   * Back closes the detail rather than leaving the app: the scene one entry shorter is the list
   * on its own, which `calculateScene` then declines, so it draws full width.
   */
  @Test
  fun backFromTwoPanesDropsTheDetail() {
    val scene = sceneFor(twoPane = true, Home, EDITOR_A)

    assertEquals(listOf(Home.toString()), scene?.previousEntries?.map { it.contentKey })
  }

  /**
   * The key identifies *which* pair is on screen, or `AnimatedContent` would treat a swap of the
   * detail as the same scene and skip the transition - and, worse, hold the outgoing entry's
   * content in a slot the incoming one then reuses.
   */
  @Test
  fun swappingTheDetailIsADifferentScene() {
    val withA = sceneFor(twoPane = true, Home, EDITOR_A)
    val withB = sceneFor(twoPane = true, Home, EDITOR_B)

    assertEquals(false, withA?.key == withB?.key)
  }

  /** `calculateScene` is declared on `SceneStrategyScope`, which is what the `with` is for. */
  private fun sceneFor(twoPane: Boolean, vararg keys: Any): Scene<Any>? =
    with(SceneStrategyScope<Any>()) {
      with(TwoPaneSceneStrategy<Any>(twoPane = twoPane)) {
        calculateScene(keys.map { NavEntry<Any>(key = it, contentKey = it.toString()) {} })
      }
    }

  private companion object {
    val EDITOR_A = Editor("alpha")
    val EDITOR_B = Editor("beta")
  }
}
