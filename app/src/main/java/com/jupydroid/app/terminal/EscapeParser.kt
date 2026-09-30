package com.jupydroid.app.terminal

/**
 * Where a sequence is, between its `ESC` and the byte that ends it.
 *
 * The reference's `ESC_*` constants (`TerminalEmulator.java` lines 45-89, termux-app `3df69d1d`,
 * Apache 2.0 - see [TerminalGrid]), of which only the states something routes to exist here: a
 * state nothing reaches is dead code, and the later sub-steps add their own.
 */
enum class EscapeState {
  /** No sequence in progress; bytes are text and C0 controls. */
  Ground,

  /** `ESC` has arrived and the byte after it says what kind of sequence this is. */
  Escape,

  /** `ESC #` - the byte after it selects a DEC private sequence, of which one is implemented. */
  EscapePound,

  /** `ESC (` - the byte after it designates the G0 graphic set. */
  DesignateG0,

  /** `ESC )` - the byte after it designates G1. */
  DesignateG1,

  /** `ESC [` - a control sequence, collecting its parameters until a final byte. */
  ControlSequence,

  /** `ESC [ ?` - a DEC private mode sequence, collecting its parameters until a final byte. */
  PrivateParameter,

  /** `ESC [ >` - the byte after it is DA2, the one sequence this parameter byte carries. */
  SecondaryAttributes,

  /** `ESC [ ? … $` - the byte after it is DECRQM, which asks what a mode is set to. */
  ModeQuery,

  /** A private parameter byte (`<`, `=`) this step does not implement. */
  UnsupportedParameterByte,

  /** `ESC [ ' ` - the byte after it is DECIC or DECDC, both of which move within the margins. */
  ColumnEdit,

  /** `ESC [ " ` - the byte after it is DECSCA, which sets the protected attribute. */
  ProtectionAttribute,

  /** `ESC [ $` - the byte after it names a rectangular operation. */
  RectangularArea,

  /** `ESC [ *` - the byte after it is DECSACE, which says how the attribute region is shaped. */
  AttributeChangeExtent,

  /** An intermediate byte (`!`, `"`, `$`, `*`, space) this step does not implement. */
  UnsupportedIntermediateByte,

  /** `ESC ]` - an Operating System Command, collecting a string until `BEL` or `ESC \\`. */
  OperatingSystemCommand,

  /** `ESC ] … ESC` - either the `\\` that ends the string, or two more bytes for it. */
  OperatingSystemCommandEscape,

  /**
   * `ESC P` - a Device Control String, collecting until a bare `\\` - **no `ESC` needed**, since
   * the reference drops one outright rather than looking ahead the way `OSC` does.
   */
  DeviceControlString,

  /**
   * `ESC _` - an Application Program Command. **Nothing is collected**: the reference's own
   * comment is "Eat APC sequences silently for now", so every byte is discarded and only the
   * lookahead below asks whether one of them ends the sequence.
   */
  ApplicationProgramCommand,

  /** `ESC _ ... ESC` - either the bare `\\` that ends the APC string, or more discarded data. */
  ApplicationProgramCommandEscape,
}

/**
 * The escape state machine: `ESC` starts a sequence, the bytes after it say which one, and a final
 * byte ends it. The `ESC` sequences are [EscapeDispatch]'s; of the control sequences, `ESC [ Pm m`
 * - Select Graphic Rendition - is acted on here and every other final byte is **swallowed whole**
 * rather than printed, until the sub-step that owns it arrives.
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
class EscapeParser(
  private val screen: TerminalScreen,
  private val reporting: TerminalReporting,
  title: WindowTitle,
  colorCommands: ColorCommands,
  clipboardCommand: ClipboardCommand,
  private val deviceControl: DeviceControlString,
) {
  private val parameters = EscapeParameters()
  private val strings = OperatingSystemCommand(title, colorCommands, clipboardCommand)
  private val escapes = EscapeDispatch(screen, strings, deviceControl)
  private val cursors = CursorAddressing(screen)
  private val edits = ScreenEditing(screen)
  private val switches = ModeSwitching(screen)
  private val margins = ScrollMargins(screen)
  private val selective = SelectiveErasing(screen)
  private val rectangles = RectangularAreas(screen)
  private val rectangleAttributes = RectangularAttributes(screen)

  var state: EscapeState = EscapeState.Ground
    private set

  /** Whether a sequence is in progress, i.e. whether [accept] would take the next code point. */
  val isParsing: Boolean get() = state != EscapeState.Ground

  /**
   * Whether a `BEL` would **end** the string in progress rather than ring, and an `ESC` would open
   * its terminator rather than start a new sequence. True in [EscapeState.OperatingSystemCommand]
   * and nowhere else - notably **not** in [EscapeState.OperatingSystemCommandEscape], where the
   * reference rings the bell and restarts on an `ESC`, because its guards read `== ESC_OSC`.
   */
  val isCollectingString: Boolean get() = state == EscapeState.OperatingSystemCommand

  /**
   * Whether an `ESC` inside the sequence in progress is **swallowed rather than starting a new
   * one** - true only in [EscapeState.DeviceControlString], where the reference's guard reads
   * `== ESC_P` and drops the byte outright, unlike [isCollectingString]'s `OSC` lookahead, which
   * re-buffers it. A guard of its own rather than folded into that one, so widening it cannot be
   * done by accident.
   */
  val isDeviceControlString: Boolean get() = state == EscapeState.DeviceControlString

  /**
   * Whether every byte - printable or control - is being discarded into an APC string rather than
   * acted on, true in both [EscapeState.ApplicationProgramCommand] and its escape lookahead. This
   * mirrors the reference's own early return **before** its C0 switch: "the APC string might be
   * arbitrary non-printable characters, so handle that early" - so [TerminalEmulator]'s C0
   * dispatch never sees a byte while this is true, unlike [isCollectingString] and
   * [isDeviceControlString], which only intercept `BEL`/`ESC`.
   */
  val isApplicationProgramCommand: Boolean
    get() = state == EscapeState.ApplicationProgramCommand || state == EscapeState.ApplicationProgramCommandEscape

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
      EscapeState.Escape -> escapes.introduce(codePoint)
      EscapeState.EscapePound -> escapes.applyPound(codePoint)
      EscapeState.DesignateG0 -> escapes.designateG0(codePoint)
      EscapeState.DesignateG1 -> escapes.designateG1(codePoint)
      EscapeState.ControlSequence -> inControlSequence(codePoint)
      EscapeState.PrivateParameter -> inPrivateParameter(codePoint)
      EscapeState.ColumnEdit, EscapeState.ProtectionAttribute, EscapeState.RectangularArea,
      EscapeState.AttributeChangeExtent, EscapeState.ModeQuery,
      -> actOnIntermediate(state, codePoint)
      EscapeState.SecondaryAttributes -> inSecondaryAttributes(codePoint)
      EscapeState.OperatingSystemCommand, EscapeState.OperatingSystemCommandEscape, EscapeState.DeviceControlString,
      EscapeState.ApplicationProgramCommand, EscapeState.ApplicationProgramCommandEscape,
      -> inCollectorState(state, codePoint)
      EscapeState.UnsupportedParameterByte, EscapeState.UnsupportedIntermediateByte -> afterUnsupportedByte(codePoint)
    }
    return true
  }

  /**
   * Parameters accumulate until a byte that is not one. `m` is applied; every other final byte is
   * swallowed with its parameters, and the private and intermediate bytes hand the rest of the
   * sequence to [afterUnsupportedByte] so that it is swallowed rather than printed.
   */
  private fun inControlSequence(codePoint: Int): EscapeState = when {
    parameters.accept(codePoint) -> EscapeState.ControlSequence
    codePoint == PRIVATE_MODE_BYTE -> EscapeState.PrivateParameter
    codePoint == SECONDARY_ATTRIBUTES_BYTE -> EscapeState.SecondaryAttributes
    codePoint in PRIVATE_PARAMETER_BYTES -> EscapeState.UnsupportedParameterByte
    codePoint == COLUMN_EDIT_BYTE -> EscapeState.ColumnEdit
    codePoint == PROTECTION_BYTE -> EscapeState.ProtectionAttribute
    codePoint == RECTANGLE_BYTE -> EscapeState.RectangularArea
    codePoint == ATTRIBUTE_EXTENT_BYTE -> EscapeState.AttributeChangeExtent
    codePoint in UNIMPLEMENTED_INTERMEDIATE_BYTES -> EscapeState.UnsupportedIntermediateByte
    else -> applyFinalByte(codePoint)
  }

  /**
   * The byte that ends a control sequence, offered to each applier that owns a group of them.
   * **What none of them claims is swallowed with its parameters**, which is the frame the appliers
   * fill in rather than a stub - a final byte the reference implements and this step does
   * not must not reach the screen as text.
   */
  private fun applyFinalByte(codePoint: Int): EscapeState {
    when {
      codePoint == SELECT_GRAPHIC_RENDITION ->
        screen.currentStyle = GraphicRendition.applied(parameters, screen.currentStyle)

      margins.apply(codePoint, parameters) -> Unit
      reporting.apply(codePoint, parameters) -> Unit
      cursors.apply(codePoint, parameters) -> Unit
      switches.applyAnsi(codePoint, parameters) -> Unit
      else -> edits.apply(codePoint, parameters)
    }
    return EscapeState.Ground
  }

  /**
   * `ESC [ ?` collects parameters like any other control sequence; its final byte goes to
   * [SelectiveErasing] for `J`/`K`, [TerminalReporting] for `n`, and [ModeSwitching] for the rest.
   * The `$` intermediate leads to DECRQM and every other one still ends the sequence.
   */
  private fun inPrivateParameter(codePoint: Int): EscapeState = when {
    parameters.accept(codePoint) -> EscapeState.PrivateParameter
    codePoint == MODE_QUERY_BYTE -> EscapeState.ModeQuery
    codePoint in INTERMEDIATE_BYTE_RANGE -> EscapeState.UnsupportedIntermediateByte
    selective.apply(codePoint, parameters) -> EscapeState.Ground
    reporting.applyPrivate(codePoint, parameters) -> EscapeState.Ground
    else -> {
      switches.applyPrivate(codePoint, parameters)
      EscapeState.Ground
    }
  }

  /**
   * `ESC [ >`, whose one final byte is DA2. **Parameters are collected and then ignored** - the
   * reference's `doCsiBiggerThan` falls through to `parseArg`, and its `c` arm reads none of them,
   * so `CSI > 0 c` and `CSI > c` answer alike. Its other arm, `m`, sets xterm resources nothing
   * here has, and is swallowed with everything else.
   *
   * **Not part of [actOnIntermediate]'s table**: this one goes on collecting, where those five do
   * not.
   */
  private fun inSecondaryAttributes(codePoint: Int): EscapeState = when {
    parameters.accept(codePoint) -> EscapeState.SecondaryAttributes
    else -> {
      if (codePoint == SECONDARY_ATTRIBUTES) reporting.replySecondaryAttributes()
      EscapeState.Ground
    }
  }

  /**
   * The five states an **intermediate byte** leads to. Each collects no further parameters, acts on
   * exactly one final byte and returns to the ground state; a byte none of them claims ends the
   * sequence, which is the reference's `unknownSequence` arm.
   *
   * One table rather than five wrappers, because that is what it is - the state machine is this
   * class's job and what each sequence *does* belongs to the appliers this dispatches to.
   */
  private fun actOnIntermediate(state: EscapeState, codePoint: Int): EscapeState {
    when (state) {
      // `'` - DECIC and DECDC.
      EscapeState.ColumnEdit -> margins.applyColumnEdit(codePoint, parameters)
      // `"` - DECSCA.
      EscapeState.ProtectionAttribute -> selective.applyProtection(codePoint, parameters)
      // `$` - the rectangles, attributes first since they share the `r` and `t` final bytes.
      EscapeState.RectangularArea ->
        if (!rectangleAttributes.apply(codePoint, parameters)) rectangles.apply(codePoint, parameters)
      // `*` - DECSACE, whose `2` means a rectangle and `0`/`1` a stream.
      EscapeState.AttributeChangeExtent -> applyAttributeChangeExtent(codePoint)
      // `?` … `$` - DECRQM.
      EscapeState.ModeQuery -> if (codePoint == MODE_QUERY) reporting.replyModeState(parameters)
      else -> Unit
    }
    return EscapeState.Ground
  }

  /** DECSACE. A parameter outside `0..2`, and any final byte but `x`, is an unknown sequence. */
  private fun applyAttributeChangeExtent(codePoint: Int) {
    val extent = parameters.at(0, default = 0, treatZeroAsDefault = false)
    if (codePoint == ATTRIBUTE_EXTENT && extent in STREAM_EXTENT..RECTANGLE_EXTENT) {
      screen.rectangularAttributeChange = extent == RECTANGLE_EXTENT
    }
  }

  /**
   * The five states that collect - or, for `APC`, discard - a string rather than parameters:
   * `OSC`, its escape lookahead, `DCS`, and `APC` with its own escape lookahead. **One arm in
   * [accept] for all five**, the way [actOnIntermediate] holds its five: `accept` sat at detekt's
   * twenty-line cap already, and the two `APC` states would have pushed it over on their own.
   *
   * `APC`'s half is `doApc`/`doApcEscape` transcribed: every byte is discarded, matching the
   * reference's own comment - "Eat APC sequences silently for now." Only the terminator is
   * examined - an `ESC` opens the lookahead, and only a bare `\\` immediately after ends the
   * sequence; anything else, including another `ESC` or `_`, is discarded and collecting resumes.
   */
  private fun inCollectorState(state: EscapeState, codePoint: Int): EscapeState = when (state) {
    EscapeState.OperatingSystemCommand -> strings.collect(codePoint)
    EscapeState.OperatingSystemCommandEscape -> strings.afterEscape(codePoint)
    EscapeState.DeviceControlString -> deviceControl.collect(codePoint)
    EscapeState.ApplicationProgramCommandEscape ->
      if (codePoint == APC_TERMINATOR) EscapeState.Ground else EscapeState.ApplicationProgramCommand
    else -> if (codePoint ==
      APC_ESCAPE
    ) {
      EscapeState.ApplicationProgramCommandEscape
    } else {
      EscapeState.ApplicationProgramCommand
    }
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
    const val SELECT_GRAPHIC_RENDITION = 'm'.code

    /** The lookahead byte an APC's terminator starts with, and the byte that completes it. */
    const val APC_ESCAPE = 0x1B
    const val APC_TERMINATOR = '\\'.code

    /** ECMA-48's parameter bytes, `0` through `?`. */
    val PARAMETER_BYTE_RANGE = 0x30..0x3F

    /** ECMA-48's intermediate bytes, space through `/`. */
    val INTERMEDIATE_BYTE_RANGE = 0x20..0x2F

    /** `?`, which introduces a DEC private mode - the one private parameter byte acted on here. */
    const val PRIVATE_MODE_BYTE = '?'.code

    /** `$` after a `?`, which introduces DECRQM, and the one final byte it takes. */
    const val MODE_QUERY_BYTE = '$'.code
    const val MODE_QUERY = 'p'.code

    /** `>`, which introduces DA2, and the one final byte it takes. */
    const val SECONDARY_ATTRIBUTES_BYTE = '>'.code
    const val SECONDARY_ATTRIBUTES = 'c'.code

    /** The private parameter bytes left over. `<` and `=` are unimplemented upstream as well. */
    val PRIVATE_PARAMETER_BYTES = '<'.code..'?'.code

    /** `'`, which introduces DECIC and DECDC - both margin arithmetic, so both land there. */
    const val COLUMN_EDIT_BYTE = '\''.code

    /** `"`, which introduces DECSCA - the protected attribute the selective erases read. */
    const val PROTECTION_BYTE = '"'.code

    /** `$`, which introduces the rectangular operations. */
    const val RECTANGLE_BYTE = '$'.code

    /** `*`, which introduces DECSACE, and the one final byte and three parameters it takes. */
    const val ATTRIBUTE_EXTENT_BYTE = '*'.code
    const val ATTRIBUTE_EXTENT = 'x'.code
    const val STREAM_EXTENT = 0
    const val RECTANGLE_EXTENT = 2

    /**
     * The intermediate bytes the reference routes to a state of its own, each for a sequence a
     * later sub-step implements: `!` DECSTR and space DECSCUSR. **Every other intermediate byte
     * ends the
     * sequence**, which is the reference's own `parseArg` fall-through rather than a rule of
     * ECMA-48 - transcribed as it stands, so the two agree byte for byte.
     */
    val UNIMPLEMENTED_INTERMEDIATE_BYTES =
      setOf('!'.code, ' '.code)
  }
}
