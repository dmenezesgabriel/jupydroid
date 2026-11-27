package com.jupydroid.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.domain.port.TerminalSettingsRepository
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
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

/**
 * The [Terminal] entry, in the strip's [tabbedPane] like every other document. The shell's own
 * title change is what the strip's [liveTabTitles] hears.
 *
 * ```
 * terminalEntry(spacePorts.terminalGateway, appContainer.terminalSettings, liveTabTitles, tabbedPane)
 * ```
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.terminalEntry(
  terminalGateway: TerminalGateway,
  terminalSettings: TerminalSettingsRepository,
  liveTabTitles: SnapshotStateMap<NavKey, String>,
  tabbedPane: @Composable (@Composable (saveTabAs: (NavKey) -> Unit) -> Unit) -> Unit,
) {
  entry<Terminal>(metadata = ListDetailSceneStrategy.detailPane()) { terminal ->
    tabbedPane {
      TerminalPaneScreen(
        session = TerminalSession(terminal.name),
        terminalGateway = terminalGateway,
        // `term.onTitleChange` → `title.label` - the tab, so the strip's map is what hears it.
        onTitleChanged = { title ->
          if (title == null) liveTabTitles.remove(terminal) else liveTabTitles[terminal] = title
        },
        terminalSettingsRepository = terminalSettings,
      )
    }
  }
}
