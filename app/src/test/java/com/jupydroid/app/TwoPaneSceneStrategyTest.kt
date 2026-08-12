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

  /**
   * The pair is a *file surface and the document it opened*, not any two entries. Tapping "Browse
   * files" leaves Recents below the explorer - Recents is the notebook home, and the browser is
   * not a document it opens - so nothing pairs and the explorer draws alone. This is the defect
   * the step opened with: it drew Recents beside the file browser on the tablet.
   */
  @Test
  fun theExplorerOnItsOwnIsOnePaneEvenOnAWideWindow() {
    assertNull(sceneFor(twoPane = true, Home, Explorer))
  }

  /** A notebook opened from the browser pairs it: the last two entries are Explorer and the Editor. */
  @Test
  fun theExplorerPairsWithTheNotebookItOpened() {
    val scene = sceneFor(twoPane = true, Home, Explorer, EDITOR_A)

    assertEquals(
      listOf(Explorer.toString(), EDITOR_A.toString()),
      scene?.entries?.map { it.contentKey },
    )
  }

  /** A `non-notebook` opened from the browser pairs the same way: Explorer|FileEditor. */
  @Test
  fun theExplorerPairsWithThePlainFileItOpened() {
    val scene = sceneFor(twoPane = true, Home, Explorer, FILE_A)

    assertEquals(
      listOf(Explorer.toString(), FILE_A.toString()),
      scene?.entries?.map { it.contentKey },
    )
  }

  /** One pop closes the detail and leaves the explorer alone - no second pane to fall into. */
  @Test
  fun backFromTheExplorerPairLeavesTheExplorerAlone() {
    val scene = sceneFor(twoPane = true, Home, Explorer, EDITOR_A)

    assertEquals(
      listOf(Home.toString(), Explorer.toString()),
      scene?.previousEntries?.map { it.contentKey },
    )
  }

  /**
   * The notebook home opens notebooks; a `non-notebook` is not one, so nothing pairs and the
   * editor fills the window - the way a phone shows it (Step 1's file-editor flow).
   */
  @Test
  fun theNotebookHomeNeverPairsWithAPlainFile() {
    assertNull(sceneFor(twoPane = true, Home, FILE_A))
  }

  /** Explorer below an editor is not an explorer-pair shape either: the browser is not a document. */
  @Test
  fun anExplorerPushedAboveTheEditorIsOnePane() {
    assertNull(sceneFor(twoPane = true, Home, EDITOR_A, Explorer))
  }

  /** `calculateScene` is declared on `SceneStrategyScope`, which is what the `with` is for. */
  private fun sceneFor(twoPane: Boolean, vararg keys: Any): Scene<Any>? =
    with(SceneStrategyScope<Any>()) {
      with(TwoPaneSceneStrategy<Any>(twoPane = twoPane)) {
        calculateScene(keys.map { NavEntry(key = it, contentKey = it.toString(), metadata = paneMetadataOf(it)) {} })
      }
    }

  /**
   * The same tagging `Navigation.kt` gives its entry providers, in miniature. The strategy reads
   * only metadata, never the key, so a test entry has to carry what the wiring would.
   */
  private fun paneMetadataOf(key: Any): Map<String, Any> =
    when (key) {
      Home -> HOME_PANE_METADATA
      Explorer -> EXPLORER_PANE_METADATA
      is Editor -> mapOf(PANE_ROLE to PANE_ROLE_DOCUMENT, PANE_DOCUMENT_KIND to PANE_KIND_NOTEBOOK)
      is FileEditor -> mapOf(PANE_ROLE to PANE_ROLE_DOCUMENT, PANE_DOCUMENT_KIND to PANE_KIND_FILE)
      else -> emptyMap()
    }

  private companion object {
    val EDITOR_A = Editor("alpha")
    val EDITOR_B = Editor("beta")
    val FILE_A = FileEditor("a.py")
  }
}
