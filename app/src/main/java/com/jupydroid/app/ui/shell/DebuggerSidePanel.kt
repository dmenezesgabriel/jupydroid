package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.SidePanel
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.ui.editor.DebugStackRows

/**
 * The reference's own `Debugger` sidebar (`packages/debugger-extension/src/index.ts:1357`),
 * folded into this app's one side area rather than a second one on the right - see
 * [SidePanel.DEBUGGER]'s own doc for why. A column that draws the open notebook's stopped call
 * stack and top-frame variables and stays there while the document scrolls -
 * [TableOfContentsSidePanel]'s own shape.
 *
 * It reads a [DebugStack] and derives nothing, because it cannot: the panel is in the list pane
 * and the stack is resolved in the detail pane's `EditorViewModel` -
 * [OpenDebugStack][com.jupydroid.app.OpenDebugStack] is
 * [OpenDocumentOutline][com.jupydroid.app.OpenDocumentOutline]'s own seam, crossed for the same
 * reason.
 *
 * **No per-document title**, unlike [TableOfContentsSidePanel]: the reference's own sidebar is
 * always captioned "Debugger" regardless of which document is open, so there is nothing to fall
 * back from.
 *
 * @param stack the open notebook's own stop, or null while nothing that has one is open or stopped.
 * @param onExpand a compound variable's own children - [com.jupydroid.app.OpenDebugStack.expand].
 * @param onSelectFrame a tapped frame's own id - [com.jupydroid.app.OpenDebugStack.selectFrame].
 * @param onEvaluate an ad hoc expression's own answer - [com.jupydroid.app.OpenDebugStack.evaluate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggerSidePanel(
  stack: DebugStack?,
  onExpand: suspend (Int) -> List<DebugVariable>,
  onSelectFrame: suspend (Int) -> Unit,
  onEvaluate: suspend (String) -> DebugVariable?,
  modifier: Modifier = Modifier,
) {
  SideBarScaffold(
    modifier = modifier,
    topBar = { TopAppBar(colors = sideBarTopAppBarColors(), title = { Text(SidePanel.DEBUGGER.label) }) },
  ) { padding ->
    Column(
      modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
      DebugStackRows(stack, onExpand = onExpand, onSelectFrame = onSelectFrame, onEvaluate = onEvaluate)
    }
  }
}
