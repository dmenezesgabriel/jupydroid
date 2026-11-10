package com.jupydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The narrowest phone the app is laid out for, where a large font scale leaves a row the least room. */
internal val PHONE_WIDTH: Dp = 320.dp

/**
 * Composes [content] at a user font [scale] while keeping the screen's own density, which is what the
 * system's "Font size" setting changes and a test's `fontScale` argument to `Density` stands in for.
 *
 * ```
 * compose.setContent { AtFontScale(2f) { SettingsScreen(...) } }
 * ```
 */
@Composable
internal fun AtFontScale(scale: Float, content: @Composable () -> Unit) {
  val density = LocalDensity.current
  CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = scale), content = content)
}

/** The layout a `Text` node measured, which is the only place a line break or an ellipsis can be read. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
  val layouts = mutableListOf<TextLayoutResult>()
  fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
  return layouts.single()
}

/** Whether any line of this node's text ends in an ellipsis. */
internal fun SemanticsNodeInteraction.isCut(): Boolean {
  val layout = textLayout()
  return (0 until layout.lineCount).any(layout::isLineEllipsized)
}

/**
 * Whether a line other than the last ends mid-word: a soft wrap at a space keeps the space on the
 * line it ends, so a line whose last character is not one was broken because a word did not fit.
 */
internal fun SemanticsNodeInteraction.breaksInsideAWord(): Boolean {
  val layout = textLayout()
  val text = layout.layoutInput.text.text
  return (0 until layout.lineCount - 1).any { text[layout.getLineEnd(it) - 1] != ' ' }
}
