package com.jupydroid.extension.api

/**
 * The characters every key row above the soft keyboard types, held once so the rows cannot
 * drift: `SymbolRow` on the notebook (and, through `SymbolRowWhileTyping`, the file editor and
 * the console prompt), `ChatKeyRow` in `:extensions:ai`, and `TerminalExtraKeysRow` each draw
 * from this beside their own surface keys - `Tab` and the history pair, `←`/`→`, `Esc` and the
 * terminal's latches.
 *
 * Here rather than in `:app` for `WindowKeyRowSlot`'s own reason: an extension's field is typed
 * into too, and an extension builds against this module and never against `:app` (ADR-0104).
 *
 * Ordered with the cell row's twelve first - the brackets, quotes, colon, `=` and `_` Python
 * reaches for, in the order it reaches for them: the colon ends every block header, the pairs
 * come next, then the comment hash and the underscore every snake_case name needs. The
 * remaining nineteen follow in the order the item that named the set names them.
 */
public val SymbolRowKeys: List<String> =
  listOf(
    ":", "(", ")", "[", "]", "{", "}", "'", "\"", "=", "#", "_",
    "`", "/", "\\", "%", "+", "-", "*", "<", ">", ";", "&", "|", "~", "^", "@", "!", "?", ",", ".",
  )
