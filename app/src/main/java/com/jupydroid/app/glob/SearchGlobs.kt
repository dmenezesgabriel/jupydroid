package com.jupydroid.app.glob

/**
 * What the search's include and exclude boxes mean, and what is excluded before anyone types.
 *
 * `QueryBuilder.parseSearchPaths` (`queryBuilder.ts:376-418`) is the box, and the two registered
 * settings are the rest.
 */

/**
 * The patterns a comma-separated box denotes.
 *
 * Four steps, in the reference's order (`queryBuilder.ts:382-404`): split glob-aware on `,`, trim,
 * drop a trailing separator, read a leading `.` as an extension, and expand each one so it matches
 * at any depth.
 *
 * **The `searchPaths` half is not modelled.** The reference groups a segment that is an absolute
 * path or starts with `./` or `../` into search *roots* rather than patterns (`:377-380`); there
 * are none here, because every path is workspace-relative and `WorkspaceRoot` refuses anything
 * resolving outside the root. The reference's `rtrim(s, '\\')` goes with it, for the reason
 * [PathGlob] gives about separators.
 *
 * ```
 * globsFrom("*.py, reports")   // matches notes.py at any depth, and everything under any reports/
 * ```
 */
fun globsFrom(box: String): List<PathGlob> = splitGlobAware(box, ',')
  .map { it.trim().trimEnd('/') }
  // Before the expansion rather than only before the trim: a box holding just "/" trims to
  // nothing, and an empty pattern expands into one that matches the workspace root.
  .filter { it.isNotEmpty() }
  .map { if (it.startsWith(".")) "*$it" else it }
  .flatMap { atAnyDepth(it) }
  .map { PathGlob(it) }

/** `files.exclude`'s registered default (`files.contribution.ts:156-158`), verbatim. */
private val FILES_EXCLUDE_DEFAULT = listOf(
  "**/.git",
  "**/.svn",
  "**/.hg",
  "**/.DS_Store",
  "**/Thumbs.db",
)

/**
 * `search.exclude`'s registered default (`search.contribution.ts:109`), verbatim.
 *
 * Kept as three literals rather than pruned to the two that could exist in a notebook workspace:
 * the setting is what it is, and a Space's `pkgs/` can hold anything pip put there.
 */
private val SEARCH_EXCLUDE_DEFAULT = listOf(
  "**/node_modules",
  "**/bower_components",
  "**/*.code-search",
)

/**
 * What is excluded when nobody has said otherwise: `files.exclude`'s five and `search.exclude`'s
 * three.
 *
 * The union is the reference's own, stated in `search.exclude`'s description - *"Inherits all glob
 * patterns from the `files.exclude` setting"* (`search.contribution.ts:108`).
 *
 * **Each names a directory, not what is under it.** `'**` + `/node_modules': true` carries no
 * trailing globstar, and the subtree disappears because a matching *directory* is pruned - that is
 * ripgrep's behaviour, and what `DiskWorkspaceSearcher` reproduces by asking this of every
 * directory it reaches as well as of every file.
 *
 * The reference's *"Use Exclude Settings and Ignore Files"* toggle
 * (`userDisabledExcludesAndIgnoreFiles`) is not modelled: there is no settings screen to disable
 * yet, and P6c is where one arrives.
 */
val DEFAULT_EXCLUDED_GLOBS: List<PathGlob> = (FILES_EXCLUDE_DEFAULT + SEARCH_EXCLUDE_DEFAULT).map { PathGlob(it) }

/**
 * `expandGlobalGlob` (`queryBuilder.ts:668-675`) - a pattern names a thing and everything under
 * it, at any depth.
 *
 * The collapse is the reference's own `replace`: without it `**` would expand to a pattern with a
 * redundant globstar segment in it.
 */
private fun atAnyDepth(pattern: String): List<String> =
  listOf("**/$pattern/**", "**/$pattern").map { it.replace("**/**", "**") }

/**
 * Which paths a search reaches, given what the include and exclude boxes hold.
 *
 * `QueryBuilder.commonQuery` folds the two boxes and the two settings into one include/exclude
 * pair and hands it to the engine (`queryBuilder.ts:140-160`); this is that pair, asked one path at
 * a time, and it is what both `WorkspaceSearcher` implementations filter with so neither has a
 * copy of the rules.
 *
 * Two plain strings rather than the domain's `SearchScope`, so this package keeps knowing only
 * about globs - the adapter is what joins a domain type to a language.
 *
 * ```
 * SearchFilter(include = "*.py", exclude = "").searches("reports/notes.py")   // true
 * ```
 *
 * @param include the *"files to include"* box; blank means everything.
 * @param exclude the *"files to exclude"* box, which applies beside [DEFAULT_EXCLUDED_GLOBS] and
 *   never instead of them.
 */
class SearchFilter(include: String, exclude: String) {
  private val included = globsFrom(include)
  private val excluded = DEFAULT_EXCLUDED_GLOBS + globsFrom(exclude)

  /**
   * Whether the file at [path] is searched: not excluded, and named by the include when there is
   * one. Exclude wins, which is the order the reference applies them in.
   */
  fun searches(path: String): Boolean =
    !excluded.any { it.matches(path) } && (included.isEmpty() || included.any { it.matches(path) })

  /**
   * Whether the walk descends into the directory at [path] - **exclude only**.
   *
   * An include cannot prune: a `.py` can be any distance below a directory whose own name matches
   * nothing. An exclude can, and pruning is what makes a default like `node_modules` cover the
   * subtree it names rather than only the folder - ripgrep's own behaviour, since a `--glob !`
   * pattern that matches a directory drops everything under it.
   */
  fun descendsInto(path: String): Boolean = !excluded.any { it.matches(path) }
}
