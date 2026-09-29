package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.TerminalFontSize
import com.jupydroid.app.domain.model.TerminalSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [TerminalSettingsRepository] must do, whoever implements it - one field today, the
 * font size.
 *
 * Clamping to [TerminalFontSize]'s bounds is the stepper's job (`TerminalPaneViewModel`), not the
 * repository's: this stores whatever it is given, the same division [EditorSettingsRepository]
 * draws between "the store" and "the screen that computes what to store."
 */
abstract class TerminalSettingsRepositoryContract {
  protected abstract fun repository(): TerminalSettingsRepository

  @Test
  fun defaultsToTheSharedCodeSize() = runTest {
    assertEquals(TerminalFontSize.DEFAULT, repository().observeSettings().first().fontSizeSp)
  }

  @Test
  fun remembersAnUpdate() = runTest {
    val repository = repository()

    repository.update(TerminalSettings(fontSizeSp = 20))

    assertEquals(20, repository.observeSettings().first().fontSizeSp)
  }

  @Test
  fun anUpdateCanChangeItAgain() = runTest {
    val repository = repository()
    repository.update(TerminalSettings(fontSizeSp = 20))

    repository.update(TerminalSettings(fontSizeSp = 12))

    assertEquals(12, repository.observeSettings().first().fontSizeSp)
  }
}
