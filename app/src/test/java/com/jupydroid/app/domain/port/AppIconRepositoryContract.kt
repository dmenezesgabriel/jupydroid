package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.AppIcon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [AppIconRepository] must do, whoever implements it - which of the two launcher
 * icons the app wears, and that a selection is what observation then reports.
 *
 * The default is blue: the manifest ships `.BlueOrbitIcon` enabled and `.BlackOrbitIcon`
 * disabled (`app/src/main/AndroidManifest.xml`), so a fresh install - and every fake standing in
 * for one - starts on [AppIcon.BLUE_ORBIT]. The choice itself is PackageManager's component
 * state, which is why there is no separate "stored" value to seed: the platform record *is* the
 * store.
 */
abstract class AppIconRepositoryContract {
  protected abstract fun repository(): AppIconRepository

  @Test
  fun aFreshInstallWearsTheBlueOrbit() = runTest {
    assertEquals(AppIcon.BLUE_ORBIT, repository().observeIcon().first())
  }

  @Test
  fun selectingAnIconIsWhatObservationThenReports() = runTest {
    val repository = repository()

    repository.select(AppIcon.BLACK_ORBIT)

    assertEquals(AppIcon.BLACK_ORBIT, repository.observeIcon().first())
  }
}
