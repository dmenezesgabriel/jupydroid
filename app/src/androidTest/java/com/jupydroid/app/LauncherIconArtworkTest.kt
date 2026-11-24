package com.jupydroid.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

/**
 * The launcher icons' layers are resources, so their contract is what the platform draws: each
 * adaptive icon's background must render the supplied artwork's gradient field (bright at the
 * top-centre, dark at the bottom - a flat backdrop would mean the shape drawable's gradient never
 * applied), the foregrounds must be the supplied cutouts at every density bucket, and the
 * monochrome silhouette must carry alpha the launcher can tint.
 *
 * Density completeness is read straight out of the shipped APK's zip entries rather than
 * `getDrawableForDensity`, which silently falls back to the nearest bucket and would hide a
 * missing file. The gradient check draws the background drawable itself - rendering the whole
 * `adaptive-icon` through `toBitmap` crops each layer to the safe-zone viewport, which is the
 * launcher's business and the device demo's evidence, not this test's.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconArtworkTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** The 108dp adaptive-icon canvas in pixels per density bucket - mdpi through xxxhdpi. */
  private val iconPixels = linkedMapOf(
    "drawable-mdpi" to 108,
    "drawable-hdpi" to 162,
    "drawable-xhdpi" to 216,
    "drawable-xxhdpi" to 324,
    "drawable-xxxhdpi" to 432,
  )

  private val iconColors = listOf("blue", "black")

  /** Top-centre and bottom-centre luminance of a rendered layer, clear of any foreground artwork. */
  private fun fieldLuminance(bitmap: Bitmap, x: Int, y: Int): Int {
    val p = bitmap.getPixel(x, y)
    return (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)).toInt()
  }

  /**
   * Decodes the `<bucket>` PNG out of the installed APK. aapt2 ships density drawables under a
   * `-v4`-suffixed path (`res/drawable-mdpi-v4/…`), so the entry is found by prefix rather than
   * an exact path - a missing file still fails by name.
   */
  private fun apkPng(zip: ZipFile, bucket: String, name: String): Bitmap {
    val entry = zip.entries().asSequence().firstOrNull {
      it.name.startsWith("res/$bucket") && it.name.endsWith("/$name.png")
    }
    assertNotNull("no res/$bucket*/$name.png entry in the APK", entry)
    val bitmap = BitmapFactory.decodeStream(zip.getInputStream(entry))
    assertNotNull("$name did not decode", bitmap)
    return bitmap!!
  }

  /** Each icon's field is a radial gradient: brighter at the top than the bottom. */
  @Test
  fun backgroundFieldsRenderTheGradient() {
    for (color in iconColors) {
      val resId = context.resources.getIdentifier(
        "ic_launcher_${color}_background",
        "drawable",
        context.packageName,
      )
      assertTrue("no drawable ic_launcher_${color}_background", resId != 0)
      val bitmap = context.getDrawable(resId)!!.toBitmap(432, 432)

      val top = fieldLuminance(bitmap, 216, 30)
      val bottom = fieldLuminance(bitmap, 216, 410)
      assertTrue(
        "ic_launcher_${color}_background top field luminance $top should clearly exceed " +
          "bottom $bottom - a flat backdrop means the gradient drawable never applied",
        top > bottom + 8,
      )
    }
  }

  /** Each foreground is a cutout - mostly transparent surround - at every density bucket. */
  @Test
  fun foregroundsAreCutoutsAtEveryDensity() {
    ZipFile(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir).use { zip ->
      for (color in iconColors) {
        for ((bucket, px) in iconPixels) {
          val bitmap = apkPng(zip, bucket, "ic_launcher_${color}_foreground")
          val path = "res/$bucket/ic_launcher_${color}_foreground.png"
          assertEquals("$path width", px, bitmap.width)
          assertEquals("$path height", px, bitmap.height)

          var transparent = 0
          var sampled = 0
          for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
              sampled++
              if (bitmap.getPixel(x, y) ushr 24 == 0) transparent++
            }
          }
          assertTrue(
            "$path is ${transparent * 100 / sampled}% transparent - the supplied cutout is " +
              "mostly surround, not a full-bleed field",
            transparent.toDouble() / sampled > 0.6,
          )
        }
      }
    }
  }

  /** The monochrome silhouette is alpha the launcher tints: transparent surround, opaque glyph. */
  @Test
  fun monochromeIsASilhouetteAtEveryDensity() {
    ZipFile(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir).use { zip ->
      for ((bucket, px) in iconPixels) {
        val bitmap = apkPng(zip, bucket, "ic_launcher_monochrome")
        val path = "res/$bucket/ic_launcher_monochrome.png"
        assertEquals("$path width", px, bitmap.width)
        assertEquals("$path height", px, bitmap.height)
        assertEquals("$path corner alpha", 0, bitmap.getPixel(2, 2) ushr 24)
        assertTrue("$path centre alpha", (bitmap.getPixel(px / 2, px / 2) ushr 24) > 0)
      }
    }
  }
}
