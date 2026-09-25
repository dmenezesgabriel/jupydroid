package com.jupydroid.app.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.DocumentDismissAction

/** What Maestro and Compose tests address - one tag per card, suffixed by its command. */
const val LAUNCHER_CARD_TAG = "launcher-card"

/** `--jp-jupyter-icon-color` (theme variables.css:444) - the letter a kernel-less Notebook card draws. */
private val NOTEBOOK_LETTER_COLOR = Color(0xFFF37626)

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
  cwd: String,
  viewModel: LauncherViewModel,
  onLaunched: (LaunchedDocument) -> Unit,
  /**
   * How to leave, or `null` where there is nothing to leave to: the reference drops the close icon
   * when the launcher is the only main-area widget (`main.title.closable` is
   * `widgets('main').length > 1` - `launcher-extension/src/index.ts:126`). At one pane the arrow is
   * navigation, not a close, and the caller always passes one.
   */
  onBack: (() -> Unit)?,
  twoPane: Boolean,
  modifier: Modifier = Modifier,
) {
  val error by viewModel.error.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier.fillMaxSize().testTag("launcher-screen"),
    containerColor = editorPalette.editorBackground,
    topBar = {
      TopAppBar(
        title = { Text("Launcher") },
        navigationIcon = {
          onBack?.let { dismiss ->
            DocumentDismissAction(twoPane = twoPane, backDescription = "Back to files", onDismiss = dismiss)
          }
        },
      )
    },
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
        // `jp-Launcher-cwd h3` - font-size2, ui-font-color1, margin 1em 0. Empty cwd is the root.
        Text(
          text = cwd.ifEmpty { "/" },
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.lg),
        )
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
      confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
    )
  }
}

/** A `jp-Launcher-section`: title row with the `border-color2` rule under it, then the cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LauncherSection(category: LauncherCategory, card: @Composable (LauncherItem) -> Unit) {
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
 * One `jp-LauncherCard`: 100dp square, `layout-color0` fill on the document surface, 1px
 * `border-color2` edge, elevation z2. The icon area is 68dp; the label below wraps at 12sp and is
 * what the CSS calls `ui-font-color1`.
 */
@Composable
private fun LauncherCard(item: LauncherItem, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    modifier = Modifier
      .size(CARD_SIZE)
      .testTag("$LAUNCHER_CARD_TAG-${item.command.name}"),
    shape = MaterialTheme.shapes.extraSmall,
    color = editorPalette.editorBackground,
    border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 */
@Composable
private fun LauncherCardIcon(item: LauncherItem) {
  when (item.category) {
    LauncherCategory.NOTEBOOK ->
      Text(item.label.first().uppercase(), fontSize = 52.sp, color = NOTEBOOK_LETTER_COLOR)
    LauncherCategory.CONSOLE ->
      Text(item.label.first().uppercase(), fontSize = 52.sp, color = CONSOLE_LETTER_COLOR)
    LauncherCategory.OTHER -> Icon(
      imageVector = otherIcon(item.command),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(UTILITY_ICON),
    )
  }
}

private fun otherIcon(command: LauncherCommand): ImageVector = when (command) {
  LauncherCommand.NEW_TERMINAL -> Icons.Filled.Terminal
  LauncherCommand.NEW_TEXT_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
  LauncherCommand.NEW_MARKDOWN_FILE -> Icons.AutoMirrored.Filled.Article
  LauncherCommand.NEW_PYTHON_FILE -> Icons.Filled.Code
  else -> Icons.AutoMirrored.Filled.InsertDriveFile
}
