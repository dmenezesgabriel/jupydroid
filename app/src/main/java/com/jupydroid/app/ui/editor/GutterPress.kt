package com.jupydroid.app.ui.editor

/** What one gutter press resolves to - see [gutterPress] for the table. */
internal enum class GutterPress { TOGGLE_BREAKPOINT, EDIT_CONDITION, SHOW_DIAGNOSTICS }

/**
 * The resolution a gutter press runs through, in order:
 *
 * 1. an **armed** line held long is the condition editor - never yielded,
 *    not even to a diagnostic mark drawn on the same line;
 * 2. a press on the **mark column** of a **diagnosed** line shows the detail - the square is
 *    the affordance, and VS Code's `markerHoverParticipant` answers the same content on hover
 *    (a hover mobile performs as a tap);
 * 3. anything else toggles a breakpoint, as every gutter tap always has.
 */
internal fun gutterPress(armed: Boolean, held: Boolean, onMark: Boolean, diagnosed: Boolean): GutterPress = when {
  armed && held -> GutterPress.EDIT_CONDITION
  onMark && diagnosed -> GutterPress.SHOW_DIAGNOSTICS
  else -> GutterPress.TOGGLE_BREAKPOINT
}
