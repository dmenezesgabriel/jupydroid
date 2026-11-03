package com.jupydroid.app.data.asset

import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.domain.port.LicenceDocuments
import com.jupydroid.app.domain.port.LicenceDocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val BUNDLED_FILES = mapOf(
  "licences/LICENSE" to "Apache License 2.0 text",
  "licences/NOTICE" to "Jupydroid notice",
  "licences/THIRD_PARTY.md" to "| Package | Version |",
  "licences/SOURCE_OFFER.md" to "# Source for the GPL and LGPL parts",
  "licences/gradle/libraries/org.example_lib-a_1.0.pom" to
    "<project><groupId>org.example</groupId><artifactId>lib-a</artifactId><version>1.0</version>" +
    "<licenses><license><name>MIT</name><url>https://example.org/mit</url></license></licenses></project>",
)

private const val RENDERED_GRADLE_LIBRARIES = "lib-a\n  org.example:lib-a:1.0\n  MIT (https://example.org/mit)\n"

class AssetLicenceDocumentsTest : LicenceDocumentsContract() {
  private fun bundled(files: Map<String, String> = BUNDLED_FILES) =
    InMemoryAssets(files).let { AssetLicenceDocuments(it::open, it::list, Dispatchers.Unconfined) }

  override fun documents(): LicenceDocuments = bundled()

  @Test
  fun readsTheBundledFilesInTheOrderAReaderMeetsThem() = runTest {
    val read = bundled().documents()

    assertEquals(
      listOf(
        LicenceDocument("Licence", "Apache License 2.0 text"),
        LicenceDocument("Notices", "Jupydroid notice"),
        LicenceDocument("Gradle libraries", RENDERED_GRADLE_LIBRARIES),
        LicenceDocument("Third-party software", "| Package | Version |", isMarkdown = true),
        LicenceDocument("Source offer", "# Source for the GPL and LGPL parts", isMarkdown = true),
      ),
      read,
    )
  }

  @Test
  fun aMissingAssetNamesItsPathAndWhatShouldHaveBundledIt() {
    val without = BUNDLED_FILES - "licences/NOTICE"

    val failure = assertThrows(IllegalStateException::class.java) {
      runBlocking { bundled(without).documents() }
    }

    assertTrue(failure.message.orEmpty(), "licences/NOTICE" in failure.message.orEmpty())
    assertTrue(failure.message.orEmpty(), "bundleLicences" in failure.message.orEmpty())
  }
}
