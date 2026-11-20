package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.WheelTarget
import com.jupydroid.app.domain.model.platformRefusal
import com.jupydroid.app.domain.port.WheelIndex
import com.jupydroid.app.terminal.SgrText
import kotlinx.serialization.json.JsonPrimitive

/**
 * pip's own line for a requirement no version could satisfy at all: `(from versions: none)` is what
 * separates a miss from a resolver answer that lists versions and matches none of them.
 *
 * The name is the requirement's own, as pip echoes it - `duckdb`, `duckdb==2.0.0`, `duckdb[all]` -
 * so the capture stops where PEP 508's name does and the extras or specifier after it are skipped.
 */
private val MISSING_REQUIREMENT = Regex(
  """Could not find a version that satisfies the requirement ([A-Za-z0-9][A-Za-z0-9._-]*)""" +
    """[^\s(]*\s*\(from versions: none\)""",
)

/**
 * The packages pip found no distribution for in [outputs], in the order it named them.
 *
 * Read off the cell's stdout streams, which is where a `%pip` run's text arrives - `%pip` runs
 * `python -m pip` through `shell.system`, so pip writes to the kernel's own descriptors
 * (`IPython/core/magics/packaging.py`, `PackagingMagics.pip`). Escapes are stripped first, because
 * pip colours the line: `RenderedOutput` draws it through the same walk.
 *
 * An output without [MISS_MARKER] is skipped before the walk, so a cell that printed two hundred
 * thousand lines of anything else costs one `contains` rather than an SGR parse of all of it
 * (`StreamFoldTest.aRunawayPrintFoldsInLinearTime` is the same hazard, measured).
 *
 * ```
 * pipMissingRequirements(cellOutputs)   // ["duckdb"]
 * ```
 */
internal fun pipMissingRequirements(outputs: List<CellOutput>): List<String> {
  val text = outputs.filterIsInstance<CellOutput.Stream>()
    .filter { it.text.contains(MISS_MARKER) }
    .joinToString("") { it.text }
  if (text.isEmpty()) return emptyList()
  return MISSING_REQUIREMENT.findAll(SgrText.parse(text).text).map { it.groupValues[1] }.distinct().toList()
}

/** Every line [MISSING_REQUIREMENT] can match carries this, so a stream without it holds no miss. */
private const val MISS_MARKER = "Could not find a version that satisfies the requirement"

/**
 * The note a cell shows when pip found no distribution and PyPI's own release files confirm this
 * Space's platform is why - and nothing when they do not, which is every other case: a typo, a
 * package pip can install, an index that cannot be asked (ADR-0165).
 *
 * A `display_data` rather than a `stream`: a stream folds into the same-name stream before it
 * (`List<CellOutput>.withOutputFolded`), which would save and draw the app's words as pip's.
 *
 * ```
 * PipInstallExplanation(FakeWheelIndex()).noteFor(cellOutputs)   // null
 * ```
 */
class PipInstallExplanation(private val wheelIndex: WheelIndex, private val target: WheelTarget = WheelTarget.SPACE) {
  /** The output to append to [outputs], or null when there is nothing true to add. */
  suspend fun noteFor(outputs: List<CellOutput>): CellOutput.DisplayData? {
    val refusals = pipMissingRequirements(outputs).mapNotNull { name ->
      wheelIndex.releasesOf(name)?.platformRefusal(name, target)
    }
    if (refusals.isEmpty()) return null
    // A blank line first: outputs are drawn one under the other with no gap, and the note is not pip's.
    return CellOutput.DisplayData(mapOf("text/plain" to JsonPrimitive("\n" + refusals.joinToString("\n\n"))))
  }
}
