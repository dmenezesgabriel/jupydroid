package com.jupydroid.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.parentDirectory
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.Twistie
import com.jupydroid.app.ui.editor.codeTextStyle
import com.jupydroid.app.ui.editor.searchMatchStyles

/**
 * What the walk found, grouped by the file it was found in - the reference's result tree, two
 * levels deep (`FileMatchRenderer` and `MatchRenderer`, `searchResultsView.ts:293-480`).
 *
 * **Two levels and not the reference's three.** VS Code can group by folder as well
 * (`FolderMatchRenderer`), behind `search.defaultViewMode` and only for a multi-root workspace;
 * this app has one root and a 360dp panel, so a third level of indentation would spend the width
 * the code needs to show a match in.
 *
 * **A `LazyListScope` extension rather than a composable owning a list**, and that is a measurement
 * rather than a style: with the glob boxes open (P2x-3e) the panel's fixed chrome is about 290dp,
 * and a soft keyboard leaves less than that - so a tree in a list of its own was measured with
 * **zero height**, composed and correct and impossible to reach. The reference has no keyboard to
 * inset for and keeps its widgets fixed above a scrolling tree; here the whole panel scrolls as one
 * surface, which is what puts a result back within reach of a thumb.
 *
 * ```
 * LazyColumn { searchResults(results, collapsed = emptySet(), onToggleFile = {}, onOpenMatch = { _, _ -> }) }
 * ```
 *
 * @param collapsed the paths whose matches are folded away - see [SearchSidePanel] for its lifetime.
 */
fun LazyListScope.searchResults(
  results: List<MatchedFile>,
  collapsed: Set<String>,
  onToggleFile: (String) -> Unit,
  onOpenMatch: (MatchedFile, MatchedLine) -> Unit,
) {
  results.forEach { file ->
    item(key = file.path) {
      FileMatchHeader(file = file, folded = file.path in collapsed, onToggle = { onToggleFile(file.path) })
    }
    // `search.collapseResults` is `alwaysExpand` by default (`search.contribution.ts:215`), so a
    // file arrives open and the header is what folds it.
    if (file.path !in collapsed) {
      items(items = file.matches, key = { "${file.path}:${it.line}:${it.range.first}" }) { match ->
        MatchRow(match = match, onOpen = { onOpenMatch(file, match) })
      }
    }
  }
}

/**
 * One file with something in it: a chevron, its name, where it lives, and how many matches
 * (`FileMatchRenderer.renderElement` - a `ResourceLabel` plus a `CountBadge`).
 *
 * The folder is drawn under the name rather than beside it, which is the call `EntryRow` already
 * made for `last_modified`: at 360dp there is no second column, and the reference itself narrows
 * as the space shrinks.
 */
@Composable
private fun FileMatchHeader(file: MatchedFile, folded: Boolean, onToggle: () -> Unit) {
  val folder = file.path.parentDirectory()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // Material's minimum, **on the node that carries the semantics** - see [MINIMUM_TAP_TARGET].
      .heightIn(min = MINIMUM_TAP_TARGET)
      // One node for the whole row, so a screen reader says "notes.py, reports, 3 matches found"
      // rather than walking three - and so a flow has one string to address the header by.
      .semantics(mergeDescendants = true) { contentDescription = fileMatchLabel(file.path, file.matches.size) }
      .clickable(onClick = onToggle)
      .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Twistie(collapsed = folded)
    Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
      Text(
        text = notebookLeafOf(file.path),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (folder.isNotEmpty()) {
        Text(
          text = folder,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    // `CountBadge` - the number of matches in this file, which is what tells a one-hit file from a
    // hundred-hit one without opening it.
    Text(
      text = "${file.matches.size}",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = Spacing.sm),
    )
  }
}

/**
 * One match: its line, with the match itself washed in the editor's own search colour.
 *
 * **One line, ellipsised, and no line number** - `search.showLineNumbers` is **false** by default
 * (`search.contribution.ts:223-226`). The number is still what a screen reader and a flow get, on
 * the row's own label, which is where the reference puts it too (`getMatchTitle`).
 *
 * The type is [codeTextStyle], because a result row shows source and what code looks like has one
 * home (`scripts/check_monospace_home.py`).
 *
 * **48dp reserved, and a result tree is exactly the shape that needs it.** One line of
 * `codeTextStyle` is 18sp, so this row measured **40dp** before [MINIMUM_TAP_TARGET] - and while
 * Compose expands any clickable's hit area to 48dp for free, that slop is the part two neighbours
 * split: a file header sits directly above every match row and both take direct hits, so
 * `NodeCoordinator`'s *"the node with direct hit will get the event"* decides the overlap.
 * `ui/MinimumTouchTarget.kt` carries the whole account; the reference's own 22px row is a mouse
 * target and not a thumb's.
 */
@Composable
private fun MatchRow(match: MatchedLine, onOpen: () -> Unit) {
  // A `Box` around the line rather than a taller `Text`: the height has to be *reserved* on the
  // node the tap lands on, and the text still has to sit in the middle of what it reserves.
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MINIMUM_TAP_TARGET)
      .clickable(onClick = onOpen)
      .semantics { contentDescription = matchRowLabel(match) }
      .padding(start = Spacing.xl, end = Spacing.md),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = previewOf(match).annotated(),
      style = codeTextStyle(),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** The row's three parts as one string, with the match wearing `editorPalette.searchMatch`. */
@Composable
private fun MatchPreview.annotated(): AnnotatedString {
  val styles = searchMatchStyles()
  return buildAnnotatedString {
    append(before)
    withStyle(styles.match) { append(match) }
    append(after)
  }
}

/**
 * Material's minimum interactive size, the value `minimumInteractiveComponentSize` enforces.
 *
 * **Written as a height rather than taken from that modifier, and that is a measurement.** The
 * modifier reserves its 48dp on a layout node *outside* the one the semantics land on, so the row
 * still measured 40dp where a test - and a neighbouring row competing for the overlap - can see it
 * (`ui/MinimumTouchTarget.kt` records the same reading against an `IconButton`). Constraining the
 * node that carries the tap is what actually reserves the space.
 */
internal val MINIMUM_TAP_TARGET: Dp = 48.dp

/**
 * What a file's header answers to - its path and the reference's own count wording,
 * *"{0} matches found"* / *"{0} match found"* (`searchResultsView.ts:358-359`).
 *
 * The **path** rather than the name, because two files called `notes.py` in two folders would
 * otherwise be one string to a flow and to a screen reader alike.
 */
internal fun fileMatchLabel(path: String, matches: Int): String =
  "$path, " + if (matches == 1) "1 match found" else "$matches matches found"

/**
 * What a match row answers to - `getMatchTitle`, which names the line the match is on, **and the
 * cell it is in where there is one**.
 *
 * The number is here and not drawn, which is the reference's own default: `search.showLineNumbers`
 * is false, and a 360dp panel has better uses for the width than a gutter. That default is also
 * what makes the cell necessary rather than decorative: in a notebook, "line 1" belongs to as many
 * lines as the file has cells, so without the cell two rows a tap apart can answer to one string -
 * which is the thing `docs/DECISIONS.md` refuses of two controls and refuses of two rows for the
 * same reason.
 *
 * **In the label rather than as a level of the tree**, which is the reference's own presentation:
 * `searchResultsView.ts` has no notebook branch at all and the cell rides on `MatchInNotebook`'s
 * id (`notebookSearchModel.ts:35`). A third level would spend indentation on chrome that the code
 * needs, which is the call P2x-3 already made against `FolderMatchRenderer`'s folder level.
 *
 * **One-based here where [MatchedLine.cell] is zero-based**, so it reads beside the line number it
 * is spoken with - the reference's own `rawCell#${index}` is an id and never a sentence.
 */
internal fun matchRowLabel(match: MatchedLine): String =
  match.cell?.let { "Cell ${it + 1}, line ${match.line}, ${match.text.trim()}" }
    ?: "Line ${match.line}, ${match.text.trim()}"
