package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a result row points **in the file**, which is what opening one has to land on.
 *
 * `searchView.open` hands `openEditor` a `selection` built from the match's own range
 * (`searchView.ts:2240`, `getEditorSelectionFromMatch`) - a range and not a line, so the editor
 * both reveals it and shows which characters were found. A [MatchedLine] carries its offsets
 * *within its line*, so the file's own offsets are what this works out.
 */
class MatchedLineRangeTest {
  private val file = "import os\nprint(cat)\nreturn cat\n"

  @Test
  fun aMatchOnTheFirstLineIsWhereItIsInTheLine() {
    val match = MatchedLine(line = 1, text = "import os", range = 7..8)

    assertEquals(7..8, match.rangeIn(file))
  }

  /** Every earlier line and its newline come first - the arithmetic the whole thing exists for. */
  @Test
  fun aLaterLineStartsAfterEveryLineBeforeIt() {
    val match = MatchedLine(line = 2, text = "print(cat)", range = 6..8)

    assertEquals(16..18, match.rangeIn(file))
  }

  @Test
  fun theLastLineIsReachedTheSameWay() {
    val match = MatchedLine(line = 3, text = "return cat", range = 7..9)

    assertEquals(28..30, match.rangeIn(file))
  }

  /**
   * **A file that changed under the match answers null rather than a wrong range.** A workspace is
   * shared with the kernel, so a cell can rewrite a file between the walk and the tap; the reference
   * refuses the same way, throwing *"has changed in the meantime"* on a stale version
   * (`replaceService.ts:213-216`). Landing on whatever now sits at those offsets would be worse than
   * landing at the top.
   */
  @Test
  fun aLineThatIsNoLongerThereIsNotGuessedAt() {
    assertNull(MatchedLine(line = 9, text = "gone", range = 0..3).rangeIn(file))
  }

  /** The line is there but no longer long enough - the same refusal, for the same reason. */
  @Test
  fun aLineTooShortForTheMatchIsNotGuessedAtEither() {
    assertNull(MatchedLine(line = 1, text = "import os", range = 40..44).rangeIn(file))
  }
}
