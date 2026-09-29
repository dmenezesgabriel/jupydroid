package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * Opening a search result lands the editor **on the match**, not at the top of the file.
 *
 * `searchView.open` hands `openEditor` a `selection` built from the match's own range
 * (`searchView.ts:2240-2256`), so the editor reveals it and shows which characters were found. A
 * result the reader then has to go and find for themselves is half a result.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorRevealTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /** Long enough that the match is nowhere near the top, which is what makes revealing it mean something. */
  private val text = (1..60).joinToString("\n") { if (it == LINE_WITH_THE_MATCH) "print(cat)" else "line $it" }

  private var handled = 0

  private fun show(reveal: MatchedLine?) {
    File(temp.root, "notes.py").writeText(text)
    compose.setContent {
      JupyterDroidTheme {
        FileEditorScreen(
          path = "notes.py",
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(),
          onBack = {},
          reveal = reveal,
          onRevealHandled = { handled += 1 },
        )
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** How far the editor has been scrolled - the same property a screen reader reads it from. */
  private fun scrolledBy(): Float = compose.onNode(hasScrollAction()).fetchSemanticsNode()
    .config[SemanticsProperties.VerticalScrollAxisRange].value()

  /** What the field says is selected, as (start, end) - the same property a screen reader reads. */
  private fun selectionInTheEditor(): Pair<Int, Int> {
    val selection = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
      .config[SemanticsProperties.TextSelectionRange]
    return selection.start to selection.end
  }

  @Test
  fun aMatchIsSelectedWhereItSitsInTheFile() {
    show(MatchedLine(line = LINE_WITH_THE_MATCH, text = "print(cat)", range = 6..8))

    val start = text.split("\n").take(LINE_WITH_THE_MATCH - 1).sumOf { it.length + 1 } + 6
    assertEquals(start to start + 3, selectionInTheEditor())
  }

  /**
   * Opened from anywhere but a result, the caret is left exactly where it has always been.
   *
   * **And where that is was measured rather than assumed**: `TextFieldState(initialText)` puts it at
   * the *end* of the text, so this reads 473 on a 473-character file and not 0. Pre-existing and
   * untouched here - what this pins is that revealing a match changed nothing for every other way
   * of opening a file.
   */
  @Test
  fun anOrdinaryOpenLeavesTheCaretWhereItAlwaysWas() {
    show(reveal = null)

    assertEquals(text.length to text.length, selectionInTheEditor())
  }

  /**
   * **The match is scrolled on to the screen**, which is the half a selection alone does not do -
   * and the half a Maestro assertion cannot see, because the editor is one node carrying the whole
   * file and any text in it matches at any scroll position. The first run of
   * `p2x-3-open-at-the-match` went green with the editor sitting on line 1, and the screenshot is
   * what said so.
   */
  @Test
  fun theMatchIsScrolledOnToTheScreen() {
    show(MatchedLine(line = LINE_WITH_THE_MATCH, text = "print(cat)", range = 6..8))

    assertTrue("the editor never scrolled off the top", scrolledBy() > 0f)
  }

  /** And an ordinary open still starts at the top, where every file editor flow expects it. */
  @Test
  fun anOrdinaryOpenStartsAtTheTop() {
    show(reveal = null)

    assertEquals(0f, scrolledBy(), 0f)
  }

  /**
   * **The request is consumed**, so a later recomposition does not drag the reader back to a match
   * they have since scrolled away from - the rule `browseRequest` already follows.
   */
  @Test
  fun theRequestIsReportedHandledSoItCannotFireTwice() {
    show(MatchedLine(line = LINE_WITH_THE_MATCH, text = "print(cat)", range = 6..8))

    assertEquals(1, handled)
  }

  /**
   * A file rewritten between the walk and the tap opens **untouched** rather than on whatever now
   * sits at those offsets - `MatchedLine.rangeIn`'s refusal, seen from the screen.
   */
  @Test
  fun aMatchTheFileNoLongerHoldsSelectsNothing() {
    show(MatchedLine(line = 900, text = "gone", range = 0..3))

    assertEquals(text.length to text.length, selectionInTheEditor())
  }
}

/** Deliberately far down: a match on line 1 would pass without anything being revealed at all. */
private const val LINE_WITH_THE_MATCH = 47

/** Long enough for a file read off disk, short enough to fail. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
