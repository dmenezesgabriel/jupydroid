package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageMarkup
import com.jupydroid.app.domain.port.LanguageMarkupKind
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.theme.Spacing

/**
 * What "Symbol info" asked for, shown.
 *
 * VS Code's `editor.contrib.hover` widget rendered with a finger: the server's markdown -
 * jedi sends a fenced signature block, then the docstring - through the same
 * `markdown/` → [RichText] path a markdown cell already draws, never a WebView and never a
 * raw string. Anchored on the caret like the completion popup and dismissed like every popup.
 *
 * ```
 * HoverPopup(hover = hover, caret = caretRect, onDismiss = { hover = null })
 * ```
 */
@Composable
internal fun HoverPopup(hover: LanguageHover, caret: IntRect, onDismiss: () -> Unit) {
  val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).getBottom(LocalDensity.current)
  // `focusable = true` is what makes outside taps reach `onDismissRequest` - the same finding
  // `DiagnosticDetailPopup` records, found live on the tablet (`DiagnosticMarkTapTest`).
  Popup(
    popupPositionProvider = CompletionPopupPosition(caret, bottomInset),
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true),
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = HOVER_ELEVATION,
      modifier = Modifier.semantics { contentDescription = HOVER_DESCRIPTION },
    ) {
      // A long docstring scrolls inside its card rather than filling the screen - VS Code's
      // own hover is a scrollable region for the same reason.
      Column(
        modifier = Modifier
          .widthIn(max = HOVER_MAX_WIDTH)
          .verticalScroll(rememberScrollState())
          .padding(Spacing.md),
      ) {
        MarkupBody(hover.contents)
      }
    }
  }
}

/**
 * A `LanguageMarkup` payload, drawn: markdown through the document pipeline, plaintext
 * verbatim. Internal rather than private because the signature-help card renders the same
 * `documentation` markup under its signature.
 */
@Composable
internal fun MarkupBody(markup: LanguageMarkup) {
  when (markup.kind) {
    LanguageMarkupKind.MARKDOWN -> {
      val blocks = remember(markup.value) { MarkdownDocument.parse(markup.value) }
      RichText(blocks)
    }
    LanguageMarkupKind.PLAINTEXT -> Text(
      text = markup.value,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** The marker every flow asks for - "the symbol-info popup is open", said accessibly. */
internal const val HOVER_DESCRIPTION = "Symbol info"

/** As wide as the completion popup and no wider - see `CellCompletions.MAX_WIDTH`'s reasoning. */
private val HOVER_MAX_WIDTH = 280.dp

/** `ElevationTokens.Level2` - the same step the diagnostic card takes off the cell it floats over. */
private val HOVER_ELEVATION = 3.dp
