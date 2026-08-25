package com.jupydroid.app.domain.model

/**
 * What the find box is asking for: the text, and the three options that change how it is read.
 *
 * The reference keeps these as four properties of `SearchDocumentModel` and turns all four into a
 * single `RegExp` in one place (`Private.parseQuery`,
 * `packages/documentsearch/src/searchmodel.ts:398-427`) - so **every search is a regular
 * expression**, and "Use Regular Expression" only decides whether the user wrote it or this did.
 *
 * `preserveCase` is deliberately not here: it changes what a *replacement* becomes, not what
 * matches, and the reference passes it through `IReplaceOptions` rather than through the query
 * (`searchmodel.ts:295-311`).
 *
 * **Here rather than in `ui/editor/`, where it was written, because it is a port's parameter
 * type** (P2x-1): `WorkspaceSearcher.search` takes one, and `domain` imports nothing project-local
 * outside `domain.model` - the same relationship [WorkspaceEntry] has to `WorkspaceRepository`.
 * Two surfaces read it now, the notebook's find bar and the workspace search, which is what makes
 * one home a rule rather than a preference (CLAUDE.md, "One home per kind of logic").
 *
 * ```
 * SearchQuery("cat", wholeWords = true).compile()   // CompiledQuery.Matching(/\bcat\b/im)
 * ```
 */
data class SearchQuery(
  val text: String,
  /** `documentsearch:` Match Case. Off when the box opens, as `_caseSensitive = false` is. */
  val caseSensitive: Boolean = false,
  /** Match Whole Word. */
  val wholeWords: Boolean = false,
  /** Use Regular Expression - whether [text] is a pattern or is the characters it is made of. */
  val useRegex: Boolean = false,
)

/** What a [SearchQuery] came to: something to look for, nothing to look for, or a broken pattern. */
sealed interface CompiledQuery {
  data class Matching(val regex: Regex) : CompiledQuery

  /**
   * There is nothing to search for - the box is blank, or the pattern matches the empty string.
   *
   * One state for both, which is the reference's own: `_updateSearch` builds `query` and calls
   * `endQuery()` whenever it is falsy, and `parseQuery` returns `null` for either reason
   * (`searchmodel.ts:334-352`).
   */
  data object Empty : CompiledQuery

  /** The pattern did not parse. Shown beside the box, never thrown - the model's `_parsingError`. */
  data class Invalid(val message: String) : CompiledQuery
}

/**
 * `Private.parseQuery`, rule for rule.
 *
 * - A query that is **not** a regex is escaped, so it matches the characters it is made of.
 * - Whole words wrap the result in `\b`, *after* the escaping - so `c.t` is bounded as the three
 *   characters it is rather than as the pattern it looks like.
 * - The flags are `gm` / `gim`; `g` is what `findAll` already means here, and `m` is on either way.
 * - **A pattern that matches the empty string finds nothing at all.** The reference says why in as
 *   many words: the search would freeze the tab, and its editor finds nothing for `/^/` anyway.
 *
 * A pattern that does not parse is [CompiledQuery.Invalid] rather than an exception, because a
 * regex being typed is unparseable for most of the time it is being typed.
 */
fun SearchQuery.compile(): CompiledQuery {
  if (text.isEmpty()) return CompiledQuery.Empty
  val pattern = escapedUnlessRegex().let { if (wholeWords) "\\b$it\\b" else it }
  return pattern.asSearchRegex(caseSensitive)
}

/**
 * What the engine made of the pattern [SearchQuery.compile] built - the `new RegExp(queryText,
 * flag)` half of `parseQuery`, and the two ways it can come to nothing.
 *
 * Its own function because the two halves answer different questions: above is what the options
 * mean, and this is what the regex engine will accept. detekt asked for the split by counting
 * returns, and the seam it landed on is the right one.
 */
private fun String.asSearchRegex(caseSensitive: Boolean): CompiledQuery {
  val options = buildSet {
    add(RegexOption.MULTILINE)
    if (!caseSensitive) add(RegexOption.IGNORE_CASE)
  }
  val regex =
    try {
      Regex(this, options)
    } catch (invalid: IllegalArgumentException) {
      return CompiledQuery.Invalid("cannot search for /$this/: ${invalid.message}")
    }
  return if (regex.matchesNothing(this)) CompiledQuery.Empty else CompiledQuery.Matching(regex)
}

/**
 * `ret.test('')` - whether this pattern can match no characters at all, and so would report a hit
 * at every position in every cell.
 *
 * **Probed without [RegexOption.MULTILINE], and that is a measurement rather than a preference.**
 * Java's multiline `^` refuses the empty subject where JavaScript's accepts it - measured on this
 * machine's JDK: `Pattern.compile("^", MULTILINE).matcher("").find()` is `false` while
 * `new RegExp('^', 'gim').test('')` is `true`. So the reference's probe read literally would let a
 * bare `^` through, and a zero-width hit on every line is exactly what its own comment says the
 * guard is for. Dropping the flag for the probe is what makes the two agree; `$` and every pattern
 * without an anchor already do, and the searching regex keeps the flag.
 */
private fun Regex.matchesNothing(pattern: String): Boolean =
  containsMatchIn("") || Regex(pattern, options - RegexOption.MULTILINE).containsMatchIn("")

/**
 * `queryString.replace(/[-[\]/{}()*+?.\\^$|]/g, '\\$&')` - the reference's own set, kept character
 * for character rather than swapped for `Regex.escape`.
 *
 * `Regex.escape` wraps the text in `\Q…\E`, which is equivalent for every input **except one that
 * contains `\E` itself**; the reference's escaping has no such seam, and matching its set exactly
 * is also what makes this readable against the line it came from.
 */
private fun SearchQuery.escapedUnlessRegex(): String =
  if (useRegex) text else text.map { if (it in ESCAPED) "\\$it" else "$it" }.joinToString("")

/** The character class in `parseQuery`'s `replace`, as the set it denotes. */
private val ESCAPED = "-[]/{}()*+?.\\^\$|".toSet()
