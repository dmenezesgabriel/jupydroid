package com.jupydroid.app.ui.editor

/** What one gutter press resolves to - see [gutterPress] for the table. */
internal enum class GutterPress { TOGGLE_BREAKPOINT, EDIT_CONDITION }

/**
 * The resolution a gutter press runs through, in order:
 *
 * 1. an **armed** line held long is the condition editor;
 * 2. anything else toggles a breakpoint, as every gutter tap always has.
 *
 * The gutter is the breakpoint's alone, as VS Code's glyph margin is
 * (`breakpointEditorContribution.ts` at `vscode@3f3380c668d0`); a finding is read from its
 * squiggle, by [diagnosticTaps].
 */
internal fun gutterPress(armed: Boolean, held: Boolean): GutterPress =
  if (armed && held) GutterPress.EDIT_CONDITION else GutterPress.TOGGLE_BREAKPOINT
