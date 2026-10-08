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
 * `POST api/kernels`, the one lifecycle call that starts a kernel with no session around it - what
 * a Space's spare is started with (ADR-0080). Apart from `OkHttpKernelLifecycleTest` only because
 * that file is already past the size limit.
 */
class OkHttpBareKernelStartTest {
  @get:Rule val serverRule = MockWebServerRule()

  /**
   * The body carries an empty `path` and no `name`. With no name, `MainKernelHandler.post` starts
   * `km.default_kernel_name`, the kernel a session asking for none would get. The empty path makes
   * `cwd_for_path` answer `root_dir` outright instead of leaving the kernel the server process's cwd
   * (jupyter_server 2.20.0 `services/kernels/handlers.py`, `services/kernels/kernelmanager.py`).
   */
  @Test
  fun startBareKernel_postsTheDefaultKernelAtTheRootAndReturnsItsIdAndName() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"id":"kernel-spare","name":"python3"}"""))

    val started = lifecycle().startBareKernel()

    assertEquals(BareKernel(kernelId = "kernel-spare", kernelName = "python3"), started)
    val request = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("POST", request.method)
    assertEquals("/api/kernels", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
    assertEquals("""{"path":""}""", request.body?.utf8())
  }

  @Test
  fun startBareKernel_failsWithTheServersAnswerWhenTheModelHasNoId() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"name":"python3"}"""))

    val failure = runCatching { lifecycle().startBareKernel() }

    val message = failure.exceptionOrNull()?.message.orEmpty()
    assertTrue("expected the server's answer in '$message'", message.contains("""{"name":"python3"}"""))
  }

  @Test
  fun startBareKernel_failsWithTheStatusWhenTheServerRefuses() = runTest {
    serverRule.server.enqueue(MockResponse(code = 500, body = "boom"))

    val failure = runCatching { lifecycle().startBareKernel() }

    val message = failure.exceptionOrNull()?.message.orEmpty()
    assertTrue("expected the status in '$message'", message.contains("500"))
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
  }
}
