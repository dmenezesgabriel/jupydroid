package com.jupydroid.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.ui.editor.NotebookKernelSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Console's holders follow the store they were put in - the activity's, which a relaunch keeps
 * and a finished activity clears. `ConsoleRecreationTest` is the relaunch itself.
 */
class RetainedConsoleTest {
  private val factory = viewModelFactory { initializer { RetainedConsole() } }

  private fun ViewModelStore.console(): RetainedConsole =
    ViewModelProvider.create(this, factory)[RetainedConsole::class]

  @Test
  fun theSameStoreHandsBackTheSessionAndTheCount() {
    val store = ViewModelStore()
    val session = NotebookKernelSession(FakeKernelGateway(), SPACE) { "console-1" }
    store.console().session = session
    store.console().nextOrdinal()

    val held = store.console()

    assertSame(session, held.session)
    assertEquals(2, held.nextOrdinal())
  }

  @Test
  fun aClearedStoreStartsWithNoSessionAndCountsFromOne() {
    val store = ViewModelStore()
    store.console().session = NotebookKernelSession(FakeKernelGateway(), SPACE) { "console-1" }
    store.console().nextOrdinal()

    store.clear()

    assertNull(store.console().session)
    assertEquals(1, store.console().nextOrdinal())
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
