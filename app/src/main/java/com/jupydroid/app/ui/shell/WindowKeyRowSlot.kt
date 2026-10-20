package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The window's one row of keys above the soft keyboard, and which screen's keys it is showing.
 *
 * The row is the window's, not the screen's: at two panes a row drawn in the notebook's own
 * bottom bar was only as wide as the notebook pane. Termux draws its extra keys as a sibling below
 * the terminal at the activity's full width (termux-app@3df69d1
 * `app/src/main/res/layout/activity_termux.xml`, `terminal_toolbar_view_pager`). The keys are still
 * the screen's to choose, because a cell wants `:` and a terminal wants Ctrl, so the screen
 * being typed into publishes its own row here.
 *
 * ```
 * val slot = WindowKeyRowSlot()
 * slot.publish(row)      // the cell takes the caret
 * slot.withdraw(row)     // it leaves; a row published since is kept
 * ```
 */
class WindowKeyRowSlot {
  /** What the window draws above the keyboard, or null when nothing being typed into wants a row. */
  var row by mutableStateOf<(@Composable () -> Unit)?>(null)
    private set

  /** Shows [row], replacing whichever row was showing. */
  fun publish(row: @Composable () -> Unit) {
    this.row = row
  }

  /**
   * Takes [row] away, and only [row]: when one document replaces another, the incoming screen can
   * publish before the outgoing one is disposed (`WindowKeyRowSlotTest`).
   */
  fun withdraw(row: @Composable () -> Unit) {
    if (this.row === row) this.row = null
  }
}

/** The slot [KeyRowPublished] publishes into; null where no [WindowKeyRowHosted] is above it. */
private val LocalWindowKeyRowSlot = staticCompositionLocalOf<WindowKeyRowSlot?> { null }

/**
 * Draws [content] with the published key row beneath it, across the window's width.
 *
 * **The keyboard's inset is paid here, once, for everything [content] draws.** `imePadding`
 * consumes the inset for its descendants, so every screen's own `imePadding` below reads zero
 * rather than lifting a second time. Without it the row would sit above the keyboard while the
 * screens beside it still paid for the keyboard themselves, counting it twice.
 *
 * ```
 * WindowKeyRowHosted { MainNavigation(appContainer, sharedNotebook, onImportConsumed) }
 * ```
 */
@Composable
fun WindowKeyRowHosted(slot: WindowKeyRowSlot = remember { WindowKeyRowSlot() }, content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxSize().imePadding()) {
    Box(modifier = Modifier.weight(1f)) {
      CompositionLocalProvider(LocalWindowKeyRowSlot provides slot, content = content)
    }
    slot.row?.invoke()
  }
}

/**
 * Puts [row] in the window's key row while this is composed, and takes it away when it leaves.
 *
 * With no [WindowKeyRowHosted] above it, as when a screen is composed alone in a test, [row] is
 * drawn here instead, so the screen keeps its keys.
 *
 * ```
 * EditorBottomBar.SYMBOLS -> KeyRowPublished { SymbolRow(input = cellInput) }
 * ```
 */
@Composable
fun KeyRowPublished(row: @Composable () -> Unit) {
  val slot = LocalWindowKeyRowSlot.current
  if (slot == null) {
    row()
    return
  }
  // The latest [row], so a recomposition that changes what the keys do reaches the drawn row
  // without withdrawing and publishing it again.
  val latest by rememberUpdatedState(row)
  DisposableEffect(slot) {
    val published: @Composable () -> Unit = { latest() }
    slot.publish(published)
    onDispose { slot.withdraw(published) }
  }
}
