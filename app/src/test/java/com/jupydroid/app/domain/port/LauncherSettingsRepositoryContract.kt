package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.LauncherSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [LauncherSettingsRepository] must do, whoever implements it - one field today,
 * whether the launcher has ever presented itself.
 *
 * The default is the *absence* of a visit: a fresh install has never been shown the launcher, so
 * `launcherSeen` starts false and the first run is what turns it true. The reference answers the
 * same question differently - its main area refills on every `layoutModified`
 * (`launcher-extension/src/index.ts:147-157`), which is the behaviour a two-pane window keeps -
 * while the one-pane push needs the flag so it fires exactly once.
 */
abstract class LauncherSettingsRepositoryContract {
  protected abstract fun repository(): LauncherSettingsRepository

  @Test
  fun aFreshInstallHasNeverSeenTheLauncher() = runTest {
    assertEquals(false, repository().observeSettings().first().launcherSeen)
  }

  @Test
  fun remembersThatTheLauncherWasShown() = runTest {
    val repository = repository()

    repository.update(LauncherSettings(launcherSeen = true))

    assertEquals(true, repository.observeSettings().first().launcherSeen)
  }
}
