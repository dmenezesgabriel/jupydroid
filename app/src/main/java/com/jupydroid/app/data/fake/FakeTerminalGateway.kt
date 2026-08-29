package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile

/**
 * A [TerminalGateway] with no server behind it: terminals are named and what is typed is echoed.
 *
 * **What the app runs where no Space can**, which is the rule every port here follows - the
 * terminal screen composes, scrolls and takes input on an x86_64 emulator, and only the shell is
 * missing. Echo is not a stand-in for a shell so much as the honest half of one: a pty in canonical
 * mode echoes what is typed before any program sees it, so this is the part of a real terminal that
 * is genuinely the terminal's rather than the program's.
 *
 * Names count from `"1"` because that is what the server does - `NamedTermManager.name_template`
 * is `"%d"` over `itertools.count(start=1)` (`terminado/management.py:394-399`) - so a caller that
 * hardcodes a name against the fake is not hardcoding a shape the real one refuses.
 */
class FakeTerminalGateway : TerminalGateway {
  private val terminals = LinkedHashMap<String, EchoingShell>()
  private var opened = 0

  /** The [cwd] each terminal was opened with, so a caller that must pass one can be shown to. */
  val openedIn = mutableListOf<String?>()

  override suspend fun open(cwd: String?): TerminalSession {
    openedIn += cwd
    val name = (++opened).toString()
    terminals[name] = EchoingShell()
    return TerminalSession(name)
  }

  override suspend fun running(): List<TerminalSession> = terminals.keys.map(::TerminalSession)

  override fun attach(session: TerminalSession): Flow<TerminalEvent> = flow {
    val shell = shellFor(session)
    emit(TerminalEvent.Ready)
    // `onSubscription`, not an `emit` beside it: this flow has no replay, so anything typed between
    // readiness and the subscription being registered would be dropped - which is the exact race
    // `RealKernelGateway` documents on its own execute path.
    emitAll(shell.printed.onSubscription { shell.ready.complete(Unit) }.upToEnd())
  }

  override suspend fun send(session: TerminalSession, text: String) {
    val shell = shellFor(session)
    shell.ready.await()
    shell.printed.emit(TerminalEvent.Output(text))
  }

  /** Nothing here has a screen to reflow; the size is remembered so a caller can be shown to. */
  override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) {
    shellFor(session).size = rows to cols
  }

  override suspend fun close(session: TerminalSession) {
    val shell = terminals.remove(session.name) ?: return
    shell.printed.emit(TerminalEvent.Ended("terminal ${session.name} was closed"))
  }

  /** The most recent size [resize] was given, or null where it was never called. */
  fun sizeOf(session: TerminalSession): Pair<Int, Int>? = terminals[session.name]?.size

  private fun shellFor(session: TerminalSession): EchoingShell = terminals[session.name]
    ?: error("no terminal named '${session.name}'; open one first, or expect ${terminals.keys}")

  /** Stops the attach flow once the terminal has ended, because [TerminalEvent.Ended] is terminal. */
  private fun Flow<TerminalEvent>.upToEnd(): Flow<TerminalEvent> = transformWhile { event ->
    emit(event)
    event !is TerminalEvent.Ended
  }

  private class EchoingShell {
    val printed = MutableSharedFlow<TerminalEvent>()
    val ready = CompletableDeferred<Unit>()
    var size: Pair<Int, Int>? = null
  }
}
