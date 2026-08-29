package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalGatewayContract

/**
 * The real gateway against the same contract the fake satisfies, over a scripted socket and an
 * in-memory lifecycle.
 *
 * The scripted socket echoes what is typed, which is not a convenience: a pty in canonical mode
 * echoes before any program sees the line, so this is what a real shell does with
 * `send("echo hello\n")` too - and it is why the contract can ask both implementations the same
 * question.
 */
class RealTerminalGatewayContractTest : TerminalGatewayContract() {
  override fun gateway(): TerminalGateway = RealTerminalGateway(
    opener = ScriptedTerminalSocketOpener(reply = { typed -> listOf(TerminalFrames.stdout(typed)) }),
    lifecycle = FakeTerminalLifecycle(),
  )
}

/**
 * A [TerminalLifecycle] that remembers what it was asked to create, with no server behind it.
 *
 * Named after the mechanism it stands in for, like every other double here. Names count from `"1"`
 * because `NamedTermManager._next_available_name` does.
 */
class FakeTerminalLifecycle : TerminalLifecycle {
  private val terminals = LinkedHashSet<String>()
  private var created = 0

  /** The `cwd` of each create, in order, so a caller that must pass one can be shown to. */
  val createdIn = mutableListOf<String?>()

  override suspend fun create(cwd: String?): String {
    createdIn += cwd
    return (++created).toString().also { terminals += it }
  }

  override suspend fun list(): List<String> = terminals.toList()

  /** A terminal that is already gone is not an error, which is the real one's rule too. */
  override suspend fun terminate(name: String) {
    terminals -= name
  }
}
