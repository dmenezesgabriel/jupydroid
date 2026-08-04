package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data class Editor(val notebookId: String) : NavKey
