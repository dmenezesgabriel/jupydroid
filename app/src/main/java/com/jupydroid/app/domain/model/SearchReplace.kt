package com.jupydroid.app.domain.model

/**
 * What one match becomes when it is replaced.
 *
 * Two steps, in the order both of the reference's call sites use them
 * (`packages/codemirror/src/searchprovider.ts:344-352` and `:412-422`):
 *
 * ```
 * const substitutedText = options?.regularExpression ? match.text.replace(query, newText) : newText;
 * const insertText = options?.preserveCase ? preserveCase(match.text, substitutedText) : substitutedText;
 * ```
 *
 * So a group reference works only under the regex option, and preserving case is applied to
 * whatever the substitution produced rather than to what was typed.
 *
 * Beside [SearchQuery] in `domain/model` and **public** since P2x-1, where it used to be
 * `internal` to `ui/editor`: `DiskWorkspaceSearcher` is what substitutes across a workspace, and
 * `data` may not import `ui` (`docs/architecture.md` §5). Re-deriving it there would be two
 * copies of the reference's two-step rule.
 *
 * ```
 * replacementFor("CAT", "dog", SearchQuery("cat"), preserveCase = true)   // "DOG"
 * ```
 */
fun replacementFor(matched: String, replaceText: String, query: SearchQuery, preserveCase: Boolean): String {
  val substituted = if (query.useRegex) matched.substituted(replaceText, query) else replaceText
  return if (preserveCase) substituted.inTheCaseOf(matched) else substituted
}

/**
 * `match.text.replace(this.query!, newText)`, with the one difference between the two languages
 * handled rather than inherited.
 *
 * **Kotlin's `Regex.replace` throws on a group reference that does not exist; JavaScript's
 * `String.prototype.replace` leaves it as the characters it is.** A replacement being typed names a
 * group that is not there yet for most of the time it is being typed, so a literal fallback is what
 * keeps the reference's behaviour rather than a crash. Same reasoning as
 * [CompiledQuery.Invalid]: half-typed input is the ordinary case, not the exceptional one.
 */
private fun String.substituted(replaceText: String, query: SearchQuery): String =
  when (val compiled = query.compile()) {
    is CompiledQuery.Matching -> runCatching { compiled.regex.replace(this, replaceText) }.getOrDefault(replaceText)
    else -> replaceText
  }

/**
 * `SearchProvider.preserveCase` (`packages/documentsearch/src/searchprovider.ts:176-198`), and the
 * third arm is the one a guess gets wrong: sentence case lowercases the **rest** of the word
 * (`first.toUpperCase() + suffix.join('').toLowerCase()`), so `Cat` -> `dOG` gives `Dog`.
 */
private fun String.inTheCaseOf(matched: String): String = when {
  matched == matched.uppercase() -> uppercase()
  matched == matched.lowercase() -> lowercase()
  matched == matched.sentenceCase() -> sentenceCase()
  else -> this
}

/** `toSentenceCase` - the first character up and everything after it down. */
private fun String.sentenceCase(): String = if (isEmpty()) this else first().uppercase() + drop(1).lowercase()
