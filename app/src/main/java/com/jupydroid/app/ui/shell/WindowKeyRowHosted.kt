package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jupydroid.extension.api.LocalWindowKeyRowSlot
import com.jupydroid.extension.api.WindowKeyRowSlot

/**
 * Draws [content] with the published key row beneath it, across the hosted region's width.
 *
 * **The keyboard's inset is paid here, once, for everything [content] draws.** `imePadding`
 * consumes the inset for its descendants, so every screen's own `imePadding` below reads zero
 * rather than lifting a second time. Without it the row would sit above the keyboard while the
 * screens beside it still paid for the keyboard themselves, counting it twice.
 *
 * Only the part of the window that is typed into is hosted: the activity bar beside it
 * (`Navigation.kt`'s `Row`) is not, and stays the window's full height so its foot rows remain
 * anchored at the bottom when the keyboard opens - inside here they rode the inset ~600px up and
 * the rail's panel region shrank to a sliver.
 *
 * ```
 * WindowKeyRowHosted(modifier = Modifier.weight(1f)) { NavDisplay(backStack, …) }
 * ```
 */
@Composable
fun WindowKeyRowHosted(
  modifier: Modifier = Modifier,
  slot: WindowKeyRowSlot = remember { WindowKeyRowSlot() },
  content: @Composable () -> Unit,
) {
  Column(modifier = modifier.fillMaxSize().imePadding()) {
    Box(modifier = Modifier.weight(1f)) {
      CompositionLocalProvider(LocalWindowKeyRowSlot provides slot, content = content)
    }
    slot.row?.invoke()
  }
}
