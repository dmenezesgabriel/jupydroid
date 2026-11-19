package com.jupydroid.extensions.git.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.git.domain.GitStatusException
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.GitStatusReaderContract
import com.jupydroid.extensions.git.domain.RepositoryStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The contract, against a loopback server scripted to answer the way jupyterlab-git's handler does. */
class OkHttpGitStatusReaderTest : GitStatusReaderContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun reader(
    repositories: Map<String, RepositoryStatus.Repository>,
    failures: Map<String, String>,
  ): GitStatusReader {
    serverRule.server.dispatcher = GitStatusLikeTheTabletsServer(repositories, failures)
    return readerUnderTest()
  }

  /**
   * `POST`, because that is the only verb `GitStatusHandler` defines, and the path's segments
   * encoded with its slashes kept, as `URLExt.join` leaves them in the reference.
   */
  @Test
  fun status_postsToTheDirectorysOwnStatusEndpointWithTheToken() = runTest {
    serverRule.server.dispatcher = GitStatusLikeTheTabletsServer(
      repositories = mapOf("my work/demo" to RepositoryStatus.Repository("master")),
    )

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

  /** A stopped server refuses the connection, and the port promises `GitStatusException` for every failure. */
  @Test
  fun status_ofAServerThatRefusesTheConnectionThrowsAGitStatusExceptionNamingThePath() = runTest {
    val reader = readerUnderTest()
    serverRule.server.close()

    val failure = runCatching { reader.status("demo") }.exceptionOrNull()

    assertTrue("$failure", failure is GitStatusException)
    assertTrue("$failure", failure!!.message!!.contains("'demo'"))
  }

  /** A server that takes the request and never answers runs into the client's read timeout. */
  @Test
  fun status_ofAServerThatNeverAnswersThrowsAGitStatusExceptionNamingThePath() = runTest {
    serverRule.server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
    val impatient = OkHttpClient.Builder().readTimeout(SILENT_SERVER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).build()

    val failure = runCatching { readerUnderTest(impatient).status("demo") }.exceptionOrNull()

    assertTrue("$failure", failure is GitStatusException)
    assertTrue("$failure", failure!!.message!!.contains("'demo'"))
  }

  private fun readerUnderTest(client: OkHttpClient = OkHttpClient()): GitStatusReader = OkHttpGitStatusReader(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
    client = client,
  )

  private companion object {
    const val TIMEOUT_SECONDS = 5L
    const val SILENT_SERVER_TIMEOUT_MILLIS = 100L
  }
}

/**
 * Answers `/git/<path>/status` as `GitStatusHandler.post` does: 200 with the status inside a
 * repository, and 500 with git's own `code` and `message` everywhere else
 * (jupyterlab-git@b62a87b7 `packages/jupyterlab/jupyterlab_git/handlers.py`).
 */
private class GitStatusLikeTheTabletsServer(
  private val repositories: Map<String, RepositoryStatus.Repository> = emptyMap(),
  private val failures: Map<String, String> = emptyMap(),
) : Dispatcher() {
  override fun dispatch(request: RecordedRequest): MockResponse {
    // `git/<segments…>/status`, decoded: the directory is what lies between the two.
    val path = request.url.pathSegments.drop(1).dropLast(1).joinToString("/")
    val repository = repositories[path]
    if (repository != null) return answer(200, statusOf(repository).toString())
    val message = failures[path] ?: "fatal: not a git repository (or any of the parent directories): .git\n"
    val refusal = buildJsonObject {
      put("code", JsonPrimitive(128))
      put("command", JsonPrimitive("git status --porcelain -b -u -z"))
      put("message", JsonPrimitive(message))
    }
    return answer(500, refusal.toString())
  }

  /** `x`, `y` and `to` as `git.py`'s `status` builds each file; `from` equals `to` but for a rename. */
  private fun statusOf(repository: RepositoryStatus.Repository) = buildJsonObject {
    put("code", JsonPrimitive(0))
    put("branch", JsonPrimitive(repository.branch))
    put(
      "files",
      buildJsonArray {
        repository.changes.forEach { file ->
          add(
            buildJsonObject {
              put("x", JsonPrimitive(file.index.toString()))
              put("y", JsonPrimitive(file.workTree.toString()))
              put("to", JsonPrimitive(file.path))
              put("from", JsonPrimitive(file.path))
            },
          )
        }
      },
    )
  }

  private fun answer(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()
}
