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
