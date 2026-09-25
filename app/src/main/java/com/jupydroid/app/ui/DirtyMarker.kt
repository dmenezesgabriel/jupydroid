package com.jupydroid.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The `•` a dirty document's title carries, drawn while the document has unsaved changes.
 *
 * **The reference's answer is a dot, on the document's own label.** A dirty widget wears
 * `jp-mod-dirty` (`docregistry/src/default.ts:542-544`), and on the tab that swaps the close icon
 * for the busy dot in the label's own colour (`ui-components/style/icons.css:268-278`); the warn
 * colour belongs to the *cell*-level marker, not this one (`notebook/style/base.css:110-118`).
 * Neither of this app's title bars is a Lumino tab strip, so the marker is a `•` drawn beside the
 * title - the same glyph a dirty cell's gutter typesets.
 *
 * The glyph is announced as [UNSAVED_CHANGES] rather than read as a bare bullet, which is the
 * string a screen reader should say for the state `jp-mod-dirty` encodes - and it is what a
 * Maestro flow or test can assert without colliding with a `•` a document's own content may draw.
 */
@Composable
fun DirtyMarker(modifier: Modifier = Modifier) {
  Text(
    text = "•",
    color = LocalContentColor.current,
    modifier = modifier.semantics { contentDescription = UNSAVED_CHANGES },
  )
}

/** What the marker announces and answers to - see [DirtyMarker]. */
const val UNSAVED_CHANGES = "Unsaved changes"
