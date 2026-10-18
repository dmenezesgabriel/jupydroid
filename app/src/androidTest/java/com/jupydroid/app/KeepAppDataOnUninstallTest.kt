package com.jupydroid.app

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the system's uninstall offers, read back from the package the platform parsed rather than
 * from the manifest source: an attribute the platform does not read is still valid XML.
 */
@RunWith(AndroidJUnit4::class)
class KeepAppDataOnUninstallTest {
  /**
   * An uninstall removes `filesDir`, and every Space's home - the user's notebooks, files, chats and
   * the sign-ins a program wrote there - lives under it. `android:hasFragileUserData` is what makes
   * the system's uninstall dialog offer to keep it, and it defaults to `false`
   * (developer.android.com/guide/topics/manifest/application-element#fragileuserdata).
   *
   * `ApplicationInfo.hasFragileUserData()` is hidden from the SDK, so this reads the flag where the
   * platform reports it: `dumpsys package` lists it in `privateFlags`.
   */
  @Test
  fun theSystemUninstallOffersToKeepTheAppsData() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    val dump = instrumentation.uiAutomation.executeShellCommand("dumpsys package $packageName").readText()
    val privateFlags = dump.lineSequence().map(String::trim).firstOrNull { it.startsWith("privateFlags=") }
    assertTrue(
      "expected PRIVATE_FLAG_HAS_FRAGILE_USER_DATA in $packageName's privateFlags, was: $privateFlags",
      privateFlags.orEmpty().contains("PRIVATE_FLAG_HAS_FRAGILE_USER_DATA"),
    )
  }

  private fun ParcelFileDescriptor.readText(): String =
    ParcelFileDescriptor.AutoCloseInputStream(this).bufferedReader().use { it.readText() }
}
