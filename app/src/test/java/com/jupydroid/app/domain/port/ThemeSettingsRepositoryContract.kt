package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.model.ThemeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [ThemeSettingsRepository] must do, whoever implements it - one field today, which
 * [ThemeOverride] the app is on.
 */
abstract class ThemeSettingsRepositoryContract {
  protected abstract fun repository(): ThemeSettingsRepository

  @Test
  fun followsTheSystemUntilSomethingSaysOtherwise() = runTest {
    assertEquals(ThemeOverride.SYSTEM, repository().observeSettings().first().override)
  }

  @Test
  fun remembersThatDarkWasChosen() = runTest {
    val repository = repository()

    repository.update(ThemeSettings(override = ThemeOverride.DARK))

    assertEquals(ThemeOverride.DARK, repository.observeSettings().first().override)
  }

  @Test
  fun aLaterUpdateCanMoveBackToSystem() = runTest {
    val repository = repository()
    repository.update(ThemeSettings(override = ThemeOverride.LIGHT))

    repository.update(ThemeSettings(override = ThemeOverride.SYSTEM))

    assertEquals(ThemeOverride.SYSTEM, repository.observeSettings().first().override)
  }
}
