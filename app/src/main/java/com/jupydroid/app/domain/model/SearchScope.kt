package com.jupydroid.app.domain.model

/**
 * Which files a workspace search reaches - the two glob boxes under the search widget's `…`.
 *
 * `searchWidget.ts:55-133` draws them as *"files to include"* and *"files to exclude"*, and
 * `QueryBuilder.commonQuery` folds them together with the two registered exclude settings before
 * anything is walked (`queryBuilder.ts:140-160`). Blank is the ordinary state of both.
 *
 * The glob language itself is `com.jupydroid.app.glob`; this is only what was typed, so the domain
 * keeps naming *what* is asked for and never *how* it is matched.
 *
 * ```
 * SearchScope(include = "*.py", exclude = "reports")
 * ```
 */
data class SearchScope(val include: String = "", val exclude: String = "")
