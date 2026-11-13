package com.jupydroid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.glyphBesideText

/**
 * An ancestry, scrolling sideways rather than eliding, with every segment a target.
 *
 * Two surfaces draw one of these - the file browser, standing in a directory, and the
 * open document, saying which folder it lives in - so it is one composable rather than two that
 * would drift. What differs between them is where it sits and whether it collapses, which is the
 * caller's [modifier], not this.
 *
 * **Sideways rather than elided**, where the reference elides the middle behind an ellipsis
 * (`packages/filebrowser/src/crumbs.ts`, `_measureAllItemWidths`). Horizontal inside a vertical
 * list is the one nesting Compose allows without a fixed size - the guidance forbids only
 * *same-direction* nesting - and it is what keeps the last crumb, the one that matters, on screen.
 *
 * ```
 * Breadcrumb(crumbs = state.crumbs, onGoTo = viewModel::goTo)
 * ```
 *
 * @param crumbs each ancestor as `path to label`, root first - [crumbsFor] or [ancestorCrumbsFor].
 * @param onGoTo given the tapped segment's path; `""` is the workspace root, not "no target".
 */
@Composable
internal fun Breadcrumb(crumbs: List<Pair<String, String>>, onGoTo: (String) -> Unit, modifier: Modifier = Modifier) {
  val separatorSize = LocalDensity.current.glyphBesideText(
    CRUMB_SEPARATOR_SIZE,
    MaterialTheme.typography.labelLarge.fontSize,
  )
  Row(
    // **The row states its height and the crumbs no longer pad to it.** Intrinsic height made the
    // row 52dp - a 20dp label inside 16dp of padding - which is 4dp past the minimum it was
    // reaching for and, worse, a number `EditorScreen` cannot state when it works out how far the
    // collapsing region has to slide (`collapsibleRegionFraction`). Material's interactive minimum is
    // that number.
    modifier = modifier
      .fillMaxWidth()
      .height(LocalMinimumInteractiveComponentSize.current)
      .horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    crumbs.forEachIndexed { at, (path, label) ->
      if (at > 0) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(separatorSize).testTag(CRUMB_SEPARATOR_TAG),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        // Horizontal only: the row above gives the target its height, and padding it vertically as
        // well would measure 52dp inside a 48dp row and clip the label it is there to space.
        modifier = Modifier.clickable { onGoTo(path) }.padding(horizontal = Spacing.md),
      )
    }
  }
}

/**
 * The chevron between two breadcrumbs, at the default font scale. A size and not a gap, and named as one:
 * `SEPARATOR` said what it was beside rather than what it is, and `Modifier.size(SEPARATOR)` read as a spacer.
 * It grows with the crumb's label from there (`glyphBesideText`).
 */
private val CRUMB_SEPARATOR_SIZE = 16.dp

internal const val CRUMB_SEPARATOR_TAG = "Crumb separator"
