package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Driven against a real loopback server, for the same reason [OkHttpKernelLifecycleTest] is. */
class OkHttpTerminalLifecycleTest {
  @get:Rule val serverRule = MockWebServerRule()

  /**
   * **200 with the model, and 201 is not it.**
   *
   * `TerminalRootHandler.post` finishes with `json.dumps(model)` and never sets a status, so Tornado
   * sends 200; the reference errors on anything else - `if (response.status !== 200)` in
   * `restapi.ts:52-56`, whose comment still calls this a redirect from an older server. A client
   * that accepted 201 the way the *session* endpoint returns it would be accepting a response this
   * server does not send.
   */
  @Test
  fun create_postsToTheTerminalsRootAndReturnsTheNameTheServerChose() = runTest {
    serverRule.server.enqueue(
      MockResponse(code = 200, body = """{"name":"1","last_activity":"2026-08-29T12:00:00Z"}"""),
    )

    val name = lifecycle().create(cwd = null)

    assertEquals("1", name)
    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/terminals", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /**
   * No `cwd` key at all when none was asked for, rather than `null`.
   *
   * `TerminalRootHandler.post` branches on `if "cwd" in data` and then `Path(data["cwd"])`
   * (`api_handlers.py:41-42`), so a JSON null would reach `Path(None)` and raise. The reference
   * cannot send one - `JSON.stringify` drops an undefined value - and neither may this.
   */
  @Test
  fun create_withNoDirectoryAsksForNoneRatherThanNull() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = """{"name":"1"}"""))

    lifecycle().create(cwd = null)

    assertEquals("{}", takeRequest().body?.utf8())
  }

  /**
   * A directory is sent **relative**, which is what the handler resolves against `server_root_dir`
   * and what "Open Terminal Here" passes (`terminal-extension/src/index.ts:151`, `cwd: item.path`).
   */
  @Test
  fun create_sendsTheDirectoryItWasGiven() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = """{"name":"2"}"""))

    lifecycle().create(cwd = "reports")

    assertEquals("""{"cwd":"reports"}""", takeRequest().body?.utf8())
  }

  /**
   * **201 is refused, and nothing but the assertion says so.**
   *
   * Written after a mutation showed the rule was a comment: swapping `code != 200` for
   * `!isSuccessful` left every other test in this file green. The sessions endpoint really does
   * answer 201 (`OkHttpKernelLifecycleTest` enqueues one), so a client that quietly accepted the
   * 2xx family would be accepting a response *this* endpoint never sends - and would go on to parse
   * a body that is not the terminal model.
   */
  @Test
  fun create_refusesA201TheWayTheReferenceDoes() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"name":"1"}"""))

    val failure = runCatching { lifecycle().create(cwd = null) }.exceptionOrNull()

    assertTrue("expected 201 to be refused, got ${failure?.message}", failure?.message?.contains("201") == true)
  }

  @Test
  fun create_refusesAResponseWithNoName() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = """{"last_activity":"2026-08-29T12:00:00Z"}"""))

    val failure = runCatching { lifecycle().create(cwd = null) }.exceptionOrNull()

    assertTrue("expected the body in '${failure?.message}'", failure?.message?.contains("last_activity") == true)
  }

  @Test
  fun list_returnsEveryRunningTerminalByName() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = """[{"name":"1"},{"name":"2"}]"""))

    assertEquals(listOf("1", "2"), lifecycle().list())
  }

  @Test
  fun list_ofAServerWithNoTerminalsIsEmptyRatherThanAFailure() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "[]"))

    assertEquals(emptyList<String>(), lifecycle().list())
  }

  /** `self.set_status(204)` in `TerminalHandler.delete`, and the only status that means deleted. */
  @Test
  fun terminate_deletesTheTerminalByName() = runTest {
    serverRule.server.enqueue(MockResponse(code = 204))

    lifecycle().terminate("1")

    val request = takeRequest()
    assertEquals("DELETE", request.method)
    assertEquals("/api/terminals/1", request.target)
  }

  /**
   * A terminal the server has already forgotten is **not** an error - it is gone, which is what the
   * caller asked for. The reference warns and returns (`restapi.ts:107-113`), and this is reachable
   * for a reason of our own besides: a shell that exits takes its terminal with it
   * (`NamedTermManager.on_eof` pops it), so the app can always be one step behind.
   */
  @Test
  fun terminate_ofATerminalThatIsAlreadyGoneIsSuccessWithALine() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = """{"message":"Terminal not found: 9"}"""))
    val log = RecordingSpaceLifecycleLog()

    val failure = runCatching { lifecycle(log).terminate("9") }.exceptionOrNull()

    assertNull("a terminal that was already gone is not a failure", failure)
    assertTrue("expected a line saying so, got ${log.stages}", log.stages.any { "gone" in it })
  }

  @Test
  fun terminate_treatsAServerErrorAsARefusal() = runTest {
    serverRule.server.enqueue(MockResponse(code = 500, body = "boom"))

    val failure = runCatching { lifecycle().terminate("1") }.exceptionOrNull()

    assertTrue("expected the status in '${failure?.message}'", failure?.message?.contains("500") == true)
  }

  /**
   * **A 200 is a refusal too**, which is the half of "anything else" that reads as success and so
   * is the half worth its own test - the same rule `OkHttpKernelLifecycle.requireSessionGone`
   * applies to a session. `TerminalHandler.delete` sets 204 explicitly; a 200 here means something
   * other than that handler answered, and treating it as deleted would leave the app showing a
   * terminal the server still has.
   */
  @Test
  fun terminate_treatsAPlain200AsARefusalRatherThanSuccess() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "{}"))

    val failure = runCatching { lifecycle().terminate("1") }.exceptionOrNull()

    assertTrue("expected 200 to be refused, got ${failure?.message}", failure?.message?.contains("200") == true)
  }

  private fun lifecycle(log: SpaceLifecycleLog = SpaceLifecycleLog.Silent) = OkHttpTerminalLifecycle(
    connection = ServerConnection(
      baseUrl = serverRule.server.url("/").toString(),
      port = serverRule.server.port,
      token = "secret-token",
    ),
    log = log,
  )

  private fun takeRequest() = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}
