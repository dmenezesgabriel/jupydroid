package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Driven against a real loopback WebSocket rather than a mocked OkHttp, because the whole
 * point of this class is the OkHttp wiring: a fake would only prove the fake works. The
 * server is in-process and ephemeral, so the tests stay fast and hermetic.
 */
class OkHttpKernelSocketOpenerTest {
  @get:Rule val serverRule = MockWebServerRule()

  private val events = LinkedBlockingQueue<KernelSocketEvent>()

  @Test
  fun open_connectsToTheKernelChannelsEndpointCarryingTheToken() {
    val server = echoServer()

    opener(server).open("kernel-1", "session-9") { events.put(it) }

    assertTrue(nextEvent() is KernelSocketEvent.Opened)
    val request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("/api/kernels/kernel-1/channels?session_id=session-9", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  @Test
  fun send_deliversFramesAndReceivesWhatTheKernelSendsBack() {
    val socket = opener(echoServer()).open("kernel-1", "session-9") { events.put(it) }
    assertTrue(nextEvent() is KernelSocketEvent.Opened)

    assertTrue(socket.send("""{"hello":"kernel"}"""))

    assertEquals(KernelSocketEvent.Received("""echo:{"hello":"kernel"}"""), nextEvent())
  }

  /** Frames keep arriving after the first: this socket outlives one execution. */
  @Test
  fun open_staysOpenAcrossManyFrames() {
    val socket = opener(echoServer()).open("kernel-1", "session-9") { events.put(it) }
    assertTrue(nextEvent() is KernelSocketEvent.Opened)

    repeat(3) { socket.send("frame-$it") }

    assertEquals(listOf("echo:frame-0", "echo:frame-1", "echo:frame-2"), List(3) { received() })
  }

  @Test
  fun close_endsTheSocketAndSaysSo() {
    val socket = opener(echoServer()).open("kernel-1", "session-9") { events.put(it) }
    assertTrue(nextEvent() is KernelSocketEvent.Opened)

    socket.close()

    val ended = nextEvent() as KernelSocketEvent.Ended
    assertTrue("a close is not a failure, was $ended", !ended.failed)
  }

  /**
   * A rejected upgrade must surface as a failure carrying the HTTP status. Reporting a bare
   * "closed" here is what would leave a stuck cell with nothing to debug.
   */
  @Test
  fun open_reportsARefusedUpgradeAsAFailureWithItsReason() {
    val server = serverRule.server
    server.enqueue(MockResponse(code = 404))

    opener(server).open("missing-kernel", "session-9") { events.put(it) }

    val ended = nextEvent() as KernelSocketEvent.Ended
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

          // A real server completes the close handshake; without this the client waits for
          // a close frame that never comes and onClosed never fires.
          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
          }
        },
      ).build(),
    )
  }

  private fun opener(server: MockWebServer) = OkHttpKernelSocketOpener(
    connection = ServerConnection(
      baseUrl = server.url("/").toString(),
      port = server.port,
      token = "secret-token",
    ),
  )

  private fun nextEvent(): KernelSocketEvent =
    events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: error("no socket event arrived")

  private fun received(): String = (nextEvent() as KernelSocketEvent.Received).frame

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}
