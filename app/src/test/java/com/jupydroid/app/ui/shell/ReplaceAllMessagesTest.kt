package com.jupydroid.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `buildReplaceAllConfirmationMessage` and `buildAfterReplaceAllMessage` transcribed
 * (`searchView.ts:855-928`) - sixteen arms, one test each.
 *
 * **The gate for a transcription is equality**, which is `PathGlobParityTest`'s rule and
 * `MatchPreviewTest`'s: what is pinned is the reference's own strings, not a sentence that reads
 * about right. Eight arms per message is not the reference being verbose - it is four plurals
 * crossed with whether a replacement was typed at all, and *"Replace 1 occurrences across 1 files"*
 * is what one arm fewer prints.
 */
class ReplaceAllMessagesTest {
  // --- the question, `buildReplaceAllConfirmationMessage` ---------------------------------------

  @Test
  fun oneOccurrenceInOneFileWithAReplacement() {
    assertEquals("Replace 1 occurrence across 1 file with 'dog'?", replaceAllConfirmation(1, 1, "dog"))
  }

  /** An empty replacement is a **deletion**, and the reference drops the clause rather than quoting ''. */
  @Test
  fun oneOccurrenceInOneFileDeleted() {
    assertEquals("Replace 1 occurrence across 1 file?", replaceAllConfirmation(1, 1, ""))
  }

  @Test
  fun oneOccurrenceInSeveralFilesWithAReplacement() {
    assertEquals("Replace 1 occurrence across 3 files with 'dog'?", replaceAllConfirmation(1, 3, "dog"))
  }

  @Test
  fun oneOccurrenceInSeveralFilesDeleted() {
    assertEquals("Replace 1 occurrence across 3 files?", replaceAllConfirmation(1, 3, ""))
  }

  @Test
  fun severalOccurrencesInOneFileWithAReplacement() {
    assertEquals("Replace 5 occurrences across 1 file with 'dog'?", replaceAllConfirmation(5, 1, "dog"))
  }

  @Test
  fun severalOccurrencesInOneFileDeleted() {
    assertEquals("Replace 5 occurrences across 1 file?", replaceAllConfirmation(5, 1, ""))
  }

  @Test
  fun severalOccurrencesInSeveralFilesWithAReplacement() {
    assertEquals("Replace 5 occurrences across 2 files with 'dog'?", replaceAllConfirmation(5, 2, "dog"))
  }

  @Test
  fun severalOccurrencesInSeveralFilesDeleted() {
    assertEquals("Replace 5 occurrences across 2 files?", replaceAllConfirmation(5, 2, ""))
  }

  // --- the answer, `buildAfterReplaceAllMessage` ------------------------------------------------

  @Test
  fun oneOccurrenceInOneFileWasReplaced() {
    assertEquals("Replaced 1 occurrence across 1 file with 'dog'.", replaceAllReport(1, 1, "dog"))
  }

  @Test
  fun oneOccurrenceInOneFileWasDeleted() {
    assertEquals("Replaced 1 occurrence across 1 file.", replaceAllReport(1, 1, ""))
  }

  @Test
  fun oneOccurrenceInSeveralFilesWasReplaced() {
    assertEquals("Replaced 1 occurrence across 3 files with 'dog'.", replaceAllReport(1, 3, "dog"))
  }

  @Test
  fun oneOccurrenceInSeveralFilesWasDeleted() {
    assertEquals("Replaced 1 occurrence across 3 files.", replaceAllReport(1, 3, ""))
  }

  @Test
  fun severalOccurrencesInOneFileWereReplaced() {
    assertEquals("Replaced 5 occurrences across 1 file with 'dog'.", replaceAllReport(5, 1, "dog"))
  }

  @Test
  fun severalOccurrencesInOneFileWereDeleted() {
    assertEquals("Replaced 5 occurrences across 1 file.", replaceAllReport(5, 1, ""))
  }

  @Test
  fun severalOccurrencesInSeveralFilesWereReplaced() {
    assertEquals("Replaced 5 occurrences across 2 files with 'dog'.", replaceAllReport(5, 2, "dog"))
  }

  @Test
  fun severalOccurrencesInSeveralFilesWereDeleted() {
    assertEquals("Replaced 5 occurrences across 2 files.", replaceAllReport(5, 2, ""))
  }

  /**
   * The two messages differ in **tense and punctuation** and in nothing else - one asks and one
   * reports. Held on one case rather than assumed, because a shared helper that got the tense from
   * the wrong side would pass all sixteen tests above only if they were written as pairs.
   */
  @Test
  fun theQuestionAndTheAnswerAreTheSameSentenceInTwoTenses() {
    assertEquals("Replace 5 occurrences across 2 files with 'dog'?", replaceAllConfirmation(5, 2, "dog"))
    assertEquals("Replaced 5 occurrences across 2 files with 'dog'.", replaceAllReport(5, 2, "dog"))
  }

  // --- the two refusals, which quote different messages ------------------------------------------

  /**
   * `_validateBeforePrepare`'s own `Error`, which is a template literal rather than a localized
   * string (`bulkTextEdits.ts:216`) - so this is the sentence, not a translation of it.
   */
  @Test
  fun aStaleResourceQuotesTheReferencesOwnConflict() {
    assertEquals("notes.py has changed in the meantime", staleResourceMessage("notes.py"))
  }

  /**
   * `cannotWorkspaceUndoDueToChanges` (`undoRedoService.ts:864-867`), with both substitutions
   * resolved: `{0}` is the bulk edit's label and a search replace passes none, so it is
   * `IBulkEditService`'s own default *"Workspace Edit"* (`bulkEditService.ts:152`), and `{1}` is
   * the impacted resources joined by `', '`.
   */
  @Test
  fun aRefusedUndoNamesTheCommandAndEveryFileThatMovedOn() {
    assertEquals(
      "Could not undo 'Workspace Edit' across all files because changes were made to " +
        "notes.py, reports/a.py",
      couldNotUndoMessage(listOf("notes.py", "reports/a.py")),
    )
  }

  /** One file is the same sentence with nothing to join - the reference has no singular arm here. */
  @Test
  fun aRefusedUndoOverOneFileNamesTheOneFile() {
    assertEquals(
      "Could not undo 'Workspace Edit' across all files because changes were made to notes.py",
      couldNotUndoMessage(listOf("notes.py")),
    )
  }
}
