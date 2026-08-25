package com.jupydroid.app.domain.model

/**
 * What a workspace walk reports while it runs, and what it says when it is over.
 *
 * The reference's own split: `ISearchService.textSearch` streams `ISearchProgressItem`s to an
 * `onProgress` callback and *resolves* to a single `ISearchComplete`
 * (`services/search/common/search.ts:595-607`). One flow carries both here, because a Kotlin flow
 * is the stream **and** the thing that ends - there is no second return value to put a completion
 * in.
 *
 * ```
 * searcher.search(query, scope).collect { if (it is SearchProgress.Found) println(it.file.path) }
 * ```
 */
sealed interface SearchProgress {
  /** One file with something in it, as it is found - the reference's per-file progress item. */
  data class Found(val file: MatchedFile) : SearchProgress

  /**
   * The walk is over. Exactly one of these ends every search, and it is the last thing emitted.
   *
   * @property limitHit `ISearchComplete.limitHit` - **the results are a subset of all matches**,
   *   because the walk stopped at `search.maxResults`. Only the thing doing the walking can answer
   *   it: a caller counting its own results against the cap cannot tell a workspace that held
   *   exactly the cap from one that held ten times it, and the panel draws the reference's warning
   *   off this rather than off a guess (`searchView.ts:1897-1899`).
   */
  data class Complete(val limitHit: Boolean) : SearchProgress
}
