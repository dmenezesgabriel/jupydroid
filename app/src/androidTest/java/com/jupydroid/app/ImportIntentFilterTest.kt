package com.jupydroid.app

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Who the platform resolves a share to, read back from `PackageManager` rather than the manifest
 * source - SpaceHomeBackupAgentTest's shape. The `VIEW` filter on `MainActivity` is the one inbound
 * door: a file manager's `VIEW` carries no category of its own, so `DEFAULT` in the filter is the
 * door those callers use, while Chrome resolves a page's `intent:` link only against activities
 * carrying `BROWSABLE` (developer.chrome.com/docs/android/intents). ADR-0124 drops that category:
 * the page's form of the intent must resolve nothing here, and the installed app's must still
 * resolve `MainActivity`.
 */
@RunWith(AndroidJUnit4::class)
class ImportIntentFilterTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** The intent both callers send at the filter: a `VIEW` on a `content` URI of any MIME type. */
  private fun viewOnContent(categories: List<String> = emptyList()): Intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(Uri.parse("content://media/external/file/1"), "*/*")
    .apply { categories.forEach { addCategory(it) } }

  private fun thisAppResolves(intent: Intent): Boolean = context.packageManager
    // minSdk 32 predates the ResolveInfoFlags overload; the int one is deprecated, not dead.
    .queryIntentActivities(intent, 0)
    .any { it.activityInfo.packageName == context.packageName }

  /** A file manager or a browser's downloads sends a `VIEW` with no category; the door stays. */
  @Test
  fun aViewFromAnInstalledAppResolvesThisApp() {
    assertTrue(thisAppResolves(viewOnContent()))
  }

  /** Chrome adds `BROWSABLE` to a page's `intent:` link; with it, nothing here may resolve. */
  @Test
  fun aWebPageViewResolvesNothingHere() {
    assertFalse(thisAppResolves(viewOnContent(listOf(Intent.CATEGORY_BROWSABLE))))
  }
}
