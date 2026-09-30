package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * **A real shell answers**, over the port the app actually uses.
 *
 * [TerminalServiceTest] proved the server spawns the pinned shell and that REST
 * reports it; what only this can show is the half after that - the `setup` frame arriving, bytes
 * coming back over the websocket, and what is typed reaching the pty. Nothing below `TerminalGateway`
 * is reached directly, so this also proves the wiring rather than the pieces.
 *
 * `Assume`-skipped where no rootfs ships, the rule [PtySupportTest] and [TerminalServiceTest]
 * follow, so it is green-by-skip on x86_64.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.TerminalGatewayDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TerminalGatewayDeviceTest {
  private val opened = mutableListOf<TerminalSession>()

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no shell to talk to",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  /** Every terminal this class opened, so one test's shell cannot be another's listing. */
  @After
  fun endWhatThisTestOpened() {
    if (runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isFailure) return
    runBlocking { opened.forEach { runCatching { terminals.close(it) } } }
    opened.clear()
  }

  /**
   * The `["setup", {}]` frame arrives, which is the only thing that means *connected*.
   *
   * A completed websocket handshake is not it: `TermSocket.open` runs afterwards and is what
   * appends this client to `terminal.clients`. Reaching [TerminalEvent.Ready] is therefore the
   * proof that the whole chain - REST create, upgrade, tornado handler - actually joined up.
   */
  @Test
  fun attachingToATerminalReachesReady(): Unit = runBlocking {
    val session = openTerminal()

    val first = withTimeoutOrNull(READY_TIMEOUT) { terminals.attach(session).first() }

    assertEquals("no setup frame arrived within $READY_TIMEOUT", TerminalEvent.Ready, first)
  }

  /**
   * **Something is typed, the shell *runs* it, and its output comes back.**
   *
   * Two things at once and neither is reachable from REST. The bytes travel *to* the pty, which
   * `TerminalServiceTest` could not show at all, and they come back over the socket as `stdout`.
   * The output is accumulated rather than read one frame at a time because a pty splits wherever
   * the read landed, and because the shell's prompt is in the same stream ahead of the answer.
   *
   * **The command is arithmetic and not `echo ok`, because a pty echoes.** An earlier plan row asked for
   * `send("echo ok\n")` -> `ok`, and that gate is hollow: a terminal in canonical mode echoes the
   * typed line back before any program sees it, so `"ok"` arrives inside the echo of the command
   * whether or not a shell ever ran. Measured, on the first green run of this test - the captured
   * output was `… $ echo ok`, with no answer after it. `42` cannot appear that way: it is nowhere
   * in `echo $((6*7))`, so only an executing shell can produce it.
   */
  @Test
  fun whatIsTypedRunsInTheShellAndItsOutputComesBack(): Unit = runBlocking {
    val session = openTerminal()

    val printed = outputAfterTyping(session, typed = "echo \$((6*7))\n", expected = "42")

    assertNotNull("the shell never printed 42 within $OUTPUT_TIMEOUT; it did not run what was typed", printed)
    Log.i(TAG, "shell printed: ${printed?.take(EXCERPT)}")
  }

  /**
   * **The shell starts in the workspace**, which is the cwd repair seen from the far end.
   *
   * `JupyterServerWorkingDirTest` asserts the `ProcessBuilder` and
   * `TerminalServiceTest.theServerProcessRunsInTheWorkspaceItIsRootedAt` asserts the server
   * process; this is the only one that asks the *shell*, which is the thing a user actually gets.
   * It read `/` before that commit.
   *
   * **`cwd=$(pwd)` rather than `pwd`, and for a sharper reason than the echo above.** busybox
   * ash's default `PS1` is `\w \$`, so the workspace path is already in the **prompt** - and the
   * prompt is replayed out of `read_buffer` the moment this attaches. A bare `pwd` therefore
   * matched before the command had even been echoed, let alone run. Measured: the captured output
   * of the first green run was the prompt and a `\u001B[6n` cursor query, nothing else. Prefixing
   * the answer puts it somewhere only an executing shell can produce it.
   */
  @Test
  fun theShellStartsInTheWorkspaceRatherThanTheFilesystemRoot(): Unit = runBlocking {
    val session = openTerminal()
    // `canonicalPath`, because `getcwd(3)` resolves symlinks and Android's `filesDir` is under
    // `/data/user/0/<pkg>`, which is a link to `/data/data/<pkg>`. A shell started in the workspace
    // therefore prints the second, and asserting the first fails on the link rather than the cwd -
    // measured, on the first run of this test. [TerminalServiceTest] canonicalises for this reason.
    val workspace = notebookWorkspaceIn(baseSpaceDir(context)).canonicalPath

    val printed = outputAfterTyping(session, typed = "echo cwd=\$(pwd)\n", expected = "cwd=$workspace")

    assertNotNull("the shell never printed cwd=$workspace within $OUTPUT_TIMEOUT", printed)
    Log.i(TAG, "shell pwd: ${printed?.takeLast(EXCERPT)}")
  }

  /** A terminal the gateway opened is listed by it, over the same REST the screen will read. */
  @Test
  fun anOpenedTerminalIsListedAndAClosedOneIsNot(): Unit = runBlocking {
    val session = openTerminal()
    assertTrue("expected $session in ${terminals.running()}", session in terminals.running())

    terminals.close(session)
    opened -= session

    assertTrue("$session survived being closed", session !in terminals.running())
  }

  private suspend fun openTerminal(): TerminalSession = terminals.open(cwd = null).also { opened += it }

  /**
   * Everything the shell printed once [expected] appears in it, or null if it never does.
   *
   * The typing is started beside the collection, never before it: a terminal's socket is opened by
   * the attach, and `send` suspends until that socket reports `setup` - so this is the port's own
   * ordering rather than choreography.
   */
  private suspend fun outputAfterTyping(session: TerminalSession, typed: String, expected: String): String? =
    coroutineScope {
      val printed = async {
        withTimeoutOrNull(OUTPUT_TIMEOUT) {
          terminals.attach(session)
            .filterIsInstance<TerminalEvent.Output>()
            .runningFold("") { all, chunk -> all + chunk.text }
            .first { expected in it }
        }
      }
      launch { terminals.send(session, typed) }
      printed.await()
    }

  private companion object {
    /** The socket only has to open; the Space is already up by the time these run. */
    val READY_TIMEOUT = 30.seconds

    /** A shell on a tablet, not a fake: generous, and a failure message rather than a deadline. */
    val OUTPUT_TIMEOUT = 30.seconds

    const val EXCERPT = 400

    /** `adb logcat -s JupydroidSpace`, the filter every Space investigation already uses. */
    const val TAG = "JupydroidSpace"

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The gateway the app builds, over the run's one server - see [BaseSpaceUnderTest] for why a
     * second one here would fight [TerminalServiceTest] over the same runtime directory.
     */
    val terminals: TerminalGateway by lazy { baseSpaceTerminalGateway(BaseSpaceUnderTest.require()) }
  }
}
