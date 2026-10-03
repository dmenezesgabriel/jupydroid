package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [openNewTerminal] - `rememberNewTerminal`'s mint-and-reveal half - where the composable's own
 * reach is `TerminalNavigationTest`'s. The refused half is what matters: the launcher card's own
 * path reports through `LauncherViewModel.error`, and this reach has no error surface, so a
 * refused `open` must land as *no terminal opened* rather than escaping the launch it runs in -
 * the same `runCatching` the VIEW intent gives `createNotebook` in `Navigation.kt`.
 */
class DocumentOpenersTest {
  @Test
  fun anOpenedTerminalIsPushedOnTheStack() = runTest {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openNewTerminal(FakeTerminalGateway(), "dir")

    assertEquals(listOf<NavKey>(Explorer, Terminal("1")), backStack)
  }

  @Test
  fun aRefusedTerminalOpenLeavesTheStackAlone() = runTest {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openNewTerminal(RefusingTerminalGateway(), "dir")

    assertEquals(listOf<NavKey>(Explorer), backStack)
  }

  /** A gateway whose only answer to `open` is no - the mint is the one ask this test needs. */
  private class RefusingTerminalGateway : TerminalGateway {
    override suspend fun open(cwd: String?): TerminalSession = throw IllegalStateException("server refused the mint")
    override suspend fun running(): List<TerminalSession> = emptyList()
    override fun attach(session: TerminalSession): Flow<TerminalEvent> = emptyFlow()
    override suspend fun send(session: TerminalSession, text: String) = Unit
    override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = Unit
    override suspend fun close(session: TerminalSession) = Unit
  }
}
