package com.jupydroid.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.breaksInsideAWord
import com.jupydroid.app.ui.isCut
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LAUNCHER_ITEMS
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.launcher.LauncherItem
import com.jupydroid.app.ui.launcher.LauncherScreen
import com.jupydroid.app.ui.launcher.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the launcher screen must *show* - the cards, the cwd, the section headers - which no JVM
 * test can answer. Card taps are asserted through the ViewModel's own outcome callback rather
 * than a navigation host: what a tap becomes is `LauncherViewModelTest`'s job.
 */
@RunWith(AndroidJUnit4::class)
class LauncherScreenTest {
  @get:Rule val compose = createComposeRule()

  private fun viewModel() = LauncherViewModel(
    FakeNotebookRepository(),
    FakeWorkspaceFiles(),
    FakeTerminalGateway(),
    FakeKernelGateway(),
    FakeKernelSpecs(),
    BASE_SPACE,
  ) { 1 }

  @Test
  fun showsTheCwdAndEverySectionInRankOrder() {
    compose.setContent {
      LauncherScreen(cwd = "reports", viewModel = viewModel(), onLaunched = {})
    }

    compose.onNodeWithText("reports").assertIsDisplayed()
    compose.onNodeWithText("Notebook").assertIsDisplayed()
    compose.onNodeWithText("Console").assertIsDisplayed()
    compose.onNodeWithText("Other").assertIsDisplayed()
  }

  @Test
  fun leavesTheRootCwdHeadingEmpty() {
    compose.setContent {
      LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {})
    }

    // The reference renders the h3 with `cwd` verbatim - empty at the root. A bare "/" was our
    // own fallback, and it read as a stray breadcrumb.
    assertEquals(0, compose.onAllNodesWithText("/").fetchSemanticsNodes().size)
  }

  @Test
  fun anEmptyCwdHeadingLeavesOneMarginRatherThanALineBetweenTwo() {
    // The tab strip above the launcher consumes the status bar's inset (`MainAreaTabStrip`), so the
    // screen is measured with it consumed, not with whatever status bar the device draws.
    compose.setContent {
      Box(Modifier.consumeWindowInsets(WindowInsets.statusBars)) {
        LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {})
      }
    }

    // The reference's empty `h3` has no line box, so its two 1em margins collapse into one: the first
    // section starts one top padding plus one margin down, not 16 + 16 + a 20dp line + 16.
    val firstSectionTop = compose.onNodeWithText("Notebook").getUnclippedBoundsInRoot().top
    assertEquals(Spacing.lg.value * 2, firstSectionTop.value, ONE_PIXEL_TOLERANCE.value)
  }

  @Test
  fun showsKernelCardsAsLetterTiles() {
    compose.setContent {
      LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {})
    }

    // Both kernel cards carry the kernelspec's label - "Python 3" twice - so the sections and
    // their letter tiles are what tell a Notebook card from a Console one. The letter itself is
    // decoration: it must not reach the semantics tree, or TalkBack reads "P Python 3".
    assertEquals(2, compose.onAllNodesWithText("Python 3").fetchSemanticsNodes().size)
    assertEquals(0, compose.onAllNodesWithText("P").fetchSemanticsNodes().size)
    compose.onNodeWithText("Terminal").assertIsDisplayed()
    compose.onNodeWithText("Text File").assertIsDisplayed()
    compose.onNodeWithText("Markdown File").assertIsDisplayed()
    compose.onNodeWithText("Python File").assertIsDisplayed()
  }

  @Test
  fun tappingACardLaunchesItsCommand() {
    val viewModel = viewModel()
    compose.setContent {
      LauncherScreen(cwd = "reports", viewModel = viewModel, onLaunched = {})
    }

    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_TEXT_FILE.name}").performClick()

    compose.waitForIdle()
    // The fake's createFile throws, so the tap's evidence is the surfaced error - the same
    // assertion `aFailedLaunchSurfacesTheError` makes at the ViewModel layer.
    compose.onNodeWithText("Error").assertIsDisplayed()
  }

  @Test
  fun everyCardKeepsItsWholeLabelAtTwiceTheFontScale() {
    compose.setContent { AtFontScale(2f) { LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {}) } }

    LAUNCHER_ITEMS.forEach { item ->
      assertFalse("${item.command}'s label is cut", labelOf(item).isCut())
      assertFalse("${item.command}'s label breaks inside a word", labelOf(item).breaksInsideAWord())
      compose.onNodeWithTag(cardTag(item)).assertHeightIsAtLeast(CARD_MINIMUM)
    }
  }

  @Test
  fun cardsInOneRowAreAsTallAsTheTallestAtTwiceTheFontScale() {
    // The tablet's one-pane width, which puts the four cards of the Other section in one row. The bounds
    // are the unclipped ones: the window cuts the row's foot, and a clipped height reads the same for all.
    compose.setContent {
      Box(Modifier.requiredWidth(TABLET_ONE_PANE_WIDTH)) {
        AtFontScale(2f) { LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {}) }
      }
    }

    val rows = LAUNCHER_ITEMS.map { compose.onNodeWithTag(cardTag(it)).getUnclippedBoundsInRoot() }.groupBy { it.top }
    rows.values.forEach { row ->
      val heights = row.map { it.bottom - it.top }
      assertTrue("a row's cards are $heights tall", heights.max() - heights.min() < ONE_PIXEL_TOLERANCE)
    }
  }

  @Test
  fun aCardIsASquareAtTheDefaultFontScale() {
    compose.setContent { AtFontScale(1f) { LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {}) } }

    LAUNCHER_ITEMS.forEach { item ->
      compose.onNodeWithTag(cardTag(item)).assertHeightIsEqualTo(CARD_MINIMUM)
    }
  }

  private fun cardTag(item: LauncherItem): String = "$LAUNCHER_CARD_TAG-${item.command.name}"

  /** The label's own node: the card merges its descendants, so the text is only in the unmerged tree. */
  private fun labelOf(item: LauncherItem): SemanticsNodeInteraction =
    compose.onNode(hasText(item.label) and hasAnyAncestor(hasTestTag(cardTag(item))), useUnmergedTree = true)

  private companion object {
    val CARD_MINIMUM = 100.dp
    val ONE_PIXEL_TOLERANCE = 0.5.dp
    val TABLET_ONE_PANE_WIDTH = 800.dp
  }
}
