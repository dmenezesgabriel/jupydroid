package com.jupydroid.extensions.git

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.runtime.remember
import com.jupydroid.extension.api.Extension
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.git.data.fake.FakeGitStatusReader
import com.jupydroid.extensions.git.data.http.OkHttpGitStatusReader
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.ui.SourceControlPanel

/**
 * jupyterlab-git's frontend: one Source Control panel on the side rail (ADR-0104).
 *
 * This class is the module's composition root, the one place it builds an adapter - `:app`'s
 * `di/AppContainer` cannot, since it does not see this module's insides.
 *
 * ```
 * AppContainer(extensions = listOf(GitExtension()))
 * ```
 *
 * @param readerFor the status reader for a Space's server; null is a Space with no server, which
 *   reads [FakeGitStatusReader] so the panel still opens and says nothing false.
 */
class GitExtension(
  private val readerFor: (SpaceServerEndpoint?) -> GitStatusReader = { server ->
    server?.let(::OkHttpGitStatusReader) ?: FakeGitStatusReader()
  },
) : Extension {
  override val sidePanels: List<SidePanelContribution> = listOf(
    // VS Code's own name for the container (`scm.contribution.ts`, vscode@3f3380c); jupyterlab-git
    // captions its panel "Git" (`src/index.ts`), which names the tool rather than what it is for.
    // `AccountTree` is Material's nearest to the codicon's branching nodes (`Codicon.sourceControl`).
    SidePanelContribution(
      id = SOURCE_CONTROL_PANEL_ID,
      label = "Source Control",
      icon = Icons.Filled.AccountTree,
    ) { host ->
      val reader = remember(host.server) { readerFor(host.server) }
      SourceControlPanel(reader = reader, path = host.browsedDirectory)
    },
  )

  private companion object {
    const val SOURCE_CONTROL_PANEL_ID = "git.sourceControl"
  }
}
