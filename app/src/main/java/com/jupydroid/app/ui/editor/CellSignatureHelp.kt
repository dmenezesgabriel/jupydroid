package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.jupydroid.app.domain.port.LanguageSignature
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.theme.FocusRingIconButton
import com.jupydroid.app.theme.LocalEditorPalette
import com.jupydroid.app.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** What the server answered about the call the caret is inside, and the code it answered for. */
data class CellSignatureHelp(val query: SignatureHelpQuery, val help: LanguageSignatureHelp)

/**
 * Asks [signatureHelpAt] about the call the caret is inside, and answers null the rest of the
 * time - VS Code's `parameterHintsModel`.
 *
 * `collectLatest` is the cancellation story the completions pipeline already runs: a new
 * keystroke cancels the debounce and any ask in flight, so a stale reply can never overwrite
 * a newer one. The [armed] flag is the model's `isTriggered` - pending *or* active - which is
 * what lets a `,` typed inside the debounce still answer after the space typed right after it.
 */
@Composable
fun rememberCellSignatureHelp(
  state: TextFieldState,
  focused: State<Boolean>,
  highlighter: SyntaxHighlighter,
  signatureHelpAt: suspend (code: String, cursorOffset: Int) -> LanguageSignatureHelp?,
): State<CellSignatureHelp?> {
  val signatureHelp = remember { mutableStateOf<CellSignatureHelp?>(null) }
  // rememberUpdatedState for the same reason rememberCellCompletions keeps it: a bound method
  // reference is a new object each recomposition and would restart the effect per keystroke.
  val askServer by rememberUpdatedState(signatureHelpAt)

  LaunchedEffect(state) {
    // null until a real change is seen - the first frame is the text that already was, and
    // `onTypeHint` never sees it as typed: focusing a caret into `join(|` must not arm.
    var previous: String? = null
    var armed = false
    snapshotFlow {
      if (focused.value) state.text.toString() to state.selection.end else null
    }.distinctUntilChanged().collectLatest { frame ->
      if (frame == null) {
        armed = false
        previous = null
        signatureHelp.value = null
        return@collectLatest
      }
      val (code, caret) = frame
      val typedChar = previous?.let { typedCharBetween(it, code, caret) }
      val query = signatureHelpQueryAt(
        code,
        caret,
        highlighter.spansOf(code),
        armed,
        typedChar,
      )
      previous = code
      if (query == null) {
        armed = false
        signatureHelp.value = null
        return@collectLatest
      }
      armed = true
      // `parameterHintsModel.DEFAULT_DELAY` - the measured 120ms between keystroke and ask.
      delay(ASK_DELAY_MILLIS)
      val help = askServer(query.code, query.cursorOffset)
      signatureHelp.value = help?.let { CellSignatureHelp(query, it) }
      // `doTrigger`'s own rule: an empty reply cancels the hints - the next char must re-arm.
      if (help == null) armed = false
    }
  }
  return signatureHelp
}

/**
 * The char a keystroke inserted at the caret, or null for a caret move, a deletion, or an
 * edit that landed elsewhere - what `onTypeHint` reads off the typed text. An auto-closed
 * `()` reports the `(`: the caret lands between the pair, and `(` is the keystroke that
 * opens almost every ask.
 */
internal fun typedCharBetween(before: String, after: String, caret: Int): Char? {
  if (after == before || caret !in 1..after.length) return null
  val prefix = before.commonPrefixWith(after).length
  val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
  return if (caret in prefix + 1..after.length - suffix) after[caret - 1] else null
}

/**
 * The `[start, end)` slice of the signature's label the active parameter owns - VS Code's
 * `getParameterLabelOffsets` verbatim: a wire `[start, end]` pair used as-is, else the first
 * `(\W|^)label(?=\W|$)` match, else no highlight.
 */
internal fun parameterLabelOffsets(signature: LanguageSignature, paramIndex: Int): Pair<Int, Int>? =
  signature.parameters.getOrNull(paramIndex)
    ?.let { parameter -> parameter.labelRange ?: regexOffsets(signature.label, parameter.label) }

/** `(\W|^)label(?=\W|$)` - the first boundary-delimited occurrence, `getParameterLabelOffsets`' own regex. */
private fun regexOffsets(signatureLabel: String, parameterLabel: String): Pair<Int, Int>? {
  val match = parameterLabel.takeIf { it.isNotEmpty() }
    ?.let { Regex("(\\W|^)${Regex.escape(it)}(?=\\W|$)").find(signatureLabel) }
  return match?.let {
    val end = it.range.last + 1
    (end - parameterLabel.length) to end
  }
}

/**
 * The signature card, drawn, VS Code's `parameterHintsWidget` with a
 * finger.
 *
 * Anchored **above** the caret first (the widget's own `[ABOVE, BELOW]` preference, which
 * also keeps it clear of the completion list below), non-focusable so it never steals a
 * keystroke from the field it describes, and scrollable where a docstring runs long - the
 * widget's own `.docs` shape.
 */
@Composable
internal fun SignatureHelpPopup(cellHelp: CellSignatureHelp, caret: IntRect) {
  val help = cellHelp.help
  val palette = LocalEditorPalette.current
  // `updateActiveSignature` changes which overload is shown locally - the arrows never re-ask.
  var activeSignature by remember(cellHelp) { mutableIntStateOf(help.activeSignature) }
  val signature = help.signatures.getOrElse(activeSignature) { help.signatures.first() }
  val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).getBottom(LocalDensity.current)
  Popup(popupPositionProvider = CompletionPopupPosition(caret, bottomInset, preferAbove = true)) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = HELP_ELEVATION,
      modifier = Modifier.semantics { contentDescription = SIGNATURE_HELP },
    ) {
      Row(modifier = Modifier.widthIn(max = HELP_MAX_WIDTH), verticalAlignment = Alignment.CenterVertically) {
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        ) {
          // `.signature .code` - the overload's own label, its active parameter lit.
          Text(
            text = signatureLabel(signature, help, palette.signatureActiveParam),
            style = codeTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
          )
          signature.documentation?.let { MarkupBody(it) }
          signature.parameters.getOrNull(signature.activeParameter ?: help.activeParameter)
            ?.documentation?.let { MarkupBody(it) }
        }
        if (help.signatures.size > 1) {
          SignatureControls(
            current = activeSignature,
            count = help.signatures.size,
            onPrevious = { activeSignature = floorMod(activeSignature - 1, help.signatures.size) },
            onNext = { activeSignature = floorMod(activeSignature + 1, help.signatures.size) },
          )
        }
      }
    }
  }
}

/** `.controls` - `previous`/`next` and the `n/N` overload count, shown only `.multiple`. */
@Composable
private fun SignatureControls(current: Int, count: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = Spacing.xs)) {
    FocusRingIconButton(
      onClick = onPrevious,
      modifier = Modifier
        .size(CONTROL_SIZE)
        .semantics { contentDescription = PREVIOUS_SIGNATURE },
    ) {
      Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
    }
    Text(
      text = "${current + 1}/$count",
      style = codeTextStyle(),
      modifier = Modifier.semantics { contentDescription = "$SIGNATURE_COUNT ${current + 1} of $count" },
    )
    FocusRingIconButton(
      onClick = onNext,
      modifier = Modifier
        .size(CONTROL_SIZE)
        .semantics { contentDescription = NEXT_SIGNATURE },
    ) {
      Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
  }
}

/** `parameterHints.cycle` defaults true - stepping past an end wraps rather than cancels. */
private fun floorMod(value: Int, mod: Int): Int = ((value % mod) + mod) % mod

/**
 * The label split the way `renderParameters` builds it - before, `label.substring(start, end)`
 * in `.parameter.active` (bold in the highlight colour), after.
 */
private fun signatureLabel(
  signature: LanguageSignature,
  help: LanguageSignatureHelp,
  activeColour: Color,
): AnnotatedString = buildAnnotatedString {
  val offsets = parameterLabelOffsets(signature, signature.activeParameter ?: help.activeParameter)
  if (offsets == null) {
    append(signature.label)
    return@buildAnnotatedString
  }
  append(signature.label.substring(0, offsets.first))
  withStyle(SpanStyle(color = activeColour, fontWeight = FontWeight.Bold)) {
    append(signature.label.substring(offsets.first, offsets.second))
  }
  append(signature.label.substring(offsets.second))
}

internal const val SIGNATURE_HELP = "Signature help"
internal const val PREVIOUS_SIGNATURE = "Previous signature"
internal const val NEXT_SIGNATURE = "Next signature"
internal const val SIGNATURE_COUNT = "Signature"

/** VS Code's measured `DEFAULT_DELAY` - 120ms between the keystroke and the ask. */
private const val ASK_DELAY_MILLIS = 120L

/** As wide as the completion and hover popups - see `CellCompletions.MAX_WIDTH`'s reasoning. */
private val HELP_MAX_WIDTH = 280.dp

/** `ElevationTokens.Level2` - the same step the hover card takes off the cell it floats over. */
private val HELP_ELEVATION = 3.dp

/** Roomy enough for a thumb, small enough for the `.controls` column's 22px own width. */
private val CONTROL_SIZE = 32.dp
