package com.jupydroid.extensions.git.data.http

import com.jupydroid.extensions.git.domain.GitStatusException
import com.jupydroid.extensions.git.domain.RepositoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two answers `POST /git/<path>/status` gave on SM-X216B, read verbatim from the test resources:
 * one inside a repository, one at the workspace root, which is not one.
 */
class GitStatusAnswerTest {
  @Test
  fun aRepositorysAnswerReadsItsBranch() {
    val status = GitStatusAnswer.parse(path = "demo", body = recorded("status-in-a-repository.json"))

    assertEquals(RepositoryStatus.Repository(branch = "master"), status)
  }

  @Test
  fun gitsOwnNotARepositoryRefusalIsAnAnswerNotAFailure() {
    val status = GitStatusAnswer.parse(path = "", body = recorded("status-outside-a-repository.json"))

    assertEquals(
      RepositoryStatus.NotARepository(
        reason = "fatal: not a git repository (or any of the parent directories): .git",
      ),
      status,
    )
  }

  /**
   * 128 is git's code for every fatal error, not only this one, so the code alone would turn a
   * corrupt repository into "not a repository".
   */
  @Test
  fun anotherFatalErrorUnderTheSameCodeIsAFailure() {
    val body = """{"code": 128, "command": "git status", "message": "fatal: unable to read tree\n"}"""

    val failure = runCatching { GitStatusAnswer.parse(path = "demo", body = body) }.exceptionOrNull()

    assertTrue("$failure", failure is GitStatusException)
    assertTrue("$failure", failure!!.message!!.contains("fatal: unable to read tree"))
  }

  @Test
  fun anAnswerThatIsNotAStatusIsAFailureThatQuotesIt() {
    val failure = runCatching { GitStatusAnswer.parse(path = "demo", body = "<html>404</html>") }.exceptionOrNull()

    assertTrue("$failure", failure is GitStatusException)
    assertTrue("$failure", failure!!.message!!.contains("<html>404</html>"))
  }

  private fun recorded(name: String): String =
    checkNotNull(javaClass.classLoader?.getResource(name)) { "no test resource '$name'" }.readText()
}
