package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The session following its document's rename - `setPath` and `setName`
 * (jupyterlab@7f0e6906 `packages/services/src/session/default.ts`), against a real loopback server
 * for the reason [OkHttpKernelLifecycleTest] is.
 */
class OkHttpKernelLifecycleSetPathTest {
  @get:Rule val serverRule = MockWebServerRule()

  /**
   * One PATCH carrying both fields: `SessionHandler.patch` applies `path` and `name` from the same
   * body (`jupyter_server/services/sessions/handlers.py` in the shipped rootfs), so the two requests
   * jupyterlab sends reach the same end state in one. The name is the basename, as `_updatePath`
   * derives it (jupyterlab@7f0e6906 `packages/docregistry/src/context.ts`).
   */
  @Test
  fun setPath_patchesTheSessionsPathAndNameInOneRequest() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = SESSION_JSON))

    lifecycle().setPath("session-1", "g/b.ipynb")

    val request = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("PATCH", request.method)
    assertEquals("/api/sessions/session-1", request.target)
    assertEquals("""{"path":"g/b.ipynb","name":"b.ipynb"}""", request.body?.utf8())
  }

  /** A session the server no longer has is 404; the caller decides whether that matters. */
  @Test
  fun setPath_failsLoudlyWhenTheSessionIsGone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = "Session not found"))

    val failure = runCatching { lifecycle().setPath("session-gone", "b.ipynb") }

    val message = failure.exceptionOrNull()?.message.orEmpty()
    assertTrue("expected a failure, got $failure", failure.isFailure)
    assertTrue("the message must carry the status, was '$message'", message.contains("404"))
  }

  private fun lifecycle() = OkHttpKernelLifecycle(
    connection = ServerConnection(
      baseUrl = serverRule.server.url("/").toString(),
      port = serverRule.server.port,
      token = "secret-token",
    ),
  )

  private companion object {
    const val TIMEOUT_SECONDS = 5L
    const val SESSION_JSON =
      """{"id":"session-1","name":"b.ipynb","path":"g/b.ipynb",""" +
        """"type":"notebook","kernel":{"id":"k1","name":"python3"}}"""
  }
}
