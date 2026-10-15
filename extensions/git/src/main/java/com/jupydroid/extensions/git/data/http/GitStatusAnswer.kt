package com.jupydroid.extensions.git.data.http

import com.jupydroid.extensions.git.domain.GitStatusException
import com.jupydroid.extensions.git.domain.RepositoryStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The body `POST /git/<path>/status` answers: `{"code": 0, "branch": …}` inside a repository, and
 * `{"code": <git's exit code>, "message": <git's stderr>}` otherwise
 * (jupyterlab-git@b62a87b7 `packages/core/jupyterlab_git_core/git.py`, `status`).
 */
@Serializable
internal data class GitStatusAnswer(val code: Int, val branch: String? = null, val message: String? = null) {
  companion object {
    private val lenient = Json { ignoreUnknownKeys = true }

    /** Enough of an unexpected body to recognise it by, and not a page pasted into a panel. */
    private const val BODY_EXCERPT = 200

    /**
     * Not being a repository is told apart by git's own words, which is the reference's own test:
     * `show_top_level` looks for `"fatal: not a git repository"` in the lowered stderr
     * (jupyterlab-git@b62a87b7 `packages/core/jupyterlab_git_core/git.py`). The code cannot say it:
     * 128 is every fatal error git has.
     *
     * ```
     * GitStatusAnswer.parse("demo", """{"code": 0, "branch": "master"}""") // Repository("master")
     * ```
     */
    fun parse(path: String, body: String): RepositoryStatus {
      val answer = runCatching { lenient.decodeFromString<GitStatusAnswer>(body) }.getOrElse {
        throw GitStatusException(path, "the server did not answer a status: ${body.take(BODY_EXCERPT)}")
      }
      val reason = answer.message.orEmpty().trim()
      if (answer.code == 0 && answer.branch != null) return RepositoryStatus.Repository(answer.branch)
      if (NOT_A_REPOSITORY in reason.lowercase()) return RepositoryStatus.NotARepository(reason)
      throw GitStatusException(path, "code ${answer.code}, ${reason.ifEmpty { body.take(BODY_EXCERPT) }}")
    }

    private const val NOT_A_REPOSITORY = "fatal: not a git repository"
  }
}
