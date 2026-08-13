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
    assertNull(sceneFor(twoPane = false, Explorer, EDITOR_A))
  }

  @Test
  fun oneEntryIsNotTwoPanesEvenOnAWideWindow() {
    assertNull(sceneFor(twoPane = true, Explorer))
  }

  /** A notebook opened from the browser pairs it: the last two entries are Explorer and the Editor. */
  @Test
  fun theExplorerPairsWithTheNotebookItOpened() {
    val scene = sceneFor(twoPane = true, Explorer, EDITOR_A)

    assertEquals(
      listOf(Explorer.toString(), EDITOR_A.toString()),
      scene?.entries?.map { it.contentKey },
    )
  }

  /** A `non-notebook` opened from the browser pairs the same way: Explorer|FileEditor. */
  @Test
  fun theExplorerPairsWithThePlainFileItOpened() {
    val scene = sceneFor(twoPane = true, Explorer, FILE_A)

    assertEquals(
      listOf(Explorer.toString(), FILE_A.toString()),
      scene?.entries?.map { it.contentKey },
    )
  }

  /**
   * Back closes the detail rather than leaving the app: the scene one entry shorter is the browser
   * on its own, which `calculateScene` then declines, so it draws full width.
   */
  @Test
  fun backFromTwoPanesDropsTheDetail() {
    val scene = sceneFor(twoPane = true, Explorer, EDITOR_A)

    assertEquals(listOf(Explorer.toString()), scene?.previousEntries?.map { it.contentKey })
  }

  /**
   * The key identifies *which* pair is on screen, or `AnimatedContent` would treat a swap of the
   * detail as the same scene and skip the transition - and, worse, hold the outgoing entry's
   * content in a slot the incoming one then reuses.
   */
  @Test
  fun swappingTheDetailIsADifferentScene() {
    val withA = sceneFor(twoPane = true, Explorer, EDITOR_A)
    val withB = sceneFor(twoPane = true, Explorer, EDITOR_B)

    assertEquals(false, withA?.key == withB?.key)
  }

  /**
   * A surface pairs only with a document *kind it opens*, which is the rule that kept Recents from
   * being drawn beside the file browser - the defect U7 opened with.
   *
   * X2h left one surface, and it opens both kinds, so nothing in the app can reach the refusing
   * branch any more. The rule is the strategy's own contract rather than any screen's, so it is
   * driven here with a surface declared to open notebooks only. Deleting the case instead would
   * leave `PANE_SURFACE_OPENS` doing something nothing checks.
   */
  @Test
  fun aSurfaceNeverPairsWithADocumentKindItDoesNotOpen() {
    assertNull(sceneFor(twoPane = true, NOTEBOOKS_ONLY_SURFACE, FILE_A))
  }

  /** Two surfaces are not a pair: a browser is not a document, whichever order they are in. */
  @Test
  fun twoSurfacesNeverPair() {
    assertNull(sceneFor(twoPane = true, NOTEBOOKS_ONLY_SURFACE, Explorer))
  }

  /** Explorer above an editor is not a pair either: the browser is not a document. */
  @Test
  fun anExplorerPushedAboveTheEditorIsOnePane() {
    assertNull(sceneFor(twoPane = true, Explorer, EDITOR_A, Explorer))
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
      NOTEBOOKS_ONLY_SURFACE ->
        mapOf(PANE_ROLE to PANE_ROLE_SURFACE, PANE_SURFACE_OPENS to listOf(PANE_KIND_NOTEBOOK))
      Explorer -> EXPLORER_PANE_METADATA
      is Editor -> mapOf(PANE_ROLE to PANE_ROLE_DOCUMENT, PANE_DOCUMENT_KIND to PANE_KIND_NOTEBOOK)
      is FileEditor -> mapOf(PANE_ROLE to PANE_ROLE_DOCUMENT, PANE_DOCUMENT_KIND to PANE_KIND_FILE)
      else -> emptyMap()
    }

  private companion object {
    /** No screen has this shape since X2h; it exists to drive the strategy's refusing branch. */
    const val NOTEBOOKS_ONLY_SURFACE = "notebooks-only-surface"
    val EDITOR_A = Editor("alpha")
    val EDITOR_B = Editor("beta")
    val FILE_A = FileEditor("a.py")
  }
}
