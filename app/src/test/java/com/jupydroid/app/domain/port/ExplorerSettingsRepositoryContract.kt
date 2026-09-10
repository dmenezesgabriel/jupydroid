package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ExplorerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [ExplorerSettingsRepository] must do, whoever implements it - one field today,
 * whether the Recents section is shut.
 *
 * The default is the reference's own: a view arrives expanded unless a stored state says otherwise
 * (`viewContainerModel.ts:517`, `!!viewDescriptor.collapsed`). Deciding *when* to write is the
 * section's job ([com.jupydroid.app.ui.explorer.RecentsViewModel]); this stores what it is given,
 * the same division [TerminalSettingsRepositoryContract] draws.
 */
abstract class ExplorerSettingsRepositoryContract {
  protected abstract fun repository(): ExplorerSettingsRepository

  @Test
  fun theSectionIsOpenUntilSomethingSaysOtherwise() = runTest {
    assertEquals(false, repository().observeSettings().first().recentsCollapsed)
  }

  @Test
  fun remembersThatTheSectionWasShut() = runTest {
    val repository = repository()

    repository.update(ExplorerSettings(recentsCollapsed = true))

    assertEquals(true, repository.observeSettings().first().recentsCollapsed)
  }

  @Test
  fun anUpdateCanOpenItAgain() = runTest {
    val repository = repository()
    repository.update(ExplorerSettings(recentsCollapsed = true))

    repository.update(ExplorerSettings(recentsCollapsed = false))

    assertEquals(false, repository.observeSettings().first().recentsCollapsed)
  }
}
