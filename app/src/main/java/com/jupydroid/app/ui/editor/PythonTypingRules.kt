package com.jupydroid.app.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.model.IndentUnit

/**
 * The three edits a Python cell is expected to make on its own: carry the indent onto a new line,
 * add a level after a line that opens a block, and close a bracket or quote as it is opened.
 *
 * Runs after every keystroke, on the buffer the field is about to show, so it can rewrite the
 * change or leave it alone - see [InputTransformation].
 *
 * ```
 * BasicTextField(state = state, inputTransformation = PythonTypingRules())
 * ```
 *
 * @param autoClosingBrackets `notebook:toggle-autoclosing-brackets`, universal - gates
 *   [closePair] and [stepOverPartner] only. [continueIndent] is a different CodeMirror setting
 *   there (`packages/codemirror/src/extension.ts:637-644` vs the block-indent behaviour bound to
 *   the language, never to this flag) and must not move with it: a cell that closes nothing must
 *   still carry a block's indent onto the next line.
 * @param indentUnit `indentUnit` - the level a `:` line adds (`:707-720`). Its own setting and
 *   not part of [autoClosingBrackets]'s: the schema's `editorConfig` keeps them apart too.
 */
@OptIn(ExperimentalFoundationApi::class)
class PythonTypingRules(
  private val autoClosingBrackets: Boolean = true,
  private val indentUnit: IndentUnit = IndentUnit.SPACES_4,
) : InputTransformation {
  override fun TextFieldBuffer.transformInput() {
    val at = soleTypedCharOffset() ?: return
    val typed = charAt(at)
    when {
      typed == '\n' -> continueIndent(at)
      !autoClosingBrackets -> Unit
      typed == charAfter(at) && typed in PARTNER_OF.values -> stepOverPartner(at)
      typed in PARTNER_OF && opensAPair(at, typed) -> closePair(at, PARTNER_OF.getValue(typed))
    }
  }

  /**
   * Where the user's single typed character landed, or null when the change was anything else.
   *
   * A paste, a replaced selection or an IME rewriting a whole word are all one "change" too, and
   * none of them is a keystroke these rules can reason about.
   */
  private fun TextFieldBuffer.soleTypedCharOffset(): Int? {
    if (changes.changeCount != 1) return null
    val inserted = changes.getRange(0)
    val oneKeystroke = changes.getOriginalRange(0).collapsed &&
      inserted.length == 1 &&
      selection == TextRange(inserted.end)
    return inserted.start.takeIf { oneKeystroke }
  }

  /** The character the typed one was pushed in front of, or null at the end of the cell. */
  private fun TextFieldBuffer.charAfter(at: Int): Char? = if (at + 1 < length) charAt(at + 1) else null

  private fun TextFieldBuffer.continueIndent(at: Int) {
    val broken = asCharSequence().substring(lineStartBefore(at), at)
    val indent = broken.takeWhile { it == ' ' || it == '\t' }
    val opened = if (broken.trimEnd().endsWith(':')) indentUnitFor(indent) else ""
    if (indent.isEmpty() && opened.isEmpty()) return
    insert(at + 1, indent + opened)
    placeCursorBeforeCharAt(at + 1 + indent.length + opened.length)
  }

  private fun TextFieldBuffer.lineStartBefore(at: Int): Int {
    var start = at
    while (start > 0 && charAt(start - 1) != '\n') start--
    return start
  }

  /**
   * A tab-indented line keeps its tabs whatever the setting says - a space unit under it is a
   * `TabError` - and every other line gets the configured unit (`indentUnit.of`, the schema's
   * own semantics: what an indent *is*, not a heuristic over the line).
   */
  private fun indentUnitFor(indent: String): String =
    if (indent.isNotEmpty() && indent.all { it == '\t' }) "\t" else indentUnit.text

  /** Drops the typed character and moves past the one already there, so `)` never doubles. */
  private fun TextFieldBuffer.stepOverPartner(at: Int) {
    delete(at, at + 1)
    placeCursorBeforeCharAt(at + 1)
  }

  private fun TextFieldBuffer.closePair(at: Int, partner: Char) {
    insert(at + 1, partner.toString())
    placeCursorBeforeCharAt(at + 1)
  }

  /**
   * Only where the partner would not be wedged into existing text: at the end of a line, before
   * whitespace, or in front of something that already closes.
   */
  private fun TextFieldBuffer.opensAPair(at: Int, typed: Char): Boolean {
    val next = charAfter(at)
    if (next != null && !next.isWhitespace() && next !in OPENS_IN_FRONT_OF) return false
    // Two quotes and then a third is a docstring being opened, not an empty string to close.
    return !(typed in QUOTES && at > 0 && charAt(at - 1) == typed)
  }

  private companion object {
    val QUOTES = setOf('\'', '"')

    /** A quote is its own partner, which is what makes step-over work for strings too. */
    val PARTNER_OF = mapOf('(' to ')', '[' to ']', '{' to '}', '\'' to '\'', '"' to '"')

    val OPENS_IN_FRONT_OF = setOf(')', ']', '}', ',', ':', ';')
  }
}
