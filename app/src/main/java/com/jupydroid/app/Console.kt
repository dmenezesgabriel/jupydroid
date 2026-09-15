package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A Console sharing [notebookId]'s own kernel - P6b-9.
 *
 * Keyed by the notebook it is attached to, [Editor]'s own identity field: two taps on the same
 * notebook's Console reveal the one already open rather than stacking a second - see
 * [MutableList.openConsole]. Unlike every other document kind, this one is never the surface a
 * detail replaces - it stacks *on top of* the [Editor] it shares a kernel with, because Back has
 * to return to the still-open notebook rather than to the browser.
 */
@Serializable data class Console(val notebookId: String) : NavKey
