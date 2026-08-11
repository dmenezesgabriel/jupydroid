package com.jupydroid.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene

/**
 * Two navigation entries drawn beside each other: the list on the left, what it opened on the
 * right.
 *
 * Both entries are still entries - this only decides where they are painted - so each keeps its
 * own `ViewModelStore` and saveable state, and popping the detail still disposes it. See
 * [TwoPaneSceneStrategy] for why that mattered enough to write.
 */
internal class TwoPaneScene<T : Any>(
  override val entries: List<NavEntry<T>>,
  override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
  /**
   * Both contentKeys, because the pair is the identity.
   *
   * `AnimatedContent` inside `NavDisplay` switches on this: keyed on the list alone, swapping the
   * open notebook would look like the same scene and the outgoing editor's content would be left
   * in a slot the incoming one reuses.
   */
  override val key: Any = entries.map { it.contentKey }

  override val content: @Composable () -> Unit = {
    Row(modifier = Modifier.fillMaxSize()) {
      // A fixed list pane rather than a weighted split: the list is one column of titles and
      // dates whatever the window does, while the notebook is the thing that wants the room -
      // and a proportional list would grow to a quarter of a 1600dp window for no reason.
      // 360dp is Material's list-pane preferred width.
      entries.first().Content(modifier = Modifier.width(LIST_PANE_WIDTH))
      VerticalDivider()
      entries.last().Content(modifier = Modifier.fillMaxWidth())
    }
  }

  /** Renders one entry, which needs a `@Composable` call and so cannot live in `content`'s body. */
  @Composable
  private fun NavEntry<T>.Content(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) { Content() }
  }

  private companion object {
    val LIST_PANE_WIDTH = 360.dp
  }
}
