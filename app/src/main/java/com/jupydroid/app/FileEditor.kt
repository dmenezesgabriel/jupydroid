package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A file that is not a notebook, open in the plain editor.
 *
 * Keyed by the path it opened under and which of that path's opens it is, as [Editor] is: two
 * files open at once must be two entries with their own `ViewModelStore` -
 * `NavEntry.contentKey` defaults to the key's `toString()` - and so must a file recreated where a
 * renamed tab used to be ([DocumentOpening]).
 */
@Serializable data class FileEditor(val path: String, val opening: Int = 0) : NavKey
