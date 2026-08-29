package com.jupydroid.app.domain.port

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * What every [TerminalGateway] must do, whoever implements it.
 *
 * Run against the fake and the real one alike, for [KernelGatewayContract]'s reason: a port whose
 * implementations agree only by inspection is a port that will drift, and the fake is what the
 * terminal screen is built on until a device with a Space runs it.
 *
 * Deliberately structural. *What* a shell prints is its own business - a real one answers with a
 * prompt and an escape sequence or two - so the assertions live where both can honestly meet:
 * a terminal is named, it announces itself ready before it says anything else, what is typed
 * reaches it, and closing it takes it off the list.
 */
abstract class TerminalGatewayContract {
  protected abstract fun gateway(): TerminalGateway

  @Test
  fun open_returnsATerminalTheServerHasNamed() = runTest {
    val session = gateway().open()

    assertTrue("a terminal needs a name to address it by, got '${session.name}'", session.name.isNotBlank())
  }

  /** Two terminals are two shells; a gateway that reused one name would silently share a process. */
  @Test
  fun open_namesEachTerminalDistinctly() = runTest {
    val gateway = gateway()

    val first = gateway.open()
    val second = gateway.open()

    assertTrue("both terminals answer to '${first.name}'", first != second)
  }

  @Test
  fun running_listsATerminalThatWasOpened() = runTest {
    val gateway = gateway()

    val session = gateway.open()

    assertTrue("expected $session in the listing, got ${gateway.running()}", session in gateway.running())
  }

  /**
   * The listing is asserted **before** the close as well, and that is not ceremony: a gateway that
   * never lists anything satisfies the second half on its own. Measured - the hollow fake this
   * contract was first run against passed exactly that way.
   */
  @Test
  fun close_takesTheTerminalOffTheListing() = runTest {
    val gateway = gateway()
    val session = gateway.open()
    assertTrue("nothing was listed to close: ${gateway.running()}", session in gateway.running())

    gateway.close(session)

    assertTrue("$session survived being closed: ${gateway.running()}", session !in gateway.running())
  }

  /**
   * **Ready comes first, and that is the whole of the reference's connection rule.**
   *
   * `default.ts:301-307` ignores every frame until `setup` arrives and only then calls itself
   * connected. A gateway that emitted output before readiness would let a caller write into a
   * socket the server has not associated yet - which is silent, because the frame is accepted and
   * dropped.
   */
  @Test
  fun attach_announcesReadyBeforeAnythingElse() = runTest {
    val gateway = gateway()

    val first = gateway.attach(gateway.open()).first()

    assertEquals(TerminalEvent.Ready, first)
  }

  /**
   * What is typed reaches the shell. The fake echoes it; a real shell echoes it because a pty in
   * canonical mode does. Either way a gateway that dropped [TerminalGateway.send] would show here
   * and nowhere else.
   */
  @Test
  fun send_reachesTheShellAndComesBackOnTheStream() = runTest {
    val gateway = gateway()
    val session = gateway.open()

    val output = gateway.outputAfterTyping(session, typed = "echo hello\n", expected = "echo hello")

    assertNotNull("nothing carrying 'echo hello' came back within $OUTPUT_TIMEOUT", output)
  }

  /**
   * Resizing reaches an attached terminal, and is asserted **while attached** because a resize is a
   * write.
   *
   * `set_size` is a frame on the terminal's own socket (`terminado/websocket.py`, `on_message`),
   * not a property the server holds elsewhere - so a gateway that accepted one with nothing
   * attached would be accepting a frame it has nowhere to put. The first version of this test did
   * exactly that and passed against the fake while failing against the real one, which is how the
   * port came to say that [TerminalGateway.resize] waits the way [TerminalGateway.send] does.
   */
  @Test
  fun resize_reachesAnAttachedTerminal() = runTest {
    val gateway = gateway()
    val session = gateway.open()

    gateway.whileAttached(session) { gateway.resize(session, rows = 24, cols = 80) }

    assertTrue("$session stopped running when it was resized", session in gateway.running())
  }

  /**
   * Everything [session] has printed once [expected] appears in it, or null if it never does.
   *
   * **Accumulated rather than taken chunk by chunk.** A pty splits its output wherever the read
   * happened to land, so the line a shell echoes can arrive in two frames and neither contains it;
   * a `take(1)` here would be asserting on the size of a buffer. The prompt a real shell prints
   * before anything is typed lands in the same stream, which is the other half of the same reason.
   *
   * **The send is started beside the collection rather than before it**, and that ordering is the
   * port's own rule rather than test choreography: [TerminalGateway.attach] opens the socket when
   * it is subscribed to, and [TerminalGateway.send] suspends until that socket is ready. Sending
   * first would be sending to a terminal nothing is attached to - precisely the case the port
   * exists to make impossible.
   */
  /**
   * Runs [body] with [session] attached, then lets the attach go.
   *
   * A terminal's socket belongs to its attach, so anything written to one needs a collector alive
   * around it - the same discipline the screen has, where a key handler only exists while the
   * terminal is on screen.
   */
  private suspend fun TerminalGateway.whileAttached(session: TerminalSession, body: suspend () -> Unit) =
    coroutineScope {
      val attached = launch { attach(session).collect { } }
      try {
        body()
      } finally {
        attached.cancel()
      }
    }

  private suspend fun TerminalGateway.outputAfterTyping(
    session: TerminalSession,
    typed: String,
    expected: String,
  ): String? = coroutineScope {
    val printed = async {
      withTimeoutOrNull(OUTPUT_TIMEOUT) {
        attach(session)
          .filterIsInstance<TerminalEvent.Output>()
          .runningFold("") { all, chunk -> all + chunk.text }
          .first { expected in it }
      }
    }
    send(session, typed)
    printed.await()
  }

  private companion object {
    /**
     * Generous, because it bounds a real shell on a tablet rather than the fake. It is a failure
     * message rather than a deadline: nothing here is timing-sensitive, and a test that hangs
     * forever says less than one that says what it waited for.
     */
    val OUTPUT_TIMEOUT = 30.seconds
  }
}
