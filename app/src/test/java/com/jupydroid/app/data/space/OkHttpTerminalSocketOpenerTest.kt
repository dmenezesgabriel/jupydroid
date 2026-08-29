package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Driven against a real loopback WebSocket, for [OkHttpKernelSocketOpenerTest]'s reason: the whole
 * point of this class is the URL it builds and the wiring under it, and a mocked OkHttp would only
 * prove the mock works.
 *
 * The listener itself is [OkHttpJupyterSockets]' and is covered there; what is asserted here is
 * everything that makes a socket a *terminal's*.
 */
class OkHttpTerminalSocketOpenerTest {
  @get:Rule val serverRule = MockWebServerRule()

  private val events = LinkedBlockingQueue<JupyterSocketEvent>()

  /**
   * **The route is outside `api/`, and that is the one shape it does not share with the kernel.**
   *
   * `jupyter_server_terminals/app.py:initialize_handlers` appends
   * `r"/terminals/websocket/(\w+)"` to the extension's handlers, while its REST handlers are
   * `r"/api/terminals"`. Getting this wrong is a 404 at upgrade time and nothing else.
   */
  @Test
  fun open_connectsToTheWebsocketRouteOutsideTheApiPrefix() {
    val server = echoServer()

    opener(server).open("1") { events.put(it) }

    assertTrue(nextEvent() is JupyterSocketEvent.Opened)
    val request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("/terminals/websocket/1", request.target)
  }

  /**
   * The token travels in a header where the reference puts it on the query string
   * (`default.ts:276-278`). Both are read by `JupyterHandler`; the reference has no choice, because
   * a browser `WebSocket` cannot set headers, and OkHttp does.
   */
  @Test
  fun open_carriesTheTokenSoTheUpgradeIsAuthenticated() {
    val server = echoServer()

    opener(server).open("1") { events.put(it) }

    assertTrue(nextEvent() is JupyterSocketEvent.Opened)
    val request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  @Test
  fun send_deliversFramesAndReceivesWhatTheShellSendsBack() {
    val socket = opener(echoServer()).open("1") { events.put(it) }
    assertTrue(nextEvent() is JupyterSocketEvent.Opened)

    assertTrue(socket.send("""["stdin","ls\n"]"""))

    assertEquals(JupyterSocketEvent.Received("""echo:["stdin","ls\n"]"""), nextEvent())
  }

  /**
   * **A terminal the server never named refuses the upgrade with a 404**, and that has to survive
   * as a reason rather than a bare close.
   *
   * `TermSocket.get` raises `web.HTTPError(404)` when `args[0] not in self.term_manager.terminals`
   * (`jupyter_server_terminals/handlers.py:58-59`), which is the shape a stale name takes after the
   * server has been replaced under the app.
   */
  @Test
  fun open_reportsARefusedUpgradeAsAFailureCarryingItsStatus() {
    serverRule.server.enqueue(MockResponse(code = 404))

    opener(serverRule.server).open("gone") { events.put(it) }

    val ended = nextEvent() as JupyterSocketEvent.Ended
    assertTrue("expected a failure, was $ended", ended.failed)
    assertTrue("reason must name the status, was '${ended.reason}'", ended.reason.contains("404"))
  }

  private fun echoServer(): MockWebServer = serverRule.server.apply {
    enqueue(
      MockResponse.Builder().webSocketUpgrade(
        object : WebSocketListener() {
          override fun onMessage(webSocket: WebSocket, text: String) {
            webSocket.send("echo:$text")
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
          }
        },
      ).build(),
    )
  }

  private fun opener(server: MockWebServer) = OkHttpTerminalSocketOpener(
    connection = ServerConnection(
      baseUrl = server.url("/").toString(),
      port = server.port,
      token = "secret-token",
    ),
  )

  private fun nextEvent(): JupyterSocketEvent =
    events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: error("no socket event arrived")

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}
