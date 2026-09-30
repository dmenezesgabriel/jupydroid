package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.Spacing

/** The reference's own `circleIconProps.height`, `'24px'` (`apputils/src/toolbar/widget.tsx:210`). */
private val STATUS_GLYPH_SIZE = 24.dp

/**
 * Which kernel this notebook runs on, and what it is doing - at the right of the action row.
 *
 * **Two of the reference's toolbar items, in one control.** JupyterLab ranks `kernelName` 1000 and
 * `executionProgress` 1002 (`packages/notebook-extension/schema/panel.json`), after the spacer at
 * 100, so they are the last two things on its notebook toolbar: a button showing
 * `sessionContext.kernelDisplayName` (`apputils/src/toolbar/widget.tsx:149-171`) and beside it the
 * status circle (`notebook/src/executionindicator.tsx:126-188`). A phone's bar has no room for two
 * 48dp targets that answer one question, so they are one - which is also VS Code's shape, a single
 * item in group `'status'` at order `-10` (`viewParts/notebookKernelView.ts:73-76`).
 *
 * **Right of Find and left of the `⋮`, which is what "the far right" means once the reference's own
 * layout is read.** `ReactiveToolbar` inserts its popup opener first and clamps every later
 * `insertItem` to `widgets.length - 1`, so the opener is always the *last* widget
 * (`packages/ui-components/src/components/toolbar.tsx:391`, `:470-480`) and `_getWidgetsToRemove`
 * walks to `toIndex = widgets.length - 1`, never collecting it. This app's popup opener is
 * [NotebookActionsMenu]. Android's top-app-bar convention puts the overflow last too, so the two
 * agree.
 *
 * **The glyph is the reference's three-way split**, in Material's vocabulary: an empty circle at
 * rest (`circleEmptyIcon`), a progress ring while there is work (`ProgressCircle`, which
 * `ExecutionIndicator` draws at 0% busy and 100% idle), and an offline bolt with no kernel
 * (`offlineBoltIcon`, which the reference uses for `connecting`/`disconnected`/`unknown`). Neutral
 * rather than coloured, which is [CellTypeControl]'s call and for its reason: this app spends brand
 * blue on the active cell's collapser and the editor being typed into, and a sixth coloured thing
 * in the row would spend the one signal those two rely on.
 *
 * **The name is the *live* kernel's, not the file's.** A notebook's `metadata.kernelspec` can say
 * "Python 3.11 (conda)" and the Space still runs its own `python3`; `kernelDisplayName` resolves
 * through the spec manager for exactly that reason (`apputils/src/sessioncontext.tsx:584-603`), so
 * the state it is handed is resolved at bind time - a bound kernel's `display_name`, a specless
 * one's raw name, "No Kernel" while nothing is bound.
 *
 * **The tap is the picker, which is the reference's own wiring.** The `kernelName` toolbar item
 * calls `dialogs.selectKernel` on click (`apputils/src/toolbar/widget.tsx:155`) - it does not
 * open a menu. The restart-and-shutdown rows the old tap showed stay on the bottom bar's `⋮`,
 * which anchors [KernelMenu] still, and that menu's own "Change Kernel…" row lands here too -
 * `notebook:change-kernel` is the same act from either.
 *
 * ```
 * KernelIndicator(status = state.kernelStatus, nameShown = wide, onPickKernel = kernel.picker::open, …)
 * ```
 */
@Composable
internal fun KernelIndicator(
  status: KernelStatus,
  /** Whether the row has the width to draw the kernel's name too - see [labelsFitOnActionRow]. */
  nameShown: Boolean,
  /** `kernelName`'s click: `selectKernel` - the picker [NotebookKernelPicker] fills. */
  onPickKernel: () -> Unit = {},
  /**
   * `sessionContext.kernelDisplayName` - the bound spec's `display_name`, or `noKernelName`'s
   * "No Kernel" while none is (`sessioncontext.tsx`). Given rather than derived: the catalogue
   * it resolves through lives a layer up.
   */
  kernelName: String = "No Kernel",
) {
  TextButton(
    onClick = onPickKernel,
    // Neutral, not the brand colour a `TextButton` defaults to - see this file's header.
    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
    // **The name, the state and the click on one node**, which is [UndoRedo]'s rule and
    // load-bearing here for its reason: a `contentDescription` on the child glyph would land one
    // level below the node carrying the click, so a flow asking for the state could not press it.
    // It is also all a narrow bar has to assert on, since the name is not drawn there.
    modifier = Modifier
      .minimumInteractiveComponentSize()
      // `kernelName`'s accessible name is `kernelDisplayName` alone in the reference - "No
      // Kernel" is already the whole of what it has to say, so the status word is not
      // repeated after it.
      .semantics {
        contentDescription =
          if (status == KernelStatus.NO_KERNEL) "No Kernel" else "Kernel $kernelName: ${status.label()}"
      },
  ) {
    KernelStatusGlyph(status)
    if (nameShown) {
      Spacer(modifier = Modifier.width(Spacing.sm))
      // `labelLarge`, Material's own role for a button's text, and [CellTypeControl]'s choice
      // beside it on this row - `TopAppBar` would otherwise hand this slot a document title.
      Text(text = kernelName, style = MaterialTheme.typography.labelLarge)
    }
  }
}

/**
 * The status mark, unnamed to a screen reader on purpose - the button above carries the whole
 * description, so naming the glyph again would give TalkBack two things to say about one control.
 * Same call [NotebookTitleStrip]'s pencil makes.
 */
@Composable
private fun KernelStatusGlyph(status: KernelStatus) {
  Icon(
    imageVector = status.glyph(),
    contentDescription = null,
    modifier = Modifier.size(STATUS_GLYPH_SIZE),
    tint = LocalContentColor.current,
  )
}

/**
 * Which glyph a state draws. **Active states are solid, resting states are outlined**, and that one
 * sentence is the whole rule.
 *
 * **The reference's two components disagree, so this says which it follows and why.** The retired
 * `KernelStatus` item drew `circleIcon` - a solid disc - for busy, starting, restarting,
 * terminating and initializing, `circleEmptyIcon` for idle, and `offlineBoltIcon` for
 * connecting/disconnected/unknown (`packages/apputils/src/toolbar/widget.tsx:213-232`). The
 * `ExecutionIndicator` that replaced it keeps that split for starting and restarting
 * (`packages/notebook/src/executionindicator.tsx:140-151`) but draws busy and idle as a
 * `ProgressCircle` at **0% and 100%** - so its idle is the *filled* one
 * (`:153-188`, `const progress = state.kernelStatus === 'busy' ? 0 : 100`).
 *
 * This follows the first, and the reason is that the second is a **progress meter**: its ring is a
 * stroked circle with a filled pie inside it whose angle is `executed/scheduled`
 * (`packages/statusbar/src/components/progressCircle.tsx`), and 100% reads as "full" only where a
 * percentage is being drawn. This bar draws no percentage - a cell's own header carries where its
 * run got to ([ExecutionLabel]) - so a disc that means "done" beside one that means "working"
 * would be two marks with no scale to read them against. **The cost is named**: a JupyterLab user's
 * eye is trained on the meter's polarity, and here a filled mark means busy rather than finished.
 *
 *
 * The bolt is **outlined where the reference's is filled**, and the weight is the only departure
 * there. `offline-bolt.svg` is Material's filled `offline_bolt` verbatim and every toolbar icon in
 * the reference is the same `jp-icon3` grey (`ui-components/style/icons.css:23`,
 * `theme-light-extension/style/variables.css:231`), so it makes no tonal distinction either. What
 * differs is which state *rests*: JupyterLab opens a session with the document and is practically
 * never here, while this app starts a kernel lazily and so shows this until something is run.
 * Filled beside four outlined glyphs it was the loudest thing in the row - measured off the frame -
 * and a resting state that reads as an alarm is the misread that retired the filled play.
 *
 * A value rather than an inline argument so [KernelStatusGlyphTest] has something to pin: without
 * it, a departure back to the wrong weight is a one-token edit no gate would see. Same call
 * [cellRunIcon] makes.
 */
internal fun KernelStatus.glyph(): ImageVector = when (this) {
  KernelStatus.BUSY, KernelStatus.STARTING, KernelStatus.RESTARTING -> Icons.Filled.Circle
  KernelStatus.IDLE -> Icons.Outlined.Circle
  KernelStatus.NO_KERNEL -> Icons.Outlined.OfflineBolt
}

/**
 * What the state is called, in the reference's own words.
 *
 * Four come from `translateKernelStatuses` (`packages/apputils/src/kernelstatuses.tsx:27-41`) and
 * [KernelStatus.NO_KERNEL] from `noKernelName` (`sessioncontext.tsx:675-677`), which is where the
 * reference keeps that particular word - on the name rather than the status. See [KernelStatus].
 */
private fun KernelStatus.label(): String = when (this) {
  KernelStatus.NO_KERNEL -> "No Kernel"
  KernelStatus.STARTING -> "Starting"
  KernelStatus.BUSY -> "Busy"
  KernelStatus.IDLE -> "Idle"
  KernelStatus.RESTARTING -> "Restarting"
}
