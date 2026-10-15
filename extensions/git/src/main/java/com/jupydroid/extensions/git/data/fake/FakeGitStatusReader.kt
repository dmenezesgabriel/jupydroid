package com.jupydroid.extensions.git.data.fake

import com.jupydroid.extensions.git.domain.GitStatusException
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.RepositoryStatus

/**
 * A workspace in which each of [repositories] is a repository on the branch it maps to, each of
 * [failures] makes git fail with its message, and every other directory is not a repository.
 *
 * What the panel reads where the Space has no server, so the panel still opens and says something
 * true: with nothing configured, nothing is a repository.
 *
 * ```
 * FakeGitStatusReader(repositories = mapOf("demo" to "master")).status("demo") // Repository("master")
 * ```
 */
class FakeGitStatusReader(
  private val repositories: Map<String, String> = emptyMap(),
  private val failures: Map<String, String> = emptyMap(),
) : GitStatusReader {
  override suspend fun status(path: String): RepositoryStatus {
    failures[path]?.let { throw GitStatusException(path, it) }
    return repositories[path]?.let { RepositoryStatus.Repository(it) }
      ?: RepositoryStatus.NotARepository("fatal: not a git repository (or any of the parent directories): .git")
  }
}
