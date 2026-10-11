package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeRemoteImageReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An `https:` image source fetched through the port, and every other source passed on. */
class RemoteImageResolverTest {
  private val outer = SourceRecordingImageResolver()
  private val resolver = RemoteImageResolver(FakeRemoteImageReader(mapOf(LOGO_URL to LOGO)), outer)

  @Test
  fun aWebAddressIsFetched() = runTest {
    assertArrayEquals(LOGO, resolver.imageBytes(LOGO_URL))
  }

  /** A web address serving nothing is the web's answer: the alt draws, and no other resolver is asked. */
  @Test
  fun aWebAddressServingNothingGoesNoFurther() = runTest {
    assertNull(resolver.imageBytes("https://images.example/gone.png"))
    assertEquals(emptyList<String>(), outer.asked)
  }

  @Test
  fun aSourceThatIsNotHttpsGoesToTheResolverAroundIt() = runTest {
    val notHttps = listOf("http://images.example/logo.png", "attachment:chart.png", "img/plot.png")

    notHttps.forEach { assertNull(resolver.imageBytes(it)) }
    assertEquals(notHttps, outer.asked)
  }

  private companion object {
    const val LOGO_URL = "https://images.example/logo.png"
    val LOGO = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
  }
}
