package com.jupydroid.app.ui.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The slug's patterns compile on ART's ICU regex engine, not only on the JVM's: ICU refuses the
 * JVM's `(?U)` flag with a `PatternSyntaxException`, which a JVM test cannot see.
 */
@RunWith(AndroidJUnit4::class)
class HeadingSlugOnDeviceTest {
  @Test
  fun aHeadingIsSluggedOnTheDevice() {
    assertEquals("whats-new", headingSlug("What's new?"))
  }
}
