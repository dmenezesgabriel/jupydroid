package com.jupydroid.app.ui.terminal

/**
 * Which latch a tap or long-press on [TerminalExtraKeysRow] addresses - Termux's own
 * `SpecialButton.CTRL`/`.ALT` (`extrakeys/SpecialButton.java`), narrowed to the two this app's
 * row carries; Shift/Fn are Termux's own optional extras, not part of this sub-step's key set.
 */
enum class TerminalModifierKey { CTRL, ALT }

/**
 * A latched modifier a tap or long-press sets from a touchscreen, where nothing can be *held*
 * down the way a hardware Ctrl key can - Termux's own `SpecialButtonState{isActive,isLocked}`
 * (`extrakeys/SpecialButtonState.java`), read here as an immutable value rather than a mutated
 * view-owning object, since this class has no `MaterialButton`s of its own to recolour.
 *
 * - [tapped] toggles [isActive]; toggling it *off* also clears [isLocked] - Termux's own
 *   `onAnyExtraKeyButtonClick`: `state.setIsActive(!state.isActive); if (!state.isActive)
 *   state.setIsLocked(false)`. A tap on a locked latch therefore unlocks and deactivates in one
 *   step, since `isActive` was already true.
 * - [longPressed] sets both together from the state *before* the change - Termux's own
 *   `SpecialButtonsLongHoldRunnable.run`: `setIsLocked(!isActive); setIsActive(!isActive)` -
 *   caps-lock style: pressing an inactive key locks it active, pressing an active (locked) one
 *   unlocks and deactivates it.
 * - [consume] is the one-shot read - Termux's own `readSpecialButton(button,
 *   autoSetInActive = true)`: it reports whether the latch was active, and clears [isActive]
 *   afterward unless [isLocked], so an unlocked tap modifies exactly the next keystroke.
 *
 * ```
 * val armed = TerminalModifierLatch().tapped() // Ctrl tapped once
 * val (ctrlWasActive, next) = armed.consume()  // the following keystroke reads it, then it clears
 * ```
 */
data class TerminalModifierLatch(val isActive: Boolean = false, val isLocked: Boolean = false) {
  fun tapped(): TerminalModifierLatch =
    if (isActive) copy(isActive = false, isLocked = false) else copy(isActive = true)

  fun longPressed(): TerminalModifierLatch = copy(isLocked = !isActive, isActive = !isActive)

  /** @return whether the latch was active for this keystroke, and the latch afterward. */
  fun consume(): Pair<Boolean, TerminalModifierLatch> =
    isActive to (if (isActive && !isLocked) copy(isActive = false) else this)
}

/** [TerminalPaneUiState]'s own pair - a terminal has exactly the two latches its row draws. */
data class TerminalModifierLatches(
  val ctrl: TerminalModifierLatch = TerminalModifierLatch(),
  val alt: TerminalModifierLatch = TerminalModifierLatch(),
)
