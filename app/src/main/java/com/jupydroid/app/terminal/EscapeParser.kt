package com.jupydroid.app.terminal

/**
 * Where a sequence is, between its `ESC` and the byte that ends it.
 *
 * The reference's `ESC_*` constants (`TerminalEmulator.java` lines 45-89, termux-app `3df69d1d`,
 * Apache 2.0 - see [TerminalGrid]), of which only the states this step can actually reach exist
 * here: a state nothing routes to is dead code, and the later sub-steps add their own.
 */
enum class EscapeState {
  /** No sequence in progress; bytes are text and C0 controls. */
  Ground,

  /** `ESC` has arrived and the byte after it says what kind of sequence this is. */
  Escape,

  /** `ESC [` - a control sequence, collecting its parameters until a final byte. */
  ControlSequence,

  /** A private parameter byte (`<`, `=`, `>`, `?`) this step does not implement. */
  UnsupportedParameterByte,

  /** An intermediate byte (`!`, `"`, `'`, `$`, `*`, space) this step does not implement. */
  UnsupportedIntermediateByte,
}

/**
 * The escape state machine: `ESC` starts a sequence, the bytes after it say which one, and a final
 * byte ends it. At this step exactly one sequence is acted on - `ESC [ Pm m`, Select Graphic
 * Rendition - and every other one is **swallowed whole** rather than printed.
 *
 * Transcribed from `TerminalEmulator.processCodePoint`'s state dispatch, `doEsc`, `doCsi`,
 * `startEscapeSequence`, `continueSequence`, `finishSequence` and
 * `doCsiUnsupportedParameterOrIntermediateByte` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]).
 *
 * **A class of its own, where the reference keeps the machine on the emulator.** `TerminalEmulator`
 * is the ground state - bytes to code points to characters - and this is everything after an
 * `ESC`; fused, the reference's `processCodePoint` is a 348-line method, and the split is the one
 * JediTerm draws between `JediEmulator` and `JediTerminal`.
 *
 * ```
 * val parser = EscapeParser(screen)
 * parser.start()                                  // ESC arrived
 * "[31m".forEach { parser.accept(it.code) }       // red from here on
 * ```
 */
class EscapeParser(private val screen: TerminalScreen) {
  private val parameters = EscapeParameters()

  var state: EscapeState = EscapeState.Ground
    private set

  /** Whether a sequence is in progress, i.e. whether [accept] would take the next code point. */
  val isParsing: Boolean get() = state != EscapeState.Ground

  /** `ESC` arrived: begin a sequence, abandoning any already in progress. */
  fun start() {
    parameters.startSequence()
    state = EscapeState.Escape
  }

  /** Back to the ground state, whatever was in progress. CAN, SUB and a terminal reset all do this. */
  fun finishSequence() {
    state = EscapeState.Ground
  }

  /**
   * Take [codePoint] as part of the sequence in progress. Returns false when there is none, which
   * is the caller's cue to draw it.
   */
  fun accept(codePoint: Int): Boolean {
    state = when (state) {
      EscapeState.Ground -> return false
      EscapeState.Escape -> afterEscape(codePoint)
      EscapeState.ControlSequence -> inControlSequence(codePoint)
      EscapeState.UnsupportedParameterByte, EscapeState.UnsupportedIntermediateByte -> afterUnsupportedByte(codePoint)
    }
    return true
  }

  /**
   * **`[` is the only introducer this step knows**, and every other one ends the sequence taking
   * only itself - which is what the reference does with an introducer it does not implement, and
   * what P4f-3 replaces for `#`, `(`, `)`, `7`, `8`, `c`, `D`, `E`, `M`, `P`, `]` and `_`.
   */
  private fun afterEscape(codePoint: Int): EscapeState =
    if (codePoint == CONTROL_SEQUENCE_INTRODUCER) EscapeState.ControlSequence else EscapeState.Ground

  /**
   * Parameters accumulate until a byte that is not one. `m` is applied; every other final byte is
   * swallowed with its parameters, and the private and intermediate bytes hand the rest of the
   * sequence to [afterUnsupportedByte] so that it is swallowed rather than printed.
   */
  private fun inControlSequence(codePoint: Int): EscapeState = when {
    parameters.accept(codePoint) -> EscapeState.ControlSequence
    codePoint in PRIVATE_PARAMETER_BYTES -> EscapeState.UnsupportedParameterByte
    codePoint in UNIMPLEMENTED_INTERMEDIATE_BYTES -> EscapeState.UnsupportedIntermediateByte
    else -> applyFinalByte(codePoint)
  }

  /**
   * The byte that ends a control sequence. `m` is applied; **every other one is swallowed with its
   * parameters**, which is the frame P4f-3 onwards fill in rather than a stub - a final byte the
   * reference implements and this step does not must not reach the screen as text.
   */
  private fun applyFinalByte(codePoint: Int): EscapeState {
    if (codePoint == SELECT_GRAPHIC_RENDITION) {
      screen.currentStyle = GraphicRendition.applied(parameters, screen.currentStyle)
    }
    return EscapeState.Ground
  }

  /**
   * `doCsiUnsupportedParameterOrIntermediateByte`: parameter bytes may follow parameter bytes,
   * intermediate bytes may follow either, and a final byte ends the sequence. Anything else ends
   * it too, which upstream is the `unknownSequence` arm.
   */
  private fun afterUnsupportedByte(codePoint: Int): EscapeState = when {
    state == EscapeState.UnsupportedParameterByte && codePoint in PARAMETER_BYTE_RANGE -> state
    codePoint in INTERMEDIATE_BYTE_RANGE -> EscapeState.UnsupportedIntermediateByte
    else -> EscapeState.Ground
  }

  private companion object {
    const val CONTROL_SEQUENCE_INTRODUCER = '['.code
    const val SELECT_GRAPHIC_RENDITION = 'm'.code

    /** ECMA-48's parameter bytes, `0` through `?`. */
    val PARAMETER_BYTE_RANGE = 0x30..0x3F

    /** ECMA-48's intermediate bytes, space through `/`. */
    val INTERMEDIATE_BYTE_RANGE = 0x20..0x2F

    /**
     * The private parameter bytes. `?` and `>` get states of their own at P4f-4, where DECSET and
     * the secondary device attributes live; `<` and `=` are unimplemented upstream as well.
     */
    val PRIVATE_PARAMETER_BYTES = '<'.code..'?'.code

    /**
     * The intermediate bytes the reference routes to a state of its own, each for a sequence a
     * later sub-step implements: `!` DECSTR, `"` DECSCA, `'` DECIC/DECDC, `$` the rectangular
     * operations, `*` DECSACE and space DECSCUSR. **Every other intermediate byte ends the
     * sequence**, which is the reference's own `parseArg` fall-through rather than a rule of
     * ECMA-48 - transcribed as it stands, so the two agree byte for byte.
     */
    val UNIMPLEMENTED_INTERMEDIATE_BYTES =
      setOf('!'.code, '"'.code, '\''.code, '$'.code, '*'.code, ' '.code)
  }
}
