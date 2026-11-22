package com.jupydroid.app.ui.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.domain.port.LicenceDocuments
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class TwoKindsOfLicenceDocuments : LicenceDocuments {
  override suspend fun documents(): List<LicenceDocument> = listOf(
    LicenceDocument("Licence", "Apache License, Version 2.0"),
    LicenceDocument("Source offer", "# Where the source is\n\nAsk for it.", isMarkdown = true),
  )
}

private class MissingAssetLicenceDocuments : LicenceDocuments {
  override suspend fun documents(): List<LicenceDocument> =
    throw IllegalStateException("licence asset 'licences/NOTICE' is not in the APK")
}

/** Whether the list shows what is shipped, a tap shows its text, and Back returns to the list. */
@RunWith(AndroidJUnit4::class)
class LicencesScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theListNamesEveryDocument() {
    startScreen(TwoKindsOfLicenceDocuments())

    compose.onNodeWithTag(licenceRowTag("Licence")).assertIsDisplayed()
    compose.onNodeWithTag(licenceRowTag("Source offer")).assertIsDisplayed()
  }

  @Test
  fun tappingAPlainDocumentShowsItsTextAsWritten() {
    startScreen(TwoKindsOfLicenceDocuments())

    compose.onNodeWithTag(licenceRowTag("Licence")).performClick()

    compose.onNodeWithText("Apache License, Version 2.0").assertIsDisplayed()
  }

  @Test
  fun tappingAMarkdownDocumentRendersItInsteadOfShowingItsMarkup() {
    startScreen(TwoKindsOfLicenceDocuments())

    compose.onNodeWithTag(licenceRowTag("Source offer")).performClick()

    compose.onNodeWithText("Where the source is").assertIsDisplayed()
    compose.onNodeWithText("# Where the source is", substring = true).assertDoesNotExist()
  }

  @Test
  fun backFromADocumentReturnsToTheList() {
    startScreen(TwoKindsOfLicenceDocuments())
    compose.onNodeWithTag(licenceRowTag("Licence")).performClick()

    Espresso.pressBack()

    compose.onNodeWithTag(licenceRowTag("Licence")).assertIsDisplayed()
    compose.onNodeWithTag(LICENCE_READER_TAG).assertDoesNotExist()
  }

  @Test
  fun aMissingAssetIsStatedInsteadOfAnEmptyList() {
    startScreen(MissingAssetLicenceDocuments())

    compose.onNodeWithText("licences/NOTICE", substring = true).assertIsDisplayed()
  }

  private fun startScreen(documents: LicenceDocuments) {
    compose.setContent {
      JupydroidTheme { LicencesScreen(remember { LicencesViewModel(documents) }) }
    }
  }
}
