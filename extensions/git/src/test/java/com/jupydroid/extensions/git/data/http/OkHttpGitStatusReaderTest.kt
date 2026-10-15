package com.jupydroid.extensions.git.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.GitStatusReaderContract
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The contract, against a loopback server scripted to answer the way jupyterlab-git's handler does. */
class OkHttpGitStatusReaderTest : GitStatusReaderContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun reader(repositories: Map<String, String>, failures: Map<String, String>): GitStatusReader {
    serverRule.server.dispatcher = GitStatusLikeTheTabletsServer(repositories, failures)
    return readerUnderTest()
  }

  /**
   * `POST`, because that is the only verb `GitStatusHandler` defines, and the path's segments
   * encoded with its slashes kept, as `URLExt.join` leaves them in the reference.
   */
  @Test
  fun status_postsToTheDirectorysOwnStatusEndpointWithTheToken() = runTest {
    serverRule.server.dispatcher = GitStatusLikeTheTabletsServer(repositories = mapOf("my work/demo" to "master"))

    readerUnderTest().status("my work/demo")

    val request = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("POST", request.method)
    assertEquals("/git/my%20work/demo/status", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** The workspace root has no segment of its own: the tablet's server was asked `/git/status`. */
  @Test
  fun status_ofTheRootAsksWithNoPathSegment() = runTest {
    serverRule.server.dispatcher = GitStatusLikeTheTabletsServer()

    readerUnderTest().status("")

    assertEquals("/git/status", serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!.target)
  }

  private fun readerUnderTest(): GitStatusReader = OkHttpGitStatusReader(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
  )

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}

/**
 * Answers `/git/<path>/status` as `GitStatusHandler.post` does: 200 with the status inside a
 * repository, and 500 with git's own `code` and `message` everywhere else
 * (jupyterlab-git@b62a87b7 `packages/jupyterlab/jupyterlab_git/handlers.py`).
 */
private class GitStatusLikeTheTabletsServer(
  private val repositories: Map<String, String> = emptyMap(),
  private val failures: Map<String, String> = emptyMap(),
) : Dispatcher() {
  override fun dispatch(request: RecordedRequest): MockResponse {
    // `git/<segments…>/status`, decoded: the directory is what lies between the two.
    val path = request.url.pathSegments.drop(1).dropLast(1).joinToString("/")
    val branch = repositories[path]
    if (branch != null) return answer(200, """{"code": 0, "branch": "$branch", "files": []}""")
    val message = failures[path] ?: "fatal: not a git repository (or any of the parent directories): .git\n"
    val refusal = buildJsonObject {
      put("code", JsonPrimitive(128))
      put("command", JsonPrimitive("git status --porcelain -b -u -z"))
      put("message", JsonPrimitive(message))
    }
    return answer(500, refusal.toString())
  }

  private fun answer(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()
}
