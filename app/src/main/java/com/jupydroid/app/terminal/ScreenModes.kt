package com.jupydroid.app.terminal

/**
 * A DEC private mode, by the number a shell names it with in `CSI ? Pm h`.
 *
 * The reference's `DECSET_BIT_*` constants and its `mapDecSetBitToInternalBit`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]) folded into one enum: upstream the
 * external number and the internal bit are two parallel tables kept in step by hand, and a mode
 * that has one without the other is a silent no-op. **Here there is no second table to drift.**
 *
 * Only the twelve upstream maps are here. A number outside them is a mode this terminal has no
 * state for, and [ScreenModes] ignores it exactly as `mapDecSetBitToInternalBit`'s `-1` does.
 */
@Suppress("MagicNumber") // each entry *is* the name for its number - that is what the enum is for
enum class DecMode(val code: Int) {
  APPLICATION_CURSOR_KEYS(1),
  REVERSE_VIDEO(5),

  /** DECOM. The clamp it feeds needs a scrolling region, so that half arrives with DECSTBM. */
  ORIGIN(6),

  /** DECAWM, which [TerminalScreen.autoWrap] reads. */
  AUTOWRAP(7),
  CURSOR_ENABLED(25),
  APPLICATION_KEYPAD(66),

  /** DECLRMM. The condition it carries is what decides `CSI s` - see [ScrollMargins]. */
  LEFT_RIGHT_MARGIN(69),
  MOUSE_TRACKING_PRESS_RELEASE(1000),
  MOUSE_TRACKING_BUTTON_EVENT(1002),
  SEND_FOCUS_EVENTS(1004),
  MOUSE_PROTOCOL_SGR(1006),
  BRACKETED_PASTE(2004),
  ;

  companion object {
    private val byCode = entries.associateBy(DecMode::code)

    /** The mode [code] names, or null where the reference's map returns `-1`. */
    fun ofCode(code: Int): DecMode? = byCode[code]
  }
}

/**
 * Which DEC private modes are on, and which the shell has marked for recall.
 *
 * Transcribed from `mCurrentDecSetFlags` / `mSavedDecSetFlags`, `setDecsetinternalBit` and
 * `isDecsetInternalBitSet`. A set of enums rather than an `Int` of bits, because the bit positions
 * upstream carry no meaning of their own - nothing serialises them and nothing reads them as a
 * number.
 *
 * ```
 * val modes = ScreenModes()
 * modes.set(DecMode.AUTOWRAP, false)   // CSI ? 7 l
 * ```
 */
class ScreenModes {
  private val current = mutableSetOf<DecMode>()
  private val marked = mutableSetOf<DecMode>()

  init {
    reset()
  }

  fun isSet(mode: DecMode): Boolean = mode in current

  /**
   * Turn [mode] on or off. **The two mouse-tracking modes are mutually exclusive** - setting either
   * clears the other, which is the one piece of logic `setDecsetinternalBit` carries.
   */
  fun set(mode: DecMode, on: Boolean) {
    if (!on) {
      current.remove(mode)
      return
    }
    EXCLUSIVE_WITH[mode]?.let(current::remove)
    current.add(mode)
  }

  /**
   * `CSI ? Pm s`. **This records no value.** Upstream the arm is `mSavedDecSetFlags |= internalBit`
   * with no read of the current state, so the [recalled] that follows always answers true.
   * Transcribed rather than corrected into a save.
   */
  fun mark(mode: DecMode) {
    marked.add(mode)
  }

  /** `CSI ? Pm r`: what [mark] left, which is what the mode is about to be set to. */
  fun recalled(mode: DecMode): Boolean = mode in marked

  /** The state a freshly opened terminal is in: wrapping on, cursor visible, and both marked. */
  fun reset() {
    current.clear()
    current.add(DecMode.AUTOWRAP)
    current.add(DecMode.CURSOR_ENABLED)
    marked.clear()
    marked.addAll(current)
  }

  private companion object {
    val EXCLUSIVE_WITH = mapOf(
      DecMode.MOUSE_TRACKING_PRESS_RELEASE to DecMode.MOUSE_TRACKING_BUTTON_EVENT,
      DecMode.MOUSE_TRACKING_BUTTON_EVENT to DecMode.MOUSE_TRACKING_PRESS_RELEASE,
    )
  }
}
