package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.app.theme.Spacing

/** Named so a flow can tell a folded cell from the field or the outputs it replaced. */
internal const val COLLAPSED_SOURCE = "Collapsed code"
internal const val COLLAPSED_OUTPUTS = "Collapsed outputs"

/**
 * What a collapsed source or a collapsed set of outputs shows in place of itself: **one line of
 * what is folded**, then an ellipsis, and a tap anywhere on it expands again.
 *
 * All three are JupyterLab's, not invented - its placeholders carry a line of the hidden
 * content, an ellipsis icon beside it and `title: "Click to expand"`
 * (`packages/cells/src/widget.ts:626-632`, `packages/cells/src/placeholder.ts:72-105`). The
 * line is what makes a folded cell identifiable at all; a bare "collapsed" row would turn a
 * folded notebook into a list of identical stubs.
 *
 * **`minimumInteractiveComponentSize` is the adaptation, and it is the one thing here the
 * reference does not supply.** `.jp-Placeholder-content` is `padding: 4px 6px`
 * (`packages/cells/style/placeholder.css:26-34`), which is a mouse target; this row measured
 * **288x32dp** on device (`evidence/p2l-4-touch-targets/before/`), 16dp under Material's minimum.
 * Compose expands the *hit area* of any clickable out to 48dp on its own, which is why the
 * suite's own touch-target assertion was green against it - but that expansion loses to a
 * neighbour's direct hit
 * (`NodeCoordinator.outOfBoundsHit`), and this row sits in a `LazyColumn` between a cell header and
 * an editor, both of which take direct hits. Material draws the same distinction in
 * `minimumInteractiveComponentSize`'s own KDoc: *"This modifier is not needed for touch target
 * expansion to happen. It only affects layout, to make sure there is adequate space for touch
 * target expansion."* So the space is reserved rather than borrowed, with Material's own modifier
 * rather than a height this app picked - the same one `IconButton` applies to itself.
 */
@Composable
fun CollapsedCell(preview: String, description: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "Expand") { onExpand() }
      .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
      // Inside the background and the ripple, so both draw at the reserved height rather than
      // leaving a transparent band above and below a 32dp pill - see the KDoc.
      .minimumInteractiveComponentSize()
      .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
      .semantics { contentDescription = description },
  ) {
    Text(
      text = preview,
      style = codeTextStyle(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Text(
      text = "…",
      // The same style as the line beside it: the two sit on one baseline, and the reference's
      // own ellipsis is an icon in the same row rather than a smaller piece of text.
      style = codeTextStyle(),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = Spacing.sm),
    )
  }
}

/**
 * The line that names what is folded.
 *
 * JupyterLab's two placeholders disagree here - the input one takes `source.split('\n')[0]`
 * and the output one takes the first *non-empty* line (`widget.ts:630`, `:1283-1289`). The
 * non-empty rule is used for both, because taking line zero literally shows an empty
 * placeholder for a cell that opens with a blank line, which is the one case a preview is for.
 */
internal fun firstNonEmptyLine(text: String): String =
  text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

/**
 * A line of the first output that has one. An image or a rendered document has no string form,
 * and neither does JupyterLab's placeholder for it - `getOutputPlaceholderText` returns
 * undefined and the row is just the ellipsis.
 */
internal fun List<RenderedOutput>.collapsedPreview(): String =
  firstNotNullOfOrNull { (it as? RenderedOutput.Text)?.text?.takeIf { text -> text.isNotBlank() } }
    ?.let(::firstNonEmptyLine)
    .orEmpty()
