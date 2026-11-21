package com.jupydroid.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launcher's door is a pair of `activity-alias` components, not `MainActivity` itself: the
 * platform resolves whichever alias is enabled as the app's launchable component, and that
 * resolution is what launcher, Recents and Settings > Apps all read. `MainActivity` keeps only
 * its `VIEW` filter - the `.ipynb` import door `ImportIntentFilterTest` pins.
 *
 * Read back from `PackageManager` rather than the manifest source, `ImportIntentFilterTest`'s own
 * shape. The enabled-state invariant is the portable half: exactly one alias is live and it is
 * the one `getLaunchIntentForPackage` names - which alias that is depends on what Settings last
 * chose, so the test pins the *rule*, not the choice.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconAliasesTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val packageName = context.packageName

  private val blueAlias = ComponentName(packageName, "$packageName.BlueOrbitIcon")
  private val blackAlias = ComponentName(packageName, "$packageName.BlackOrbitIcon")

  /** `android:enabled` as the manifest declares it - the value `getComponentEnabledSetting`'s DEFAULT resolves to. */
  private fun manifestEnabled(alias: ComponentName): Boolean = alias == blueAlias

  private fun effectiveEnabled(alias: ComponentName): Boolean {
    val state = context.packageManager.getComponentEnabledSetting(alias)
    return when (state) {
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
      PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
      else -> manifestEnabled(alias)
    }
  }

  private fun aliasInfo(alias: ComponentName) = context.packageManager.getActivityInfo(
    alias,
    // minSdk 32 predates the ActivityInfoFlags overload; the int one is deprecated, not dead.
    PackageManager.MATCH_DISABLED_COMPONENTS,
  )

  /** Both aliases exist, name `MainActivity` as their target, and carry the launcher filter. */
  @Test
  fun bothAliasesTargetMainActivity() {
    for (alias in listOf(blueAlias, blackAlias)) {
      val info = aliasInfo(alias)
      assertEquals("$alias targetActivity", "$packageName.MainActivity", info.targetActivity)
      assertEquals("$alias exported", true, info.exported)
      assertTrue("$alias icon", info.icon != 0)
      assertEquals("$alias label", "JupyterDroid", info.loadLabel(context.packageManager).toString())
    }
  }

  /** The two icons are different resources - an alias pair sharing one icon would switch nothing. */
  @Test
  fun theAliasesCarryDifferentIcons() {
    assertNotEquals(blueAlias.toString(), blackAlias.toString())
    assertNotEquals(aliasInfo(blueAlias).icon, aliasInfo(blackAlias).icon)
  }

  /** Exactly one alias is enabled at a time, and it is the one the launcher resolves. */
  @Test
  fun exactlyOneAliasIsEnabledAndItIsWhatLaunchResolves() {
    val enabled = listOf(blueAlias, blackAlias).filter(::effectiveEnabled)
    assertEquals("expected exactly one enabled launcher alias, got $enabled", 1, enabled.size)

    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    assertNotNull("no launch intent for $packageName", launch)
    val resolved = launch!!.component
    assertNotNull(resolved)
    assertTrue(
      "launch intent resolved $resolved, expected one of $blueAlias/$blackAlias",
      resolved == blueAlias || resolved == blackAlias,
    )
    assertEquals(enabled.single(), resolved)
  }

  /** `MainActivity` itself answers no MAIN/LAUNCHER query - only the aliases do. */
  @Test
  fun mainActivityIsNotItselfALauncherActivity() {
    val launchables = context.packageManager.queryIntentActivities(
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
      0,
    )
    val ours = launchables.mapNotNull { it.activityInfo }.filter { it.packageName == packageName }
    assertEquals(1, ours.size)
    assertTrue(
      "the launchable entry is ${ours.single().name}, expected an alias not MainActivity",
      ours.single().name != "$packageName.MainActivity",
    )
  }
}
