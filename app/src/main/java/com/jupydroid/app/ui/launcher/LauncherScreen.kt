package com.jupydroid.app.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.theme.FocusRingTextButton
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.theme.focusRing

/** What Maestro and Compose tests address - one tag per card, suffixed by its command. */
const val LAUNCHER_CARD_TAG = "launcher-card"

/** `--jp-jupyter-icon-color` (theme variables.css:444) verbatim - the Notebook letter in dark. */
private val NOTEBOOK_LETTER_DARK = Color(0xFFF37626)

/**
 * The same Jupyter orange shifted dark enough to clear WCAG's 3:1 large-text floor on the light
 * card: `#F37626` measures 2.71:1 there, this measures 3.22:1. Dark keeps the verbatim brand.
 */
private val NOTEBOOK_LETTER_LIGHT = Color(0xFFE2690B)

/** `base.css:158` - the letter a kernel-less Console card draws, copied from the console icon. */
private val CONSOLE_LETTER_COLOR = Color(0xFF0288D1)

/** `jp-Launcher-content`'s `width: 85%` - the centred column the cards and sections live in. */
private const val CONTENT_WIDTH = 0.85f

/** `--jp-private-launcher-card-size` = 7.692em at the launcher's 13px base - the card's square. */
private val CARD_SIZE = 100.dp

/** `--jp-private-launcher-card-icon-height` = 5.231em - the strip a card's glyph sits in. */
private val ICON_STRIP = 68.dp

/** `jp-LauncherCard`'s `border: 1px solid var(--jp-border-color2)`. */
private val CARD_BORDER = 1.dp

/** `jp-LauncherCard`'s resting `box-shadow: var(--jp-elevation-z2)`. */
private val CARD_ELEVATION = 2.dp

/** `jp-Launcher-sectionHeader`'s `margin-left: 5px` - tuned to align the title with the cards. */
private val SECTION_TITLE_INDENT = 5.dp

/** The glyph a non-kernel card draws inside [ICON_STRIP]. */
private val UTILITY_ICON = 40.dp

/**
 * The Launcher - `packages/launcher/src/widget.tsx`. A sectioned grid of cards over the document
 * surface; every geometry is the reference CSS's own: 7.692em card, 8px margin, 1px
 * `border-color2`, `layout-color0` fill, 5.231em icon area over a 2.462em label
 * (`base.css:6-15,60-177`). Kernel cards draw the reference's `noKernelIcon` fallback - the
 * label's first letter at 4em in the section's icon colour - which is what a kernelspec with no
 * icon_url is supposed to look like, and the user asked for the letter tile.
 */
@Composable
fun LauncherScreen(
  cwd: String,
  viewModel: LauncherViewModel,
  onLaunched: (LaunchedDocument) -> Unit,
  modifier: Modifier = Modifier,
) {
  val error by viewModel.error.collectAsStateWithLifecycle()
  Scaffold(
    // `testTagsAsResourceId` is what the cards' LAUNCHER_CARD_TAGs ride to a Maestro flow - the
    // same line TerminalPaneScreen sets for `console-input-field`'s own reachability.
    modifier = modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.testTag("launcher-screen"),
    containerColor = editorPalette.editorBackground,
    // No top bar at all: the tab above names it (`title.label` is the strip's, not a panel
    // row's) and leaving it is the strip's own act - ✕ where the launcher is closable
    // (`main.title.closable`, `launcher-extension/src/index.ts:126`), the arrow below two panes.
    // The strip already consumed the status-bar insets, so the content needs no spacer either.
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // `jp-Launcher-content`: width 85%, padding-top 1.231em, sides 2.462em.
      Column(
        modifier = Modifier.fillMaxWidth(CONTENT_WIDTH).padding(top = Spacing.lg),
      ) {
        CwdHeading(cwd)
        LauncherCategory.entries.forEach { category ->
          LauncherSection(category) { item ->
            LauncherCard(item) { viewModel.launch(item, cwd, onLaunched) }
          }
        }
      }
    }
  }
  error?.let { message ->
    // `showErrorMessage('Error', error)` - the reference reports a failed command in a dialog.
    AlertDialog(
      onDismissRequest = viewModel::dismissError,
      title = { Text("Error") },
      text = { Text(message) },
      confirmButton = { FocusRingTextButton(onClick = viewModel::dismissError) { Text("OK") } },
    )
  }
}

/**
 * `jp-Launcher-cwd h3` - font-size2, ui-font-color1, margin 1em 0. The reference draws the path
 * verbatim, so at the root this is an empty heading - no stray "/".
 *
 * An empty `h3` has no line box, so its two 1em margins collapse into one
 * (`jupyterlab@7f0e690680a7 packages/launcher/style/base.css:43-48`): at the root the heading is one
 * [Spacing.lg] of space, not a 20dp line between two of them
 * (`LauncherScreenTest.anEmptyCwdHeadingLeavesOneMarginRatherThanALineBetweenTwo`).
 */
@Composable
private fun CwdHeading(cwd: String) {
  if (cwd.isEmpty()) {
    Spacer(Modifier.height(Spacing.lg))
    return
  }
  Text(
    text = cwd,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(vertical = Spacing.lg),
  )
}

/** A `jp-Launcher-section`: title row with the `border-color2` rule under it, then the cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LauncherSection(category: LauncherCategory, card: @Composable FlowRowScope.(LauncherItem) -> Unit) {
  val items = LAUNCHER_ITEMS.filter { it.category == category }
  if (items.isEmpty()) return
  Column(Modifier.padding(bottom = Spacing.md)) {
    // `jp-Launcher-sectionHeader`: margin-left 5px, border-bottom border-color2, margin-bottom 8px.
    Row(
      modifier = Modifier.padding(start = SECTION_TITLE_INDENT, bottom = Spacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = category.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.md).weight(1f),
        color = MaterialTheme.colorScheme.outlineVariant,
      )
    }
    // `jp-Launcher-cardContainer`: a wrapping row of cards at 8px margins.
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
      verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
      items.forEach { card(it) }
    }
  }
}

/**
 * One `jp-LauncherCard`: 100dp wide and at least 100dp tall, a 1px edge, elevation z2. The icon area
 * is 68dp; the label below wraps at 12sp and is what the CSS calls `ui-font-color1`. The height is a
 * minimum, as the reference's `min-height` is (`jupyterlab@7f0e690680a7
 * packages/launcher/style/base.css:102-103`): a fixed square left the label 28dp, one line at 200% font
 * scale, so "Python 3" drew as "Pytho…" (`LauncherScreenTest.everyCardKeepsItsWholeLabelAtTwiceTheFontScale`).
 * The width grows to the label's longest word, so no word is broken, and the cards of a row stretch to
 * its tallest, as the reference's flex row does (`base.css:89-96` sets no `align-items`, so it stretches;
 * `LauncherScreenTest.cardsInOneRowAreAsTallAsTheTallestAtTwiceTheFontScale`).
 *
 * Two roles land on different tokens than the CSS names, both on the ramp's own convention:
 * the reference's `border-color2` is a decorative separator, but a card edge *bounds a
 * component*, which is the `outline` role WCAG 1.4.11 asks 3:1 of (3.5:1 on both cards here);
 * and its `layout-color0` fill is the same "content card on the document surface" job
 * `cellBackground` already does, which also keeps the card visible in dark where a z2 shadow
 * over the page colour cannot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.LauncherCard(item: LauncherItem, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    modifier = Modifier
      .fillMaxRowHeight()
      .widthIn(min = CARD_SIZE)
      .width(IntrinsicSize.Min)
      .heightIn(min = CARD_SIZE)
      .semantics { contentDescription = item.label }
      .testTag("$LAUNCHER_CARD_TAG-${item.command.name}")
      .focusRing(),
    shape = MaterialTheme.shapes.extraSmall,
    color = editorPalette.cellBackground,
    border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outline),
    shadowElevation = CARD_ELEVATION,
  ) {
    Column {
      // `jp-LauncherCard-icon`: the icon's 68dp strip, centred.
      Column(
        modifier = Modifier.fillMaxWidth().height(ICON_STRIP),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        LauncherCardIcon(item)
      }
      // `jp-LauncherCard-label`: padding 0 4px 4px, 12sp, centred, wraps up to two lines.
      Text(
        text = item.label,
        fontSize = 12.sp,
        lineHeight = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs).padding(bottom = Spacing.xs),
      )
    }
  }
}

/**
 * The card's glyph. Kernel cards draw `jp-LauncherCard-noKernelIcon` - `label[0].toUpperCase()`
 * at `--jp-private-launcher-large-icon-size` in the section colour (`base.css:145-159`) - and the
 * Other cards draw their command's own icon, as `LabIcon.resolveReact` does for a non-kernel item.
 *
 * The letter is sized in **dp**, not sp: it is a decoration inside a fixed 68dp strip, so a
 * font-scale setting must grow the label but not blow the glyph out of its tile. For the same
 * reason it carries no semantics - the card's label is what TalkBack announces.
 */
@Composable
private fun LauncherCardIcon(item: LauncherItem) {
  when (item.category) {
    LauncherCategory.NOTEBOOK -> LetterTile(item, notebookLetterColor())
    LauncherCategory.CONSOLE -> LetterTile(item, CONSOLE_LETTER_COLOR)
    LauncherCategory.OTHER -> Icon(
      imageVector = otherIcon(item.command),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(UTILITY_ICON),
    )
  }
}

/** The letter tile itself: first letter of the label, ~76% of the icon strip like the reference. */
@Composable
private fun LetterTile(item: LauncherItem, color: Color) {
  Text(
    text = item.label.first().uppercase(),
    fontSize = with(LocalDensity.current) { (ICON_STRIP * LETTER_FRACTION).toSp() },
    color = color,
    modifier = Modifier.clearAndSetSemantics {},
  )
}

/** `noKernelIcon` is 4em over the 5.231em icon strip (`variables.css:172`, `base.css:134`). */
private const val LETTER_FRACTION = 0.765f

/** The luminance split between the dark card (~0.01) and the light card (~0.96). */
private const val DARK_SURFACE_MAX_LUMINANCE = 0.5f

/** The Notebook letter's colour is theme-aware - see [NOTEBOOK_LETTER_LIGHT]'s note. */
@Composable
private fun notebookLetterColor(): Color {
  val darkCard = editorPalette.cellBackground.luminance() < DARK_SURFACE_MAX_LUMINANCE
  return if (darkCard) NOTEBOOK_LETTER_DARK else NOTEBOOK_LETTER_LIGHT
}

private fun otherIcon(command: LauncherCommand): ImageVector = when (command) {
  LauncherCommand.NEW_TERMINAL -> Icons.Filled.Terminal
  LauncherCommand.NEW_TEXT_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
  LauncherCommand.NEW_MARKDOWN_FILE -> Icons.AutoMirrored.Filled.Article
  LauncherCommand.NEW_PYTHON_FILE -> Icons.Filled.Code
  else -> Icons.AutoMirrored.Filled.InsertDriveFile
}
