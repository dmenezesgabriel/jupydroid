package com.jupydroid.app.ui.settings

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeLicenceDocuments
import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.domain.port.LicenceDocuments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A [LicenceDocuments] whose asset is missing, as the adapter reports it. */
private class MissingAssetLicenceDocuments : LicenceDocuments {
  override suspend fun documents(): List<LicenceDocument> =
    throw IllegalStateException("licence asset 'licences/NOTICE' is not in the APK")
}

/** The Licences screen's state: a list of documents, the one being read, and a load that failed. */
@OptIn(ExperimentalCoroutinesApi::class)
class LicencesViewModelTest {
  @get:Rule val dispatcher = MainDispatcherRule()

  @Test
  fun listsTheDocumentsTheAdapterHolds() = runTest {
    val viewModel = LicencesViewModel(FakeLicenceDocuments())

    val state = viewModel.uiState.value as LicencesUiState.Listed

    assertEquals(listOf("Licence", "Notices"), state.documents.map { it.title })
    assertNull(state.reading)
  }

  @Test
  fun openingATitleReadsThatDocument() = runTest {
    val viewModel = LicencesViewModel(FakeLicenceDocuments())

    viewModel.open("Notices")

    val state = viewModel.uiState.value as LicencesUiState.Listed
    assertEquals("Jupydroid", state.reading?.text)
  }

  @Test
  fun closingReturnsToTheList() = runTest {
    val viewModel = LicencesViewModel(FakeLicenceDocuments())
    viewModel.open("Notices")

    viewModel.close()

    assertNull((viewModel.uiState.value as LicencesUiState.Listed).reading)
  }

  @Test
  fun openingATitleNotListedChangesNothing() = runTest {
    val viewModel = LicencesViewModel(FakeLicenceDocuments())

    viewModel.open("No such document")

    assertNull((viewModel.uiState.value as LicencesUiState.Listed).reading)
  }

  @Test
  fun aFailedLoadStatesTheAssetThatIsMissing() = runTest {
    val viewModel = LicencesViewModel(MissingAssetLicenceDocuments())

    val state = viewModel.uiState.value as LicencesUiState.Failed

    assertTrue(state.message, "licences/NOTICE" in state.message)
  }
}
