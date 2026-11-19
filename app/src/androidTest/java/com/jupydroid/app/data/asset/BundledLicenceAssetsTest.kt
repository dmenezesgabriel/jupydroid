package com.jupydroid.app.data.asset

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The names [AssetLicenceDocuments] reads and the files the `bundleLicences` task in
 * `app/build.gradle.kts` copies are written in two places, and no JVM test can see an APK: this
 * reads the real assets of the installed app, so a rename on one side fails here rather than as a
 * crash on the Licences screen.
 */
@RunWith(AndroidJUnit4::class)
class BundledLicenceAssetsTest {
  private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets

  private val licenceDocuments =
    AssetLicenceDocuments(openAsset = assets::open, listAssets = { path -> assets.list(path).orEmpty().asList() })

  private fun bundled(): Map<String, String> =
    runBlocking { licenceDocuments.documents() }.associate { it.title to it.text }

  @Test
  fun everyDocumentIsTheRepositoryFileItsTitleNames() {
    val read = bundled()

    assertEquals(
      listOf("Licence", "Notices", "Gradle libraries", "Third-party software", "Source offer"),
      read.keys.toList(),
    )
    assertTrue(read.getValue("Licence").contains("Apache License"))
    assertTrue(read.getValue("Notices").contains("Termux"))
    assertTrue(read.getValue("Gradle libraries").contains("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:"))
    assertFalse(read.getValue("Gradle libraries").contains("licence not declared"))
    assertTrue(read.getValue("Third-party software").contains("| Package | Version |"))
    assertTrue(read.getValue("Source offer").contains("Source for the GPL and LGPL parts of the APK"))
  }
}
