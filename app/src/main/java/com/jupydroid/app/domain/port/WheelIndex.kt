package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.PackageReleases

/**
 * What a PyPI package publishes, release by release - the source of truth a miss is explained from,
 * because pip's own text says the same thing for a name that exists nowhere (ADR-0165).
 *
 * A port because the answer is a network read, and because the sentence a cell shows must be
 * testable without PyPI.
 *
 * ```
 * wheelIndex.releasesOf("muslonly")?.platformRefusal("muslonly", WheelTarget.KERNEL)
 * ```
 */
interface WheelIndex {
  /**
   * [packageName]'s releases, or null when PyPI has no such package or could not be asked - the
   * answer that says nothing, never an error the cell would have to carry.
   */
  suspend fun releasesOf(packageName: String): PackageReleases?

  companion object {
    /**
     * A package's whole index is a few megabytes at most - duckdb's 169 releases are about one - so
     * this is room to answer in rather than a limit anyone meets.
     */
    const val MAX_INDEX_BYTES: Int = 8 * 1024 * 1024
  }
}
