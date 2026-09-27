package com.jupydroid.app.domain.model

/**
 * What one level of indentation **is** - a tab, or a count of spaces.
 *
 * JupyterLab's `indentUnit` setting, with the schema's own shape and literals
 * (`packages/codemirror/src/extension.ts:707-720`): `type: 'string'`, `default: '4'`,
 * `enum: ['Tab', '1', '2', '4', '8']`, which the reference turns into `indentUnit.of('\t')` or
 * `' '.repeat(value)`. [option] is that literal, which is also the label the settings row
 * shows; [text] is what gets *inserted* for one level.
 *
 * The schema's `tabSize` beside it (`:963-972`) is deliberately not modelled: it is the
 * *display* width of a `\t` character, and a Compose `TextFieldState` has no tab-stop to set -
 * a row for it would light up and change nothing. Its default of 4 does still answer the one
 * question a Tab unit leaves open on the wire - see `IndentUnit.formattingOptions`.
 */
enum class IndentUnit(val option: String, val text: String) {
  TAB("Tab", "\t"),
  SPACES_1("1", " "),
  SPACES_2("2", "  "),
  SPACES_4("4", "    "),
  SPACES_8("8", "        "),
  ;

  /** The level's width in spaces, or null when the unit is a tab - `insertSpaces` reads this. */
  val spaces: Int? get() = if (this == TAB) null else text.length
}
