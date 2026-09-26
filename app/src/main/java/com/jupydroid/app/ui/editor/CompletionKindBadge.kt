package com.jupydroid.app.ui.editor

/**
 * The colour group a completion kind belongs to, after VS Code's `symbolIcon.*` registry
 * (`editor/contrib/symbolIcons/browser/symbolIcons.ts`): only three hues are registered -
 * callables purple, class-likes amber, members blue - and every other id inherits `foreground`,
 * which [PLAIN] carries so the row's own text colour does the work.
 */
enum class CompletionBadgeFamily { CALLABLE, CLASSLIKE, MEMBER, PLAIN }

/**
 * An LSP `CompletionItemKind` - **declaration order is the protocol's numbering** (Text=1 …
 * TypeParameter=25), so the wire's `kind` int indexes it directly with no map of literals.
 * [letter] is IntelliJ's completion-icon convention - a monogram badge, since VS Code's
 * `symbol-<name>` codicons have no Android font; [family] is the colour group above.
 */
private enum class CompletionItemKind(val letter: String, val family: CompletionBadgeFamily) {
  TEXT("a", CompletionBadgeFamily.PLAIN),
  METHOD("m", CompletionBadgeFamily.CALLABLE),
  FUNCTION("f", CompletionBadgeFamily.CALLABLE),
  CONSTRUCTOR("c", CompletionBadgeFamily.CALLABLE),
  FIELD("f", CompletionBadgeFamily.MEMBER),
  VARIABLE("v", CompletionBadgeFamily.MEMBER),
  CLASS("C", CompletionBadgeFamily.CLASSLIKE),
  INTERFACE("I", CompletionBadgeFamily.MEMBER),
  MODULE("M", CompletionBadgeFamily.PLAIN),
  PROPERTY("p", CompletionBadgeFamily.PLAIN),
  UNIT("u", CompletionBadgeFamily.PLAIN),
  VALUE("v", CompletionBadgeFamily.PLAIN),
  ENUM("E", CompletionBadgeFamily.CLASSLIKE),
  KEYWORD("k", CompletionBadgeFamily.PLAIN),
  SNIPPET("s", CompletionBadgeFamily.PLAIN),
  COLOR("c", CompletionBadgeFamily.PLAIN),
  FILE("F", CompletionBadgeFamily.PLAIN),
  REFERENCE("r", CompletionBadgeFamily.PLAIN),
  FOLDER("D", CompletionBadgeFamily.PLAIN),
  ENUM_MEMBER("e", CompletionBadgeFamily.MEMBER),
  CONSTANT("K", CompletionBadgeFamily.PLAIN),
  STRUCT("S", CompletionBadgeFamily.PLAIN),
  EVENT("e", CompletionBadgeFamily.CLASSLIKE),
  OPERATOR("o", CompletionBadgeFamily.PLAIN),
  TYPE_PARAMETER("T", CompletionBadgeFamily.PLAIN),
}

/** What a completion row leads with: a monogram [letter] in [family]'s `symbolIcon.*` colour. */
data class CompletionBadge(val letter: String, val family: CompletionBadgeFamily)

/**
 * The badge for an LSP `CompletionItemKind` number, or null when the number names no kind -
 * including a kernel answer's absent kind, which earns no badge because the kernel claimed none.
 */
fun completionBadge(kind: Int?): CompletionBadge? = kind
  ?.let { CompletionItemKind.entries.getOrNull(it - 1) }
  ?.let { CompletionBadge(it.letter, it.family) }
