package com.jupydroid.app.domain.model

import java.text.Collator

/** What a workspace entry is, which decides how it sorts and what opens it. */
enum class WorkspaceEntryKind {
  DIRECTORY,
  FILE,
}

/**
 * One thing in the workspace: a file or a directory, named by its path from the workspace root.
 *
 * The path is the identity, as it is for a notebook since X1b and as it is in JupyterLab, where a
 * document is addressed by path throughout (`Contents.IModel.path`).
 *
 * @property name what the row shows - the last segment of [path]
 * @property path relative to the workspace root, so `reports/summary.ipynb`
 */
data class WorkspaceEntry(
  val name: String,
  val path: String,
  val kind: WorkspaceEntryKind,
)

/**
 * The order JupyterLab lists a directory in.
 *
 * `Private.sort` (`packages/filebrowser/src/listing.ts`): directories first via `getPriority`, then
 * by name through `Intl.Collator` with `numeric: sortFileNamesNaturally` - and that setting
 * defaults to **true**, so `file2` precedes `file10`. Notebooks are *not* hoisted: the
 * `sortNotebooksFirst` that would do it defaults to false.
 *
 * ```
 * repository.list(path).inWorkspaceOrder()
 * ```
 *
 * `java.text.Collator` is the JVM's equivalent of `Intl.Collator` - locale-aware and
 * case-insensitive at its default strength - with the numeric-aware comparison added on top,
 * because the JVM collator has no `numeric` option of its own.
 */
fun List<WorkspaceEntry>.inWorkspaceOrder(): List<WorkspaceEntry> =
  sortedWith(compareBy<WorkspaceEntry> { it.kind != WorkspaceEntryKind.DIRECTORY }.then(byNaturalName))

/**
 * Names compared the way a person reads them: `file2` before `file10`.
 *
 * Digit runs are compared as numbers and everything else through a [Collator], so case and accents
 * follow the locale rather than the byte values - `Beta` sorts beside `beta`, not before every
 * lowercase letter.
 */
private val byNaturalName = Comparator<WorkspaceEntry> { left, right ->
  compareNaturally(left.name, right.name)
}

/**
 * `file2` before `file10`, and `Beta` beside `beta`.
 *
 * Each name is split into runs of digits and runs of everything else, and the runs are compared
 * pairwise: digits as numbers, the rest through a [Collator] so case and accents follow the locale
 * rather than the byte values. That is what `Intl.Collator{ numeric: true }` does for the
 * reference, and the JVM collator has no numeric option of its own.
 */
private fun compareNaturally(left: String, right: String): Int {
  val leftRuns = runsOf(left)
  val rightRuns = runsOf(right)
  for ((leftRun, rightRun) in leftRuns.zip(rightRuns)) {
    val order = compareRuns(leftRun, rightRun)
    if (order != 0) return order
  }
  return leftRuns.size - rightRuns.size
}

private fun runsOf(name: String): List<String> = RUNS.findAll(name).map { it.value }.toList()

private fun compareRuns(left: String, right: String): Int =
  if (left[0].isDigit() && right[0].isDigit()) compareNumbers(left, right) else COLLATOR.compare(left, right)

/** Leading zeros carry no value, so `007` and `7` are the same number and the longer run is larger. */
private fun compareNumbers(left: String, right: String): Int {
  val leftDigits = left.trimStart('0')
  val rightDigits = right.trimStart('0')
  return if (leftDigits.length != rightDigits.length) {
    leftDigits.length - rightDigits.length
  } else {
    leftDigits.compareTo(rightDigits)
  }
}

private val RUNS = Regex("""\d+|\D+""")

/** SECONDARY so accents still separate names while case does not, which is what the browser shows. */
private val COLLATOR: Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
