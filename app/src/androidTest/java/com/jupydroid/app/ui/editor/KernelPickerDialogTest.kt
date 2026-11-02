package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.KernelPickGroup
import com.jupydroid.app.domain.model.KernelPickOption
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `selectKernel` dialog (`packages/apputils/src/sessioncontext.tsx:1398-1460`): a grouped
 * list - the `<select>`'s `<optgroup>`s as section headers - with its Select and Cancel buttons.
 *
 * Instrumented rather than JVM because the rows are what is being asserted: that each option is
 * a tappable row its text names, and that the row the session already sits on is the marked one.
 */
@RunWith(AndroidJUnit4::class)
class KernelPickerDialogTest {
  @get:Rule val compose = createComposeRule()

  /** The group's `<optgroup label>` is drawn, and the rows under it are the options' texts. */
  @Test
  fun theGroupsAndTheirRowsAreListed() {
    showDialog()

    compose.onNodeWithText("Start python Kernel").assertIsDisplayed()
    compose.onNodeWithText("Python 3").assertIsDisplayed()
    compose.onNodeWithText("Use No Kernel").assertIsDisplayed()
    // The row and - unbound - the relabeled cancel button both read "No Kernel"; the row is the
    // one that is *selectable*.
    compose.onNode(noKernelRow).assertIsDisplayed()
    compose.onNodeWithText("Start Kernel").assertIsDisplayed()
  }

  /** `Select kernel for: "<name>"` - the label over the `<select>` names the file (`:1873`). */
  @Test
  fun theDialogNamesTheDocumentItIsChoosingFor() {
    showDialog()

    compose.onNodeWithText("Select Kernel").assertIsDisplayed()
    compose.onNodeWithText("Select kernel for: \"Data.ipynb\"").assertIsDisplayed()
  }

  /** The session's own kernel is the marked row - `selected` on its `<option>`. */
  @Test
  fun theKernelAlreadyBoundIsThePickedRow() {
    showDialog(bound = true)

    compose.onNodeWithText("Python 3").assertIsSelected()
  }

  /** *Select* hands the tapped row's choice to `changeKernel` - a spec row starts that kernel. */
  @Test
  fun aPickedSpecIsWhatSelectAnswers() {
    var picked: KernelChoice? = KernelChoice.Running("unset")
    showDialog(onPick = { picked = it })

    compose.onNodeWithText("Python 3").performClick()
    compose.onNodeWithText("Select").performClick()

    assertEquals(KernelChoice.Spec("python3"), picked)
  }

  /** The "No Kernel" row's value is `JSON.stringify(null)` - null is what Select answers. */
  @Test
  fun pickingNoKernelAnswersNull() {
    var picked: KernelChoice? = KernelChoice.Spec("unset")
    showDialog(onPick = { picked = it })

    compose.onNode(noKernelRow).performClick()
    compose.onNodeWithText("Select").performClick()

    assertEquals(null, picked)
  }

  /** With nothing bound the cancel button is the display name - `hasNoKernel` relabels it (`:1407-1410`). */
  @Test
  fun unboundSaysNoKernelInsteadOfCancel() {
    var dismissed = false
    showDialog(bound = false, onDismiss = { dismissed = true })

    // The relabeled cancel button, not the row: `hasClickAction` without `isSelectable`.
    compose.onNode(hasText("No Kernel") and hasClickAction() and isSelectable().not())
      .performClick()

    assertTrue(dismissed)
  }

  @Test
  fun boundKeepsTheCancelLabel() {
    showDialog(bound = true)

    compose.onNodeWithText("Cancel").assertIsDisplayed()
  }

  /**
   * Regression (kernel-picker-tablet tablet run): a fixed-height scroll Column clipped its last
   * row against the dialog's text slot - the running-kernel row rendered on screen but was
   * flagged invisible, so neither touch drags nor a11y scrolls could reach it. The list must
   * be a lazy list whose scroll actions bring any option into the viewport.
   */
  @Test
  fun aRowBeyondTheViewportScrollsIntoReach() {
    showDialog(groups = GROUPS + deepGroup())

    compose.onNodeWithTag(KERNEL_PICKER_LIST_TAG).performScrollToNode(hasText("Kernel 19"))

    compose.onNodeWithText("Kernel 19").assertIsDisplayed()
  }

  private fun showDialog(
    bound: Boolean = false,
    groups: List<KernelPickGroup> = GROUPS,
    onPick: (KernelChoice?) -> Unit = {},
    onDismiss: () -> Unit = {},
  ) {
    compose.setContent {
      JupyterDroidTheme {
        KernelPickerDialog(
          groups = groups,
          notebookTitle = "Data.ipynb",
          noKernel = !bound,
          kernelDisplayName = if (bound) "Python 3" else "No Kernel",
          onPick = onPick,
          onDismiss = onDismiss,
        )
      }
    }
  }

  private companion object {
    /** The "No Kernel" *row*, told apart from the same-labelled cancel button by being selectable. */
    val noKernelRow = hasText("No Kernel") and isSelectable()

    /** Twenty running sessions under one group - deep enough to overflow any dialog height. */
    fun deepGroup(): KernelPickGroup = KernelPickGroup(
      "Connect to Existing Kernel",
      (0..19).map { KernelPickOption("Kernel $it", choice = KernelChoice.Running("id-$it")) },
    )

    val GROUPS = listOf(
      KernelPickGroup(
        "Start python Kernel",
        listOf(KernelPickOption(text = "Python 3", choice = KernelChoice.Spec("python3"), selected = true)),
      ),
      KernelPickGroup("Use No Kernel", listOf(KernelPickOption(text = "No Kernel", choice = null))),
      KernelPickGroup(
        "Start Kernel",
        listOf(KernelPickOption(text = "P8g Julia", choice = KernelChoice.Spec("p8g"))),
      ),
    )
  }
}
