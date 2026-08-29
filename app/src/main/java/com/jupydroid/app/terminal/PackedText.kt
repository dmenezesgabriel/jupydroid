package com.jupydroid.app.terminal

/**
 * Reading code points out of a packed UTF-16 [CharArray] - what a terminal row stores its text in.
 *
 * Written once here because the decode appears four times across [RowText] and [TerminalRow]
 * where the reference (`terminal-emulator/src/main/java/com/termux/terminal/`, termux-app
 * `3df69d1d`, Apache 2.0) inlines it each time.
 */

/** The code point beginning at [index]; [charCountAt] says how far past it the next one starts. */
internal fun codePointAt(chars: CharArray, index: Int): Int {
  val first = chars[index]
  return if (first.isHighSurrogate()) Character.toCodePoint(first, chars[index + 1]) else first.code
}

/** The java chars the code point at [index] occupies: two for a surrogate pair, otherwise one. */
internal fun charCountAt(chars: CharArray, index: Int): Int = if (chars[index].isHighSurrogate()) 2 else 1
