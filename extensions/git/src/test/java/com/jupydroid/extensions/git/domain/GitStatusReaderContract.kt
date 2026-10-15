package com.jupydroid.extensions.git.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [GitStatusReader] must do, whoever implements it.
 *
 * The answers are the ones the tablet's `jupyter_server` gave through jupyterlab-git's handlers, so an
 * implementation that passes this has agreed with the device rather than with a guess.
 */
abstract class GitStatusReaderContract {
  /**
   * A reader over a workspace in which each of [repositories] is a repository standing as it maps
   * to, each of [failures] makes git fail with the message it maps to, and every other directory is
   * not a repository at all.
   */
  protected abstract fun reader(
    repositories: Map<String, RepositoryStatus.Repository> = emptyMap(),
    failures: Map<String, String> = emptyMap(),
  ): GitStatusReader

  @Test
  fun aRepositoryAnswersItsBranch() = runTest {
    val status = reader(repositories = mapOf("demo" to RepositoryStatus.Repository("master"))).status("demo")

    assertEquals(RepositoryStatus.Repository(branch = "master"), status)
  }

  /** The demo's repository: one tracked file edited, one new file staged, one new file left alone. */
  @Test
  fun aRepositoryAnswersItsChangedFiles() = runTest {
    val demo = RepositoryStatus.Repository(
      branch = "master",
      changes = listOf(
        ChangedFile(path = "a.txt", index = ' ', workTree = 'M'),
        ChangedFile(path = "s.txt", index = 'A', workTree = ' '),
        ChangedFile(path = "c.txt", index = '?', workTree = '?'),
      ),
    )

    assertEquals(demo, reader(repositories = mapOf("demo" to demo)).status("demo"))
  }

  /** The workspace root is the case the demo opens on: a path with no segment at all. */
  @Test
  fun aDirectoryOutsideEveryRepositoryAnswersSoRatherThanFailing() = runTest {
    val status = reader(repositories = mapOf("demo" to RepositoryStatus.Repository("master"))).status("")

    assertTrue(status is RepositoryStatus.NotARepository)
  }

  @Test
  fun anyOtherFailureThrowsAndNamesThePath() = runTest {
    val reader = reader(failures = mapOf("broken" to "fatal: unable to read tree"))

    val failure = runCatching { reader.status("broken") }.exceptionOrNull()

    assertTrue("$failure", failure is GitStatusException)
    assertTrue("$failure", failure!!.message!!.contains("'broken'"))
    assertTrue("$failure", failure.message!!.contains("fatal: unable to read tree"))
  }
}
