package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The [OpenDocuments] registry kept for exactly as long as the documents it points at.
 *
 * A document's ViewModel lives in its `NavEntry`'s store, which a relaunched activity keeps - a
 * rotation relaunches it, and so does a dark-mode switch - and it registers only when it is built.
 * A registry made by plain `remember` was therefore rebuilt empty beside documents that were still
 * open and unsaved, and the tab's close stopped asking
 * (`OpenDocumentsRecreationTest.closingAnUnsavedDocumentStillAsksAfterTheActivityIsRelaunched`).
 *
 * A `ViewModel` of the activity rather than a field of `AppContainer`: the process outlives a
 * finished activity, and with it every document this would still hold.
 */
class RetainedOpenDocuments : ViewModel() {
  val openDocuments = OpenDocuments()
}

/**
 * The registry the activity already holds, or a new one on its first composition.
 *
 * ```
 * MainNavigation(…, openDocuments = rememberRetainedOpenDocuments())
 * ```
 */
@Composable
fun rememberRetainedOpenDocuments(): OpenDocuments = viewModel { RetainedOpenDocuments() }.openDocuments
