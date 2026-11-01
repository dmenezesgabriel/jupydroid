package com.jupydroid.app

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.DarkEditorPalette
import com.jupydroid.app.theme.LightEditorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The window the app launches into - the part of the app that exists before any Compose runs,
 * and is therefore invisible to every other test in this tree.
 *
 * Both assertions here are regressions for defects that were live and that nothing caught: a
 * manifest attribute in the wrong element is still valid XML, and a missing `values-night`
 * qualifier is not an error at all.
 */
@RunWith(AndroidJUnit4::class)
class AppWindowTest {
  /**
   * The keyboard must arrive as an inset, not as a shove.
   *
   * `android:windowSoftInputMode="adjustResize"` was declared on `<application>`, where the
   * platform does not read it - the attribute is only honoured on `<activity>`. Set one level up
   * it is silently ignored, so the activity ran at the framework default, which resolved to
   * `SOFT_INPUT_ADJUST_PAN` (`dumpsys window` reported `sim={adjust=pan}`). Panning translates
   * the whole window to keep the focused field visible, which dragged the top app bar up **under
   * the status bar** every time a cell was tapped: `Back` measured `[16,44][40,68]` with no
   * keyboard and `[16,0][40,17]` with one.
   *
   * `SOFT_INPUT_ADJUST_RESIZE` is deprecated by the platform, but the manifest still declares
   * `adjustResize` (`AndroidManifest.xml`), and this test is the check that it took effect.
   */
  @Test
  @Suppress("DEPRECATION")
  fun theActivityResizesForTheKeyboardRatherThanPanning() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val adjust = activity.window.attributes.softInputMode and
          WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertEquals(
          "expected SOFT_INPUT_ADJUST_RESIZE; pan drags the app bar under the status bar",
          WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
          adjust,
        )
      }
    }
  }

  /**
   * The pre-Compose frame must already be the colour the app is about to draw.
   *
   * `windowBackground` cannot be read from the palette - it is resolved before there is a
   * composition - so the value is stated twice, once in Kotlin and once in `res/values{,-night}`.
   * This is what keeps the copies honest. The theme used to be `Theme.Material.Light` with no
   * night variant at all, so a cold launch in dark mode flashed white.
   */
  @Test
  fun theWindowBackgroundMatchesTheEditorBackgroundOfTheThemeInForce() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
          Configuration.UI_MODE_NIGHT_YES
        val expected = if (night) DarkEditorPalette else LightEditorPalette
        val background = activity.window.decorView.background
        assertTrue("windowBackground must be a flat colour, was $background", background is ColorDrawable)
        assertEquals(
          "windowBackground disagrees with the palette; the two copies have drifted",
          expected.editorBackground,
          Color((background as ColorDrawable).color),
        )
      }
    }
  }
}
