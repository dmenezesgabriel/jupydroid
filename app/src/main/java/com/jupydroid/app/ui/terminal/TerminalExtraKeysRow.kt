package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.ui.editor.symbolKeyStyle
import com.jupydroid.extension.api.FocusRingTextButton

/**
 * [TerminalExtraKeysRow]'s handle for an external driver, `SYMBOL_ROW_TAG`'s reason: Gboard carries
 * its own key with the accessible text "Tab", so a flow cannot find this row's keys by text alone.
 */
internal const val TERMINAL_EXTRA_KEYS_TAG = "terminal_extra_keys"

/**
 * The row a touchscreen terminal needs that a soft keyboard cannot produce on its own -
 * 3a, `SymbolRow`'s own shape (`Surface`/scrolling `Row`/`imePadding`/
 * `navigationBarsPadding`) generalised rather than rebuilt, talking to [TerminalPaneViewModel]
 * the way `SymbolRow` talks to `FocusedCellInput`.
 *
 * Ctrl and Alt **latch** rather than emit a byte themselves - Termux's own `SpecialButtonState`
 * (`extrakeys/SpecialButtonState.java`), reproduced as [TerminalModifierLatch]: a tap arms the
 * next keystroke typed on the *soft* keyboard (there is no hardware key here to hold down while
 * a letter is pressed), and a long-press locks it caps-lock style. Every other key is pre-encoded
 * through [terminalKeystroke] and sent raw via `onKey` - the exact bytes a hardware key of the
 * same name already sends, so this row adds no second encoding scheme. A latched
 * Ctrl/Alt does not modify these direct taps (there is no "Ctrl+Home" here) - only characters
 * typed on the soft keyboard go through [applyModifierLatches], `TerminalPaneViewModel.typeCharacters`'s
 * own job.
 *
 * Visible whenever [TerminalPaneScreen] is - unlike `SymbolRow`, gated on `CellMode.EDIT`, a
 * terminal has no separate view/edit mode to gate on. [TerminalPaneScreen] publishes it to the
 * window's key row, so at two panes it spans the window as Termux's bar spans the activity
 * (termux-app@3df69d1 `app/src/main/res/layout/activity_termux.xml`).
 *
 * ```
 * TerminalExtraKeysRow(
 *   latches = state.latches,
 *   applicationCursorKeys = { viewModel.emulator.isApplicationCursorKeys },
 *   onTapModifier = viewModel::tapModifier,
 *   onLongPressModifier = viewModel::longPressModifier,
 *   onKey = viewModel::type,
 * )
 * ```
 */
@Composable
internal fun TerminalExtraKeysRow(
  latches: TerminalModifierLatches,
  applicationCursorKeys: () -> Boolean,
  onTapModifier: (TerminalModifierKey) -> Unit,
  onLongPressModifier: (TerminalModifierKey) -> Unit,
  onKey: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag(TERMINAL_EXTRA_KEYS_TAG),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        // Scrolls rather than wraps or shrinks - `SymbolRow`'s own reason: every key stays in the
        // semantics tree, where a screen reader and a Maestro flow can both still find it.
        .horizontalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding(),
    ) {
      ExtraKey(label = "Esc") { onKey(sendableKey(Key.Escape, applicationCursorKeys())) }
      ExtraKey(label = "Tab") { onKey(sendableKey(Key.Tab, applicationCursorKeys())) }
      ModifierExtraKey(
        label = "Ctrl",
        latch = latches.ctrl,
        onTap = { onTapModifier(TerminalModifierKey.CTRL) },
        onLongPress = { onLongPressModifier(TerminalModifierKey.CTRL) },
      )
      ModifierExtraKey(
        label = "Alt",
        latch = latches.alt,
        onTap = { onTapModifier(TerminalModifierKey.ALT) },
        onLongPress = { onLongPressModifier(TerminalModifierKey.ALT) },
      )
      ARROW_KEYS.forEach { (label, key) ->
        ExtraKey(label = label) { onKey(sendableKey(key, applicationCursorKeys())) }
      }
      NAVIGATION_KEYS.forEach { (label, key) ->
        ExtraKey(label = label) { onKey(sendableKey(key, applicationCursorKeys())) }
      }
    }
  }
}

/** [terminalKeystroke] always encodes one of this row's own keys - `ctrlPressed`/`keyCode` are unused by them. */
private fun sendableKey(key: Key, applicationCursorKeys: Boolean): String =
  requireNotNull(terminalKeystroke(key, ctrlPressed = false, keyCode = 0, applicationCursorKeys)) {
    "TerminalExtraKeysRow's own $key must be one terminalKeystroke() encodes"
  }

private val ARROW_KEYS = listOf(
  "\u2190" to Key.DirectionLeft,
  "\u2191" to Key.DirectionUp,
  "\u2193" to Key.DirectionDown,
  "\u2192" to Key.DirectionRight,
)

private val NAVIGATION_KEYS =
  listOf("Home" to Key.MoveHome, "End" to Key.MoveEnd, "PgUp" to Key.PageUp, "PgDn" to Key.PageDown)

/** A plain tap, [SymbolRow]'s own `SymbolKey` - a `TextButton` for Material's 48dp minimum touch target. */
@Composable
private fun ExtraKey(label: String, onClick: () -> Unit) {
  FocusRingTextButton(onClick = onClick) {
    Text(text = label, style = symbolKeyStyle())
  }
}

/**
 * Ctrl/Alt: a tap and a long-press mean different things ([TerminalModifierLatch.tapped] versus
 * [TerminalModifierLatch.longPressed]), which `TextButton`'s own single `onClick` cannot express -
 * built on [Modifier.combinedClickable] directly rather than nesting a second clickable inside a
 * `TextButton`, which would fire both. `LocalMinimumInteractiveComponentSize` is `TextButton`'s
 * own floor - Material's 48dp, `Breadcrumb.kt`/`EditorTopBar.kt`'s own precedent for reading it
 * directly rather than restating the number - applied explicitly since this key does not get it
 * for free.
 *
 * Highlighted the moment it is armed - Termux's own `SpecialButtonState.setIsActive` recolours the
 * button on **[TerminalModifierLatch.isActive]** alone, not on [TerminalModifierLatch.isLocked]
 * separately, so a locked latch reads identically to a freshly tapped one; [describedAs] is what
 * tells a screen reader (and this sub-step's own test) the difference.
 *
 * The label carries the padding a `TextButton` key does ([ButtonDefaults.TextButtonContentPadding]):
 * the box only floors at the touch target, so once the label outgrows it - at the platform's largest
 * font - nothing else keeps "Ctrl" and "Alt" apart and they read as one word
 * (`TerminalExtraKeysRowTest.ctrlAndAltLabelsAreAKeyPaddingApartAtTwiceTheFontScale`).
 */
@Composable
private fun ModifierExtraKey(label: String, latch: TerminalModifierLatch, onTap: () -> Unit, onLongPress: () -> Unit) {
  val contentColor = if (latch.isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
  val minimumTouchTarget = LocalMinimumInteractiveComponentSize.current
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
      .combinedClickable(onClick = onTap, onLongClick = onLongPress)
      .semantics { contentDescription = describedAs(label, latch) },
  ) {
    Text(
      text = label,
      style = symbolKeyStyle(),
      color = contentColor,
      modifier = Modifier.padding(ButtonDefaults.TextButtonContentPadding),
    )
  }
}

private fun describedAs(label: String, latch: TerminalModifierLatch): String = when {
  latch.isLocked -> "$label, locked"
  latch.isActive -> "$label, active"
  else -> label
}
