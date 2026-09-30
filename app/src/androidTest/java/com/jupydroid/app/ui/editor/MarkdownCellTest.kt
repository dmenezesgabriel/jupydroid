package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Audit A1c: markdown renders, and there is a way back to the source and forward again. */
@RunWith(AndroidJUnit4::class)
class MarkdownCellTest {
  @get:Rule val compose = createComposeRule()

  /**
   * `toc:display-numbering` puts the number **in the document**, which is what that command's own
   * label promises - the reference writes it into the rendered heading itself
   * (`packages/notebook/src/toc.ts:733-748`), not only into the panel.
   */
  @Test
  fun aNumberedHeadingCarriesItsNumberInTheDocument() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = true,
        {},
        {},
        headingPrefix = "1.2. ",
      )
    }

    compose.onNodeWithText("1.2. Results").assertIsDisplayed()
  }

  @Test
  fun rendersTheMarkdownRatherThanItsSource() {
    compose.setContent { MarkdownCell(rememberTextFieldState("## Results"), "## Results", rendered = true, {}, {}) }

    // The heading's text without its hashes is the whole point of rendering.
    compose.onNodeWithText("Results").assertIsDisplayed()
    compose.onNodeWithText("## Results").assertDoesNotExist()
  }

  @Test
  fun tappingTheRenderedCellAsksForTheEditor() {
    var asked = false
    compose.setContent {
      MarkdownCell(rememberTextFieldState("# Title"), "# Title", rendered = true, { asked = true }, {})
    }

    compose.onNodeWithContentDescription(RENDERED_MARKDOWN).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { asked }
  }

  /**
   * Asking for the editor is not the same as being able to type in it. The tap used to leave an
   * unfocused field, so the next keystroke went nowhere - invisible to every test here, because
   * `performTextInput` focuses the node itself, and caught only by `a1-markdown` on the tablet.
   *
   * The tap flips both flags, which is what `withMarkdownEditorOpened` does: it opens the editor
   * **and** asks for the caret, and only a tap does the second.
   */
  @Test
  fun openingTheEditorPutsTheCursorInIt() {
    var rendered by mutableStateOf(true)
    var takesCaret by mutableStateOf(false)
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("# Title"),
        "# Title",
        rendered,
        onEditRequested = {
          rendered = false
          takesCaret = true
        },
        onSourceEdited = {},
        takesCaret = takesCaret,
      )
    }

    compose.onNodeWithContentDescription(RENDERED_MARKDOWN).performClick()

    compose.onNodeWithText("# Title").assertIsFocused()
  }

  /**
   * **And a cell unrendered by a command does not.** `changeCellType` unrenders every cell it
   * visits and ends in command mode (`packages/notebook/src/actions.tsx:3308-3314`), so a run
   * changed to markdown composes several of these at once - each one asking for the caret was the
   * soft keyboard coming up over a notebook nobody was typing in.
   */
  @Test
  fun anEditorNobodyAskedForLeavesTheCaretAlone() {
    compose.setContent {
      MarkdownCell(rememberTextFieldState("# Title"), "# Title", rendered = false, {}, {})
    }

    compose.onNodeWithText("# Title").assertIsNotFocused()
  }

  /** Rendered empty markdown is nothing at all, which would be a row with no way into it. */
  @Test
  fun anEmptyCellStillOffersSomethingToTap() {
    compose.setContent { MarkdownCell(rememberTextFieldState(""), "", rendered = true, {}, {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertIsDisplayed()
  }

  @Test
  fun editingShowsTheSourceAgain() {
    compose.setContent { MarkdownCell(rememberTextFieldState("## Results"), "## Results", rendered = false, {}, {}) }

    compose.onNodeWithText("## Results").assertIsDisplayed()
  }

  /**
   * The reason `ReportEdits` flushes on dispose: the edit is debounced, and leaving the editor
   * for the rendered view within that window used to drop the last keystrokes.
   */
  @Test
  fun switchingBackToRenderedKeepsWhatWasJustTyped() {
    var source by mutableStateOf("start")
    var rendered by mutableStateOf(false)
    compose.setContent {
      MarkdownCell(
        state = rememberTextFieldState("start"),
        source = source,
        rendered = rendered,
        onEditRequested = { rendered = false },
        onSourceEdited = { source = it },
      )
    }

    compose.onNodeWithText("start").performTextInput(" and more")
    rendered = true

    compose.waitUntil(TIMEOUT_MILLIS) { source == "start and more" }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    /**
     * A device's non-linear font scaling moves a laid-out line off its style's declared height:
     * measured at about 7% in P2m-2 (19.2dp declared against 20.67dp drawn), which on the tallest
     * heading here is under 2dp. The defect this guards against was 10dp.
     */
    const val TOLERANCE_DP = 2.0f

    /** Markdown's own six, which is what `headingStyle` branches over. */
    const val HEADING_LEVELS = 6
  }

  // --- the heading collapser, beside the heading it folds ---------------------------------

  /**
   * **The caret is a disclosure triangle on the heading, not chrome above it.** JupyterLab appends
   * it to the cell's *prompt node* (`packages/cells/src/widget.ts:2386-2408`) and pins it there at
   * `position: absolute; right: 0; top: 0; bottom: 0` (`packages/cells/style/widget.css:154-168`) -
   * the right edge of the 64px prompt column, which is immediately left of the content. Six
   * per-level rules then set `background-position-y: calc(0.3 * <that level's font size>)` so the
   * 25px caret stays on the heading's **first line** as the heading shrinks: for `h1` at 29px in a
   * ~43px line box that puts the icon's centre at 21.2px against a line centre of 21.5px.
   *
   * It used to sit in this app's own header strip, a row above the heading and beside the Run
   * control, with nothing connecting it to what it folds
   * (before this).
   *
   * IntelliJ is empty for this question and it was checked: everything under `notebooks/` that
   * collapses collapses *outputs* (`CollapsingComponent`,
   * `NotebookOutputCollapseAllInSelectedCellsAction`); markdown heading folding lives in its
   * closed-source Jupyter plugin.
   */
  @Test
  fun theCollapserSitsBesideTheHeadingRatherThanAboveIt() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = true,
        {},
        {},
        fold = HeadingFold(level = 2, collapsed = false),
      )
    }

    val caret = compose.onNodeWithContentDescription(COLLAPSE_HEADING).getUnclippedBoundsInRoot()
    val heading = compose.onNodeWithText("Results").getUnclippedBoundsInRoot()
    assertTrue("caret at $caret is not left of the heading at $heading", caret.right <= heading.left)
    assertTrue(
      "caret at $caret shares no line with the heading at $heading",
      caret.top < heading.bottom && heading.top < caret.bottom,
    )
  }

  /**
   * **Centred on the heading's line, which is what the reference's six per-level rules buy.** Each
   * sets `background-position-y: calc(0.3 * <that level's font size>)` for a 25px caret
   * (`packages/cells/style/widget.css:184-213`), landing `h1`'s icon centre at 21.2px against a
   * line centre of 21.5px.
   *
   * Top-aligned inside its own 48dp button ours sat **10dp low** - measured on device at 316
   * against 306 - which is the kind of miss that looks like nothing in a code review and like a
   * mistake on screen. Every level, because the whole point is that it tracks the size.
   */
  @Test
  fun theCaretIsCentredOnTheHeadingsLine() {
    // All six in one composition: `setContent` may be called once per rule, so a loop around it
    // throws rather than testing the next level.
    compose.setContent {
      Column {
        (1..HEADING_LEVELS).forEach { level ->
          MarkdownCell(
            rememberTextFieldState("${"#".repeat(level)} Level $level"),
            "${"#".repeat(level)} Level $level",
            rendered = true,
            {},
            {},
            fold = HeadingFold(level = level, collapsed = false),
          )
        }
      }
    }

    (1..HEADING_LEVELS).forEach { level ->
      val caret = compose.onAllNodesWithContentDescription(COLLAPSE_HEADING)[level - 1]
        .getUnclippedBoundsInRoot()
      val heading = compose.onNodeWithText("Level $level").getUnclippedBoundsInRoot()
      assertEquals(
        "level $level",
        ((heading.top + heading.bottom) / 2).value,
        ((caret.top + caret.bottom) / 2).value,
        TOLERANCE_DP,
      )
    }
  }

  /**
   * **The caret keeps a full-size press even where its line is smaller than one.** Centring it on
   * the heading's line means its box is that line's height - 20sp at level 6 against `titleSmall` -
   * and the fix for that is the button overflowing the box rather than being shrunk by it. Level 6
   * is the one that would break: this test moved here from `CellTouchTargetTest` when the caret
   * left the header, and it is the reason the box does not clip.
   */
  @Test
  fun theCaretKeepsItsPressAtTheSmallestHeading() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("###### Deep"),
        "###### Deep",
        rendered = true,
        {},
        {},
        fold = HeadingFold(level = HEADING_LEVELS, collapsed = false),
      )
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /**
   * **Rendered only, which retires P2n-3's both-states assertion.** JupyterLab's prompt node
   * carries the caret whether the editor is showing or not - `InputArea` builds it once and hides
   * only the *editor* when the cell renders (`packages/cells/src/inputarea.ts:53-56,111-119`) -
   * and this test used to transcribe that. Composing it while the source is open spends a column
   * of the editor's width on an affordance that cannot be used mid-typing anyway: collapsing the
   * section would close what is being written (ADR-0057). The header's play - the control that
   * renders the cell - is what is left on it.
   */
  @Test
  fun theCollapserIsGoneWhileTheHeadingsSourceIsOpen() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = false,
        {},
        {},
        fold = HeadingFold(level = 2, collapsed = false),
      )
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).assertDoesNotExist()
  }

  /** The other half of the pair: rendered, the caret is back where it folds what it names. */
  @Test
  fun theCollapserIsBesideTheHeadingOnceItRenders() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = true,
        {},
        {},
        fold = HeadingFold(level = 2, collapsed = false),
      )
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).assertIsDisplayed()
  }

  /**
   * **What the caret's absence buys, measured rather than asserted in prose**: a heading's open
   * source starts at the same x as a cell that never had a caret, so every editor in the document
   * runs to one left bound while it is being typed into. This is the invariant P2n spent itself
   * establishing, read here at the state P2n could not reach.
   */
  @Test
  fun anOpenHeadingsSourceStartsWhereProseDoes() {
    compose.setContent {
      Column {
        MarkdownCell(
          rememberTextFieldState("## Results"),
          "## Results",
          rendered = false,
          {},
          {},
          fold = HeadingFold(level = 2, collapsed = false),
        )
        MarkdownCell(rememberTextFieldState("just prose"), "just prose", rendered = false, {}, {})
      }
    }

    val heading = compose.onNodeWithText("## Results").getUnclippedBoundsInRoot()
    val prose = compose.onNodeWithText("just prose").getUnclippedBoundsInRoot()
    assertEquals(prose.left, heading.left)
  }

  /** A cell that is not a heading has no caret at all - `level > 0` is the reference's own guard. */
  @Test
  fun proseCarriesNoCollapser() {
    compose.setContent {
      MarkdownCell(rememberTextFieldState("just prose"), "just prose", rendered = true, {}, {})
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).assertDoesNotExist()
  }

  /** The label names what a tap will do, so it is the other word once the section is folded. */
  @Test
  fun aFoldedHeadingOffersToExpandInstead() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = true,
        {},
        {},
        fold = HeadingFold(level = 2, collapsed = true),
      )
    }

    compose.onNodeWithContentDescription(EXPAND_HEADING).assertIsDisplayed()
  }

  @Test
  fun tappingTheCollapserAsksForTheToggle() {
    var toggles = 0
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = true,
        {},
        {},
        fold = HeadingFold(level = 2, collapsed = false),
        onToggleHeadingCollapse = { toggles++ },
      )
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).performClick()

    assertEquals(1, toggles)
  }
}
