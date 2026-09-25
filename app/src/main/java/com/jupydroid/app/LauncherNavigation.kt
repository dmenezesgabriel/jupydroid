package com.jupydroid.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.port.LauncherSettingsRepository
import com.jupydroid.app.ui.editor.NotebookKernelSession
import com.jupydroid.app.ui.launcher.LaunchedDocument

/**
 * What a launched Launcher card leaves behind becomes the document it named.
 *
 * `docmanager:open`'s rule still applies to the file cards - `openDocument` decides the editor from
 * the path alone and writes the recent - while terminal and console cards name their destination
 * directly. The console's session travels beside the back stack the way the notebook console's
 * does: it is a live object two `NavEntry`s share, so it is published through [onConsoleReady]
 * rather than carried in the key.
 */
@Composable
fun rememberLaunchHandler(
  backStack: MutableList<NavKey>,
  openDocument: (String) -> Unit,
  onConsoleReady: (NotebookKernelSession) -> Unit,
): (LaunchedDocument) -> Unit = remember(backStack, openDocument, onConsoleReady) {
  { launched ->
    when (launched) {
      is LaunchedDocument.CreatedNotebook -> openDocument(launched.path)
      is LaunchedDocument.CreatedFile -> openDocument(launched.path)
      is LaunchedDocument.OpenedTerminal -> backStack.openTerminal(launched.name)
      is LaunchedDocument.CreatedConsole -> {
        onConsoleReady(launched.session)
        backStack.openStandaloneConsole(launched.path, launched.title)
      }
    }
  }
}

/**
 * `maybeCreate`: `labShell.layoutModified.connect(() => { if (labShell.isEmpty('main')) create() })`
 * (`launcher-extension/src/index.ts:147-157`) - an empty main area fills itself with a launcher on
 * every layout change. The stack's own notification is `backStack.size` in the key: closing the
 * last document is the layout change that empties it.
 *
 * At one pane there is no empty main area to fill - the browser *is* the window - so the push
 * happens on the first run only (`docs/DECISIONS.md`, P6d) and `launcherSeen` is what makes
 * "once" survive a restart. Setting the flag on the two-pane fill too is deliberate: the launcher
 * has already presented itself there, and a rotation to one pane must not count that presentation
 * as still owed.
 *
 * The second effect is the fill's other half: narrowing the window to one pane removes it - there
 * is no detail area for it to be a placeholder *for*, and leaving it would bury the side panel the
 * user was in. Only when it is on top and only the fill's own: a tapped `+` is a destination like
 * any other, and [isFill] is the whole difference between the two.
 */
@Composable
fun LauncherFillEffect(
  backStack: MutableList<NavKey>,
  settingsRepository: LauncherSettingsRepository,
  twoPane: Boolean,
  isFill: MutableState<Boolean>,
) {
  val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = null)
  LaunchedEffect(twoPane, backStack.size, settings) {
    val loaded = settings ?: return@LaunchedEffect
    val show = backStack.mainAreaIsEmpty() && (twoPane || !loaded.launcherSeen)
    if (!show) return@LaunchedEffect
    backStack.openLauncher()
    isFill.value = true
    if (!loaded.launcherSeen) {
      settingsRepository.update(loaded.copy(launcherSeen = true))
    }
  }
  LaunchedEffect(twoPane) {
    if (!twoPane && isFill.value && backStack.lastOrNull() == Launcher) {
      backStack.removeLastOrNull()
      isFill.value = false
    }
  }
}

/**
 * `main.title.closable = widgets('main').length > 1` (`launcher-extension/src/index.ts:126`):
 * alone in the main area at two panes there is nothing beneath to return to, so a lone launcher
 * draws no close affordance. At one pane the arrow is navigation, not a close, and is always drawn.
 */
fun launcherDismiss(backStack: MutableList<NavKey>, twoPane: Boolean): (() -> Unit)? =
  if (twoPane && !backStack.launcherIsClosable()) {
    null
  } else {
    { backStack.removeLastOrNull() }
  }

/**
 * `closable = false`'s other half: with no close affordance drawn, Back is the one remaining way
 * the lone launcher could leave - and letting it pop would only re-trigger the fill, a whole-frame
 * flicker. Called *after* NavDisplay so its handler wins the gesture over the display's own, which
 * would otherwise pop the entry.
 */
@Composable
fun LoneLauncherBackGuard(backStack: MutableList<NavKey>, twoPane: Boolean) {
  BackHandler(
    enabled = twoPane && backStack.lastOrNull() == Launcher && !backStack.launcherIsClosable(),
  ) {
    // Swallowed: the lone launcher is the main area's resting state, not a screen to leave.
  }
}
