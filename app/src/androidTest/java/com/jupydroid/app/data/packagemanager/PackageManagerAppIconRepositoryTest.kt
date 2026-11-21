package com.jupydroid.app.data.packagemanager

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.AppIcon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The port's real adapter against the platform it wraps: `select` must leave PackageManager's
 * component state - the record launcher, Recents and Settings > Apps all read - agreeing with
 * what `observeIcon` reports and with what `getLaunchIntentForPackage` resolves.
 *
 * Every test starts and ends at the manifest defaults (blue enabled, black disabled) via
 * `COMPONENT_ENABLED_STATE_DEFAULT`, so the suite leaves the device as a fresh install would
 * find it - `LauncherIconAliasesTest` reads the same states and must not see a leftover choice.
 */
@RunWith(AndroidJUnit4::class)
class PackageManagerAppIconRepositoryTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val packageManager = context.packageManager
  private val packageName = context.packageName

  private val blueAlias = ComponentName(packageName, "$packageName.BlueOrbitIcon")
  private val blackAlias = ComponentName(packageName, "$packageName.BlackOrbitIcon")

  @Before
  fun manifestDefaults() = restoreManifestDefaults()

  @After
  fun restoreDefaults() = restoreManifestDefaults()

  private fun restoreManifestDefaults() {
    for (alias in listOf(blueAlias, blackAlias)) {
      packageManager.setComponentEnabledSetting(
        alias,
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        PackageManager.DONT_KILL_APP,
      )
    }
  }

  /** The seed is read back from which alias the platform resolves as launchable - blue by default. */
  @Test
  fun observeIconSeedsFromTheAliasLaunchResolves() = runBlocking {
    val repository = PackageManagerAppIconRepository(context)

    assertEquals(AppIcon.BLUE_ORBIT, repository.observeIcon().first())
  }

  /** Selecting black flips both components' state and what the launch intent resolves. */
  @Test
  fun selectBlackFlipsComponentsAndLaunchResolution() = runBlocking {
    val repository = PackageManagerAppIconRepository(context)

    repository.select(AppIcon.BLACK_ORBIT)

    assertEquals(AppIcon.BLACK_ORBIT, repository.observeIcon().first())
    assertEquals(
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
      packageManager.getComponentEnabledSetting(blackAlias),
    )
    assertEquals(
      PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      packageManager.getComponentEnabledSetting(blueAlias),
    )
    assertEquals(blackAlias, packageManager.getLaunchIntentForPackage(packageName)?.component)
  }

  /** Selecting blue again restores the default wearer - the pair flips both ways. */
  @Test
  fun selectingBlueAgainRestores() = runBlocking {
    val repository = PackageManagerAppIconRepository(context)
    repository.select(AppIcon.BLACK_ORBIT)

    repository.select(AppIcon.BLUE_ORBIT)

    assertEquals(AppIcon.BLUE_ORBIT, repository.observeIcon().first())
    assertEquals(blueAlias, packageManager.getLaunchIntentForPackage(packageName)?.component)
  }
}
