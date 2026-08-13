package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Driven against a real loopback server, for the same reason the socket opener is. */
class OkHttpKernelLifecycleTest {
  @get:Rule val serverRule = MockWebServerRule()

  @Test
  fun start_asksTheServerForAKernelAndReturnsItsId() = runTest {
    serverRule.server.enqueue(
      MockResponse(code = 201, body = """{"id":"kernel-abc","name":"python3"}"""),
    )

    val kernelId = lifecycle().start("Analysis.ipynb")

    assertEquals("kernel-abc", kernelId)
    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/kernels", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** Without a body the server picks a default kernel; we still have to send valid JSON. */
  @Test
  fun start_sendsAJsonBodySoTheServerAcceptsThePost() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"id":"k1"}"""))

    lifecycle().start("Analysis.ipynb")

    val request = takeRequest()
    assertTrue(
      "expected a JSON content type, was '${request.headers["Content-Type"]}'",
      request.headers["Content-Type"]!!.startsWith("application/json"),
    )
  }

  /**
   * The kernel is started **at the notebook's path**, which is what gives it a working directory.
   *
   * `jupyter_server` only sets one when it is given a path:
   *
   *     if path is not None:
   *         kwargs["cwd"] = self.cwd_for_path(path, env=kwargs.get("env", {}))
   *
   * (`services/kernels/kernelmanager.py:_async_start_kernel`, read out of the server in our own
   * rootfs). With no path there is no `cwd` at all and the kernel inherits the server process's -
   * which on the device is a **read-only filesystem**, so `open('data.csv', 'w')` in a cell raised
   * `OSError: [Errno 30] Read-only file system`. X1a's promise that a notebook can `open()` a file
   * the user can see had never actually held for a relative path.
   *
   * JupyterLab gets there by creating a *session* - `SessionContext({path: this._path, type:
   * 'notebook', ...})` (`packages/docregistry/src/context.ts:82-90`) - and the session's path is
   * handed to the same `start_kernel(path=...)`. `POST /api/kernels` takes the path directly
   * (`services/kernels/handlers.py:56`), which reaches the identical server code without claiming
   * to be the session work that Steps 17/18 own.
   */
  @Test
  fun start_asksForTheKernelAtTheNotebooksPathSoItHasAWorkingDirectory() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"id":"k1"}"""))

    lifecycle().start("reports/Analysis.ipynb")

    assertEquals("""{"path":"reports/Analysis.ipynb"}""", takeRequest().body?.utf8())
  }

  /** The server turns the *document* path into the directory holding it, so this sends the path. */
  @Test
  fun start_sendsTheDocumentPathRatherThanItsDirectory() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"id":"k1"}"""))

    lifecycle().start("Analysis.ipynb")

    assertEquals("""{"path":"Analysis.ipynb"}""", takeRequest().body?.utf8())
  }

  @Test
  fun start_failsLoudlyWhenTheServerWillNotStartAKernel() = runTest {
    serverRule.server.enqueue(MockResponse(code = 500, body = "boom"))

    val failure = runCatching { lifecycle().start("Analysis.ipynb") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
    assertTrue(
      "the message must carry the status, was '${failure.exceptionOrNull()?.message}'",
      failure.exceptionOrNull()?.message?.contains("500") == true,
    )
  }

  /** A kernel with no id is not a kernel; returning "" would fail later and further away. */
  @Test
  fun start_failsWhenTheResponseCarriesNoKernelId() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"name":"python3"}"""))

    val failure = runCatching { lifecycle().start("Analysis.ipynb") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  @Test
  fun interrupt_postsToTheKernelsInterruptEndpoint() = runTest {
    serverRule.server.enqueue(MockResponse(code = 204))

    lifecycle().interrupt("kernel-abc")

    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/kernels/kernel-abc/interrupt", request.target)
  }

  @Test
  fun interrupt_failsLoudlyWhenTheKernelIsGone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = "no such kernel"))

    val failure = runCatching { lifecycle().interrupt("kernel-gone") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  /**
   * `POST /api/kernels/<id>/restart`, the same shape as interrupt and the endpoint JupyterLab
   * calls (`packages/services/src/kernel/restapi.ts:127-145`).
   */
  @Test
  fun restart_postsToTheKernelsRestartEndpoint() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "{\"id\":\"kernel-abc\",\"name\":\"python3\"}"))

    lifecycle().restart("kernel-abc")

    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/kernels/kernel-abc/restart", request.target)
  }

  @Test
  fun restart_failsLoudlyWhenTheKernelIsGone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = "no such kernel"))

    val failure = runCatching { lifecycle().restart("kernel-gone") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  private fun takeRequest() = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!

  private fun lifecycle(server: MockWebServer = serverRule.server) = OkHttpKernelLifecycle(
    connection = ServerConnection(
      baseUrl = server.url("/").toString(),
      port = server.port,
      token = "secret-token",
    ),
  )

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}
