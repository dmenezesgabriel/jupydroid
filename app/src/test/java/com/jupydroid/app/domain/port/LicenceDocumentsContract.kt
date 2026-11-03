package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [LicenceDocuments] must do, whoever implements it: hand back at least one document,
 * each with a title a list can show and a text a reader can read, and no two sharing a title - the
 * title is what the Licences screen's rows are told apart by.
 */
abstract class LicenceDocumentsContract {
  protected abstract fun documents(): LicenceDocuments

  @Test
  fun hasAtLeastOneDocument() = runTest {
    assertTrue(documents().documents().isNotEmpty())
  }

  @Test
  fun everyDocumentHasATitleAndAText() = runTest {
    documents().documents().forEach {
      assertTrue("title of $it", it.title.isNotBlank())
      assertTrue("text of '${it.title}'", it.text.isNotBlank())
    }
  }

  @Test
  fun noTwoDocumentsShareATitle() = runTest {
    val titles = documents().documents().map { it.title }

    assertEquals(titles.distinct(), titles)
  }
}
