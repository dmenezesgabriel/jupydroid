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
 * The default is shut, as the reference's secondary Explorer views declare it - Outline and Timeline
 * both carry `collapsed: true` (vscode@3f3380c668d0 `contrib/outline/browser/outline.contribution.ts`,
 * `contrib/timeline/browser/timeline.contribution.ts`), which `viewContainerModel.ts` applies until a
 * stored state says otherwise. Deciding *when* to write is the
 * section's job ([com.jupydroid.app.ui.explorer.RecentsViewModel]); this stores what it is given,
 * the same division [TerminalSettingsRepositoryContract] draws.
 */
abstract class ExplorerSettingsRepositoryContract {
  protected abstract fun repository(): ExplorerSettingsRepository

  @Test
  fun theSectionIsShutUntilTheUserOpensIt() = runTest {
    assertEquals(true, repository().observeSettings().first().recentsCollapsed)
  }

  @Test
  fun remembersThatTheSectionWasOpened() = runTest {
    val repository = repository()

    repository.update(ExplorerSettings(recentsCollapsed = false))

    assertEquals(false, repository.observeSettings().first().recentsCollapsed)
  }

  @Test
  fun anUpdateCanShutItAgain() = runTest {
    val repository = repository()
    repository.update(ExplorerSettings(recentsCollapsed = false))

    repository.update(ExplorerSettings(recentsCollapsed = true))

    assertEquals(true, repository.observeSettings().first().recentsCollapsed)
  }
}
