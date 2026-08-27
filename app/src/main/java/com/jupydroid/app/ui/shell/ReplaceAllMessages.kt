package com.jupydroid.app.ui.shell

/**
 * The two sentences a workspace replace says - the one it asks and the one it reports.
 *
 * `buildReplaceAllConfirmationMessage` and `buildAfterReplaceAllMessage` (`searchView.ts:855-928`),
 * which between them hold **sixteen** string literals. Three substitutions vary across them and
 * nothing else does: the two plurals and whether a replacement was typed at all. The reference
 * spells every combination out because `nls.localize` needs a key per literal and a translator
 * needs each whole sentence - neither of which applies here, so what is transcribed is the
 * arithmetic and every one of the sixteen results is pinned by equality in `ReplaceAllMessagesTest`.
 *
 * **Not `resultCountMessage`'s neighbours by accident** (`SearchSidePanel.kt`): that one is the
 * same kind of transcription for the same reason, and *"1 results in 1 files"* is what the arm it
 * would be missing prints.
 */

/**
 * *"Replace 5 occurrences across 2 files with 'dog'?"* - asked before anything is written.
 *
 * The counts are the matches **on screen**, which is the whole of P2x-1e: `replaceAll()` reads
 * `searchResult.count()` and `fileCount()` off the tree rather than re-running the query
 * (`searchView.ts:816-817`).
 *
 * **`truncateValue` is declined rather than transcribed** (`searchView.ts:889-899`). The reference
 * clamps a replacement of more than ten lines to ten and an ellipsis; the replace box here is
 * `singleLine = true` ([SearchReplaceRow]), so a replacement cannot hold a newline and the clamp
 * could never fire. Unreachable code is what `CLAUDE.md` forbids, and a multi-line replace box is
 * what would bring it back.
 *
 * ```
 * replaceAllConfirmation(5, 2, "dog")   // "Replace 5 occurrences across 2 files with 'dog'?"
 * ```
 *
 * @param replaceValue empty for a **deletion**, where the reference drops the clause rather than
 *   quoting an empty string.
 */
fun replaceAllConfirmation(occurrences: Int, fileCount: Int, replaceValue: String): String =
  countedSentence("Replace", "?", occurrences, fileCount, replaceValue)

/**
 * *"Replaced 5 occurrences across 2 files with 'dog'."* - the same sentence, afterwards.
 *
 * Built from the same counts as the question and **before** the write starts
 * (`searchView.ts:820`), so the report cannot disagree with what was agreed to.
 *
 * ```
 * replaceAllReport(5, 2, "dog")   // "Replaced 5 occurrences across 2 files with 'dog'."
 * ```
 */
fun replaceAllReport(occurrences: Int, fileCount: Int, replaceValue: String): String =
  countedSentence("Replaced", ".", occurrences, fileCount, replaceValue)

/** The one sentence both are, with the tense and the punctuation handed in. */
private fun countedSentence(
  verb: String,
  ends: String,
  occurrences: Int,
  fileCount: Int,
  replaceValue: String,
): String {
  val occurrence = if (occurrences == 1) "occurrence" else "occurrences"
  val file = if (fileCount == 1) "file" else "files"
  val with = if (replaceValue.isEmpty()) "" else " with '$replaceValue'"
  return "$verb $occurrences $occurrence across $fileCount $file$with$ends"
}
