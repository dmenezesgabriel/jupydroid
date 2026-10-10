package com.jupydroid.app.data.intent

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the intent asks for is the framework's to read, so it is asserted here rather than on the
 * JVM, where `Intent` and `Uri` are stubs. No test opens a web address: that would put a browser in
 * front of every instrumented test that runs after it.
 */
@RunWith(AndroidJUnit4::class)
class IntentExternalLinkOpenerTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val opener = IntentExternalLinkOpener(context)

  @Test
  fun asksTheDeviceToViewTheDestination() {
    val view = opener.viewIntentFor("https://jupyter.org/try")

    assertEquals(Intent.ACTION_VIEW, view.action)
    assertEquals(Uri.parse("https://jupyter.org/try"), view.data)
  }

  /** The container holds the application context, which may start an activity only in a new task. */
  @Test
  fun opensInANewTask() {
    val flags = opener.viewIntentFor("https://jupyter.org").flags

    assertTrue("expected FLAG_ACTIVITY_NEW_TASK in $flags", flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  /** A device with no browser or mail app is a device, not a crash. */
  @Test
  fun aDestinationNothingTakesOpensNothing() {
    opener.open("jupydroid-no-app-takes-this://anywhere")
  }
}
