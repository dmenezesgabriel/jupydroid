package com.jupydroid.app.domain.model

import kotlinx.serialization.json.JsonElement

/**
 * A stream output's `name`. [STDIN] is never sent by a kernel: it is the front end's echo of an
 * answer to `input()`, which JupyterLab writes into the notebook beside the prints around it
 * (jupyterlab@7f0e690 packages/outputarea/src/widget.ts, `onInputRequest`).
 */
enum class StreamName { STDOUT, STDERR, STDIN }

/**
 * mime -> payload, with each payload kept **as JSON** rather than flattened to a string.
 *
 * nbformat types a bundle's values as `MultilineString | PartialJSONObject`, so a payload may be
 * a whole object - which is how `application/json` and every jupyter-widget view is carried.
 * JupyterLab keeps them the same way: `convertBundle` copies each value through `extract`, which
 * returns primitives as they are and deep-clones anything else, stringifying nothing
 * (`packages/rendermime/src/outputmodel.ts:334-356`).
 *
 * Multi-line text arrives joined and leaves split again; both rules live in
 * `com.jupydroid.app.data.disk` beside the format they belong to.
 */
typealias MimeBundle = Map<String, JsonElement>

/** A single Jupyter output message the kernel produced for a cell. */
sealed interface CellOutput {
  /**
   * [cursor] is where the next chunk of this stream writes: the end of [text], unless a `\r` or
   * `\b` moved it back - see [withOutputFolded]. It rides on the output rather than beside it so
   * every edit that copies or restores outputs carries it, as JupyterLab keeps `_streamIndex` with
   * the last stream it built (jupyterlab@7f0e690 packages/outputarea/src/model.ts). nbformat has no
   * field for it, so a saved stream reopens with the cursor its text leaves once `\r` and `\b` are
   * interpreted - where `fromJSON` leaves it, and the end for any text this app wrote.
   */
  data class Stream(val name: StreamName, val text: String, val cursor: Int = text.length) : CellOutput

  data class Error(val name: String, val value: String, val traceback: List<String>) : CellOutput

  data class DisplayData(val mimeBundle: MimeBundle) : CellOutput

  data class ExecuteResult(val mimeBundle: MimeBundle, val executionCount: Int) : CellOutput

  /**
   * The kernel's `clear_output` instruction: drop the cell's outputs. With [wait] the drop
   * happens when the next output arrives instead of at once - the swap `wait=True` exists for.
   */
  data class ClearOutput(val wait: Boolean = false) : CellOutput
}
