package com.jupydroid.extensions.git.domain

/**
 * Reads `git status` for a directory of the entered Space's workspace.
 *
 * `OkHttpGitStatusReader` asks jupyterlab-git's handler in the Space; `FakeGitStatusReader` answers
 * from a map.
 *
 * ```
 * when (val status = reader.status("demo")) { is RepositoryStatus.Repository -> status.branch … }
 * ```
 */
interface GitStatusReader {
  /**
   * The status of [path], relative to the workspace root (`""`).
   *
   * @throws GitStatusException when git fails for any reason but [path] not being in a repository.
   */
  suspend fun status(path: String): RepositoryStatus
}

/** git failed on [path] for a reason other than it not being a repository, or its answer was unreadable. */
class GitStatusException(path: String, reason: String) : Exception("git status of '$path' failed: $reason")
