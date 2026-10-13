package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A notebook open in the notebook editor: the path it opened under, and which of that path's
 * opens it is - see [DocumentOpening] for why the path alone is not its identity.
 */
@Serializable data class Editor(val notebookId: String, val opening: Int = 0) : NavKey
