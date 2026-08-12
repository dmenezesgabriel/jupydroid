package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A file that is not a notebook, open in the plain editor.
 *
 * Keyed by path, as [Editor] is: the path is the document's identity throughout
 * (`Contents.IModel.path`), and two files open at once must be two entries with their own
 * `ViewModelStore` - `NavEntry.contentKey` defaults to the key's `toString()`.
 */
@Serializable data class FileEditor(val path: String) : NavKey
