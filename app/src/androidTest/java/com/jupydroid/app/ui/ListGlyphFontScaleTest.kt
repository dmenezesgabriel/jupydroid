package com.jupydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeExplorerSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.ExplorerSettings
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.explorer.ENTRY_ROW_ICON_TAG
import com.jupydroid.app.ui.explorer.ExplorerScreen
import com.jupydroid.app.ui.explorer.RECENTS_TWISTIE_TAG
import com.jupydroid.app.ui.explorer.RECENT_ROW_ICON_TAG
import com.jupydroid.app.ui.shell.SEARCH_TWISTIE_TAG
import com.jupydroid.app.ui.shell.SEARCH_WORKSPACE_BOX
import com.jupydroid.app.ui.shell.SearchSidePanel
import com.jupydroid.app.ui.shell.WorkspaceReplace
import com.jupydroid.app.ui.shell.fileMatchLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * What a glyph that leads a list row does as the system's font size grows: it keeps its proportion to the
 * label beside it, and the row that holds both still holds both. The Explorer's file and folder icon, a
 * recent's icon and the chevron on the Recent heading share one surface; the chevron on a search result's
 * file header is the other.
 */
@RunWith(AndroidJUnit4::class)
class ListGlyphFontScaleTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private var fontScale by mutableFloatStateOf(1f)
  private var scaled = Density(1f)

  /** A glyph and the text it leads, found by what each is tagged or says, and the node that holds both. */
  private class Surface(val name: String, val glyphTag: String, val label: String, val container: SemanticsMatcher) {
    override fun toString() = name
  }

  /** What one surface measured at one font scale, in dp: the glyph, its label's line and the container. */
  private data class SurfaceAt(val glyph: Dp, val labelLine: Dp, val container: Dp) {
    val ratio: Float get() = glyph.value / labelLine.value
  }

  private val explorerSurfaces = listOf(
    Surface("entry row", ENTRY_ROW_ICON_TAG, ENTRY_NAME, hasText(ENTRY_NAME)),
    Surface("recent row", RECENT_ROW_ICON_TAG, RECENT_PATH, hasText(RECENT_PATH)),
    Surface("recents heading", RECENTS_TWISTIE_TAG, "Recent", hasText("Recent")),
  )

  private val searchSurfaces = listOf(
    Surface(
      "search file header",
      SEARCH_TWISTIE_TAG,
      MATCHED_FILE,
      hasContentDescription(fileMatchLabel(MATCHED_FILE, 1)),
    ),
  )

  private fun showExplorer() {
    File(temp.root, ENTRY_NAME).writeText("x")
    compose.setContent {
      JupyterDroidTheme {
        AtFontScale(fontScale) {
          scaled = LocalDensity.current
          ExplorerScreen(
            workspaceRepository = DiskWorkspaceRepository(temp.root),
            workspaceFiles = DiskWorkspaceFiles(temp.root),
            notebookRepository = DiskNotebookRepository(temp.root),
            kernelSpecs = FakeKernelSpecs(),
            space = BASE_SPACE,
            recentDocuments = FakeRecentDocumentsRepository(
              listOf(RecentDocument(RECENT_PATH, WorkspaceEntryKind.FILE)),
            ),
            explorerSettings = FakeExplorerSettingsRepository(ExplorerSettings(recentsCollapsed = false)),
            onOpenFile = {},
          )
        }
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText(RECENT_PATH)).fetchSemanticsNodes().isNotEmpty() }
  }

  /** [cutAt] hides what the panel lays out below it, as the soft keyboard hides the list's tail. */
  private fun showSearchWithOneMatch(cutAt: Dp = Dp.Unspecified) {
    val replace = WorkspaceReplace(OpenDocuments(), FakeNotebookRepository(), FakeWorkspaceRepository())
    compose.setContent {
      JupyterDroidTheme {
        AtFontScale(fontScale) {
          scaled = LocalDensity.current
          CutAt(cutAt) {
            SearchSidePanel(
              searcher = FakeWorkspaceSearcher(mapOf(MATCHED_FILE to "cat")),
              replace = replace,
              onOpenMatch = { _, _ -> },
            )
          }
        }
      }
    }
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput("cat")
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodes(hasContentDescription(fileMatchLabel(MATCHED_FILE, 1))).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Composable
  private fun CutAt(height: Dp, content: @Composable () -> Unit) {
    if (height == Dp.Unspecified) return content()
    // `requiredHeight` would centre the taller panel in the cut, hiding its top instead of its tail.
    Box(Modifier.height(height).clipToBounds()) {
      Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).height(WINDOW_HEIGHT)) { content() }
    }
  }

  private fun surfaceAt(surface: Surface, scale: Float): SurfaceAt {
    fontScale = scale
    compose.waitForIdle()
    return SurfaceAt(
      glyph = height(compose.onAllNodes(hasTestTag(surface.glyphTag), useUnmergedTree = true)[0]),
      labelLine = height(compose.onNode(hasText(surface.label), useUnmergedTree = true)),
      container = height(compose.onNode(surface.container)),
    )
  }

  /**
   * The node's laid-out height, not `boundsInRoot`, which a scrolling list clips to its viewport. The soft keyboard
   * the search box raises cuts the list's tail, so a header it cut read 43dp for a 45dp chevron beside an unclipped
   * label whenever the keyboard arrived between two readings; see
   * `theSearchChevronKeepsItsRatioWhenTheListIsCutThroughItsHeader`.
   */
  private fun height(node: SemanticsNodeInteraction): Dp = with(scaled) { node.fetchSemanticsNode().size.height.toDp() }

  private fun assertEachKeepsItsRatio(surfaces: List<Surface>) {
    val drifted = surfaces.associate { surface ->
      val atDefault = surfaceAt(surface, 1f).ratio
      surface.name to LARGER_SCALES.associateWith { surfaceAt(surface, it) }.filterValues {
        abs(it.ratio - atDefault) > RATIO_TOLERANCE
      }.mapValues { (_, reading) -> "$reading against $atDefault at 1.0" }
    }.filterValues { it.isNotEmpty() }

    assertEquals("glyph over label not within $RATIO_TOLERANCE of its value at 1.0", emptyMap<String, Any>(), drifted)
  }

  private fun assertEachContainerHoldsItsContent(surfaces: List<Surface>) = surfaces.forEach { surface ->
    val reading = surfaceAt(surface, 2f)

    assertTrue(
      "$surface ${reading.container} is shorter than its glyph or label: $reading",
      reading.container >= maxOf(reading.glyph, reading.labelLine),
    )
  }

  @Test
  fun theExplorerGlyphsKeepTheirRatioToTheirLabelAtEveryLargerFontScale() {
    showExplorer()

    assertEachKeepsItsRatio(explorerSurfaces)
  }

  @Test
  fun theExplorerRowsStillHoldTheirGrownGlyphAndLabelAtTwiceTheFontScale() {
    showExplorer()

    assertEachContainerHoldsItsContent(explorerSurfaces)
  }

  @Test
  fun theSearchChevronKeepsItsRatioToItsFileNameAtEveryLargerFontScale() {
    showSearchWithOneMatch()

    assertEachKeepsItsRatio(searchSurfaces)
  }

  @Test
  fun theSearchChevronKeepsItsRatioWhenTheListIsCutThroughItsHeader() {
    showSearchWithOneMatch(cutAt = CUT_THROUGH_HEADER)

    assertEachKeepsItsRatio(searchSurfaces)
  }

  @Test
  fun theSearchFileHeaderStillHoldsItsGrownChevronAndNameAtTwiceTheFontScale() {
    showSearchWithOneMatch()

    assertEachContainerHoldsItsContent(searchSurfaces)
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    /** The 640dp window of `dev36`, which the panel fills when nothing cuts it. */
    val WINDOW_HEIGHT = 640.dp

    /**
     * Inside the header's row at every scale: it spans 280dp to 328dp at 1.0 and 306dp to 367dp at 2.0 on `dev36`.
     * Higher, the clipped glyph and the clipped label shrink together and their ratio hides the cut.
     */
    val CUT_THROUGH_HEADER = 325.dp
    const val ENTRY_NAME = "notes.txt"
    const val RECENT_PATH = "reports/summary.ipynb"
    const val MATCHED_FILE = "found.py"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) with each
     * 24dp glyph grown by its label's own conversion: a row's icon 0.968, 1.000 and 1.000 at 1.3, 1.5 and 2.0
     * against 1.000, and a chevron 1.185, 1.188 and 1.184 against 1.200, so at most 0.032, which is whole-pixel
     * rounding of a 31dp label line. With no change the ratios fell to 0.774, 0.686 and 0.571 for a row and to
     * 0.889, 0.750 and 0.632 for a chevron.
     */
    const val RATIO_TOLERANCE = 0.05f
  }
}
