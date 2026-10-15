package com.jupydroid.extensions.git.domain

/** What `git status` says about a directory: a repository and where it stands, or not one at all. */
sealed interface RepositoryStatus {
  /**
   * Inside a repository. [branch] is the checked-out branch's name, as `git status -b` prints it, and
   * [changes] every file `git status` lists, untracked ones included, in git's order.
   */
  data class Repository(val branch: String, val changes: List<ChangedFile> = emptyList()) : RepositoryStatus

  /**
   * Outside every repository - an answer, not a failure, since a workspace root that is not a
   * repository is the ordinary case. [reason] is git's own words for it.
   */
  data class NotARepository(val reason: String) : RepositoryStatus
}
