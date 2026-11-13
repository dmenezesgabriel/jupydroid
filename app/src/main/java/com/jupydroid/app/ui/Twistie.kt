package com.jupydroid.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.glyphBesideText

/**
 * The chevron that says whether a section is shut, and which way it opens.
 *
 * The reference registers exactly these two glyphs for it - `Codicon.chevronDown` while expanded,
 * `Codicon.chevronRight` while collapsed (`viewPane.ts:85-86`) - and draws them into the header's
 * own `twistiesContainer` (`:457`).
 *
 * **No content description.** The row that carries this is what a screen reader reads, and it
 * states the same fact as a `stateDescription`; a description here would have TalkBack say it
 * twice. That is the split `SearchResults`' own file header already made.
 *
 * It is sized from the label it leads, so it keeps its proportion to that label as the system's font size grows.
 *
 * ```
 * Twistie(collapsed = folded, labelSize = MaterialTheme.typography.bodyMedium.fontSize)
 * ```
 *
 * @param labelSize the font size of the text this chevron sits beside, as that text's style states it.
 */
@Composable
fun Twistie(collapsed: Boolean, labelSize: TextUnit, modifier: Modifier = Modifier) {
  Icon(
    // AutoMirrored on the collapsed arm and not on the open one, which is Material's own split
    // rather than an inconsistency: a chevron pointing *into* the row flips in a right-to-left
    // locale and one pointing down does not. Same rule the rail's own icons follow.
    imageVector = if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.size(LocalDensity.current.glyphBesideText(TWISTIE_SIZE, labelSize)),
  )
}

/** The chevron at the default font scale: Material's `Icon` default, which is what it drew before. */
private val TWISTIE_SIZE = 24.dp
