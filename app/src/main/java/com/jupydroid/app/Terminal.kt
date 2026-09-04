package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A shell running on the Space's server, open in the terminal screen.
 *
 * Keyed by the server's own session name (`TerminalSession.name`), not by a path - a terminal has
 * no document identity to reveal by. "New Terminal" always mints a fresh name, so two taps are two
 * different keys and the second replaces the first rather than revealing it - see
 * [MutableList.openTerminal].
 */
@Serializable data class Terminal(val name: String) : NavKey
