package com.jupydroid.app.terminal

/**
 * The numeric parameters between a sequence's introducer and its final byte: `31;41` in
 * `ESC [ 31;41 m`, `38:5:119` in `ESC [ 38:5:119 m`.
 *
 * Transcribed from `TerminalEmulator.parseArg`, `getArg` and the `mArgs` / `mArgIndex` /
 * `mArgsSubParamsBitSet` fields (`terminal-emulator/src/main/java/com/termux/terminal/
 * TerminalEmulator.java`, termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under
 * that repository's `LICENSE.md` exception).
 *
 * **A class of its own, where the reference has four fields and two methods on a 2,617-line
 * emulator.** Every escape state that takes parameters shares one of these, and a parameter buffer
 * with no screen attached is something a test can drive directly - upstream it is reachable only by
 * driving a whole terminal.
 *
 * ```
 * val parameters = EscapeParameters()
 * parameters.startSequence()
 * "31".forEach { parameters.accept(it.code) }
 * parameters.at(0, default = -1, treatZeroAsDefault = false)   // 31
 * ```
 */
class EscapeParameters {
  private val values = IntArray(MAX_PARAMETERS)

  /** The index of the parameter still being read; [count] is this plus one. */
  private var openIndex = 0

  /** Bit `n` is set when parameter `n` was opened by `:` rather than `;`. */
  private var subParameterBits = 0

  /**
   * How many parameters the sequence has so far - **never zero**. An empty `ESC [ m` carries one
   * absent parameter, which is what makes it a reset rather than a sequence with nothing to apply.
   */
  val count: Int get() = openIndex + 1

  /** Forget the sequence before this one. Called when `ESC` arrives, as the reference does. */
  fun startSequence() {
    values.fill(ABSENT)
    openIndex = 0
    subParameterBits = 0
  }

  /**
   * Take one code point as a parameter byte. Returns false when it is not one - the caller's cue
   * that this is the sequence's final or intermediate byte.
   */
  fun accept(codePoint: Int): Boolean = when (codePoint) {
    in '0'.code..'9'.code -> {
      appendDigit(codePoint - '0'.code)
      true
    }

    ';'.code, ':'.code -> {
      openNextParameter(isSubParameter = codePoint == ':'.code)
      true
    }

    else -> false
  }

  /**
   * Parameter [index], or [default] when it is absent - and when it is zero, if the caller reads a
   * zero as "unstated". Cursor motions do (`CSI 0 A` moves one row); SGR does not (`CSI 0 m` is
   * the reset).
   */
  fun at(index: Int, default: Int, treatZeroAsDefault: Boolean): Int {
    val value = values[index]
    return if (value < 0 || (value == 0 && treatZeroAsDefault)) default else value
  }

  /** Whether parameter [index] was opened with `:`, i.e. belongs to the parameter before it. */
  fun isSubParameter(index: Int): Boolean = subParameterBits and (1 shl index) != 0

  private fun appendDigit(digit: Int) {
    val current = values[openIndex]
    val grown = if (current < 0) digit else current * DECIMAL + digit
    values[openIndex] = minOf(grown, MAX_VALUE)
  }

  /**
   * Past the last parameter the separator is **dropped and the sequence goes on**, so the digits
   * after it land on the parameter already open. The reference logs an error and continues; there
   * is nothing here to log to, and `ControlSequenceIntroducerTest.testManyParameters` pins the
   * behaviour rather than the log.
   */
  private fun openNextParameter(isSubParameter: Boolean) {
    if (openIndex + 1 >= MAX_PARAMETERS) return
    openIndex++
    if (isSubParameter) subParameterBits = subParameterBits or (1 shl openIndex)
  }

  companion object {
    /** The reference's `MAX_ESCAPE_PARAMETERS`, and the width of [subParameterBits]. */
    const val MAX_PARAMETERS = 32

    /** A parameter nobody wrote a digit into. */
    private const val ABSENT = -1

    private const val MAX_VALUE = 9999
    private const val DECIMAL = 10
  }
}
