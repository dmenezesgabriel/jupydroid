package com.jupydroid.app

import com.jupydroid.extensions.git.domain.GitStatusException
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.RepositoryStatus

/**
 * A git status reader whose first read fails and every later one finds a repository on [branch], as a
 * server that was down and came back.
 *
 * ```
 * GitExtension(readerFor = { FailsOnceGitStatusReader(branch = "master") })
 * ```
 */
class FailsOnceGitStatusReader(private val branch: String) : GitStatusReader {
  private var failed = false

  override suspend fun status(path: String): RepositoryStatus {
    if (failed) return RepositoryStatus.Repository(branch)
    failed = true
    throw GitStatusException(path, "connection refused")
  }
}
