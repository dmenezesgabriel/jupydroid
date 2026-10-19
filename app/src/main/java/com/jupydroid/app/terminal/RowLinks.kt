package com.jupydroid.app.terminal

/**
 * One row's hyperlinks: the URI an `OSC 8` link (`HyperlinkCommand`) was open with when each column
 * was written, or `null`.
 *
 * The counterpart of [RowStyles], kept apart from [CellStyle] because a link does not fit its `Long`:
 * five bits are free there, and an id that small would be reused while rows still in the scrollback
 * hold the old one, opening the wrong address. The URI itself is stored instead - one `String`
 * shared by every cell of a link - so there is no table of links to prune. The array is made on the
 * first link written, so a row that never held one costs a `null`.
 *
 * ```
 * val links = RowLinks(columns = 80)
 * links.set(0, "https://example.org")
 * ```
 */
internal class RowLinks(private val columns: Int) {
  private var uris: Array<String?>? = null

  fun at(column: Int): String? = uris?.get(column)

  fun set(column: Int, uri: String?) {
    val stored = uris
    if (stored != null) {
      stored[column] = uri
      return
    }
    if (uri == null) return
    uris = arrayOfNulls<String>(columns).also { it[column] = uri }
  }

  /** No column a link any more, which is what clearing a row does. */
  fun clear() {
    uris = null
  }
}

/**
 * The URI of the `OSC 8` link written over ([column], [row]), or `null`. An extension because
 * [TerminalGrid] is at detekt's `TooManyFunctions` cap.
 *
 * ```
 * grid.hyperlinkAt(column = 0, row = 0)   // "https://example.org" over a linked cell
 * ```
 */
fun TerminalGrid.hyperlinkAt(column: Int, row: Int): String? = rowAt(row).links.at(column)
