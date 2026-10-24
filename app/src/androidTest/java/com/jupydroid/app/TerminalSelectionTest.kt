package com.jupydroid.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import com.jupydroid.app.ui.terminal.TERMINAL_SELECTION_END_TAG
import com.jupydroid.app.ui.terminal.TERMINAL_SELECTION_START_TAG
import com.jupydroid.app.ui.terminal.TerminalCellPosition
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
import com.jupydroid.app.ui.terminal.TerminalPaneViewModel
import com.jupydroid.app.ui.terminal.TerminalSelectionEnd
import com.jupydroid.app.ui.terminal.terminalCellMetrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A long press on the terminal selects the word under it and the platform's floating toolbar
 * offers Copy over it - a question of what is on screen to press, so it is asked of the real
 * screen. The toolbar and the clipboard are Compose locals, recorded here rather than the
 * system's own, which a test cannot read back.
 */
@RunWith(AndroidJUnit4::class)
class TerminalSelectionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val gateway = FakeTerminalGateway()
  private val session = runBlocking { gateway.open() }
  private val viewModel = TerminalPaneViewModel(session, gateway)
  private val toolbar = RecordingTextToolbar()
  private var cell by mutableStateOf<IntSize?>(null)

  @Test
  fun aLongPressOnAWordShowsTheToolbarOverItAndItsCopyPutsTheWordOnTheClipboard() {
    val clipboard = RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java))
    showTheTerminal(clipboard, text = "ls word")

    compose.pressTerminalCell(cell, column = 4) { longClick(it) }

    val shown = requireNotNull(toolbar.shownOver) { "no toolbar was shown" }
    assertTrue("the toolbar's rect $shown is not over column 4", shown.contains(cellCentreInRoot(column = 4)))
    compose.runOnUiThread { requireNotNull(toolbar.onCopy).invoke() }
    compose.waitForIdle()
    assertEquals("word", clipboard.text())
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  /** `TerminalView`'s own way out of a selection: a tap. */
  @Test
  fun aTapEndsTheSelectionAndHidesTheToolbar() {
    showTheTerminal(
      RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java)),
      text = "ls word",
    )
    compose.pressTerminalCell(cell, column = 4) { longClick(it) }
    assertEquals(TextToolbarStatus.Shown, toolbar.status)

    compose.pressTerminalCell(cell, column = 0) { click(it) }
    compose.waitForIdle()

    assertEquals(null, viewModel.selector.selection.value)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  /** `ACTION_PASTE` in `TextSelectionCursorController` (termux-app `3df69d1d`): the selection ends, then the clipboard is pasted. */
  @Test
  fun theToolbarsPasteEndsTheSelectionAndSendsTheClipboardToTheShell() {
    val clipboard = RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java))
    runBlocking { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("test", "pasted"))) }
    showTheTerminal(clipboard, text = "ls word")
    compose.pressTerminalCell(cell, column = 4) { longClick(it) }

    compose.runOnUiThread { requireNotNull(toolbar.onPaste).invoke() }

    compose.waitUntil { firstRow().endsWith("pasted") }
    compose.waitForIdle()
    assertEquals(null, viewModel.selector.selection.value)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun anOsc52PayloadThePaneReceivesLandsOnTheClipboard() {
    val clipboard = RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java))
    showTheTerminal(clipboard, text = "")

    // `FakeTerminalGateway` prints back what it is sent, so this is a program writing OSC 52.
    compose.runOnUiThread { viewModel.type("\u001B]52;c;SGVsbG8=\u0007") }

    compose.waitUntil { clipboard.text() == "Hello" }
  }

  /** `TextSelectionHandleView` (termux-app `3df69d1d`): a handle on each end, and dragging one moves that end. */
  @Test
  fun draggingTheStartHandleLeftWidensWhatCopyTakes() {
    val clipboard = RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java))
    showTheTerminal(clipboard, text = "ls word")
    compose.pressTerminalCell(cell, column = 4) { longClick(it) }
    compose.onNodeWithTag(TERMINAL_SELECTION_END_TAG).assertIsDisplayed()

    val measured = requireNotNull(cell)
    compose.onNodeWithTag(TERMINAL_SELECTION_START_TAG).performTouchInput {
      swipe(center, center - Offset(measured.width * 3f, 0f), durationMillis = 400)
    }
    compose.waitForIdle()
    compose.runOnUiThread { requireNotNull(toolbar.onCopy).invoke() }
    compose.waitForIdle()

    assertEquals("ls word", clipboard.text())
  }

  /**
   * `TextSelectionCursorController.render` (termux-app `3df69d1d`) invalidates the one `ActionMode`
   * the selection started with. A `hide` in between finishes it, and the platform fades a new one in.
   */
  @Test
  fun theToolbarFollowsAMovedSelectionWithoutBeingHidden() {
    showTheTerminal(
      RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java)),
      text = "ls word",
    )
    compose.pressTerminalCell(cell, column = 4) { longClick(it) }
    val before = requireNotNull(toolbar.shownOver) { "no toolbar was shown" }
    val hidesBefore = toolbar.hides

    compose.runOnUiThread {
      viewModel.selector.moveEnd(TerminalSelectionEnd.START, TerminalCellPosition(column = 0, row = 0))
    }
    compose.waitForIdle()

    assertEquals(hidesBefore, toolbar.hides)
    assertTrue("the toolbar stayed over $before", requireNotNull(toolbar.shownOver).left < before.left)
  }

  /** `TerminalView.updateFloatingToolbarVisibility` (termux-app `3df69d1d`): hidden while a handle moves. */
  @Test
  fun aHeldHandleHidesTheToolbarAndReleasingItShowsItAgain() {
    showTheTerminal(
      RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java)),
      text = "ls word",
    )
    compose.pressTerminalCell(cell, column = 4) { longClick(it) }
    val measured = requireNotNull(cell)
    val handle = compose.onNodeWithTag(TERMINAL_SELECTION_END_TAG)

    handle.performTouchInput {
      down(center)
      moveBy(Offset(measured.width * 2f, 0f))
    }
    compose.waitForIdle()
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)

    handle.performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(TextToolbarStatus.Shown, toolbar.status)
  }

  @Test
  fun theHandlesGoAwayWithTheSelection() {
    showTheTerminal(
      RecordingClipboard(compose.activity.getSystemService(ClipboardManager::class.java)),
      text = "ls word",
    )
    compose.pressTerminalCell(cell, column = 4) { longClick(it) }
    compose.onNodeWithTag(TERMINAL_SELECTION_START_TAG).assertIsDisplayed()

    compose.pressTerminalCell(cell, column = 0) { click(it) }

    compose.onNodeWithTag(TERMINAL_SELECTION_START_TAG).assertDoesNotExist()
    compose.onNodeWithTag(TERMINAL_SELECTION_END_TAG).assertDoesNotExist()
  }

  private fun showTheTerminal(clipboard: Clipboard, text: String) {
    compose.setContent {
      JupyterDroidTheme {
        cell = terminalCellMetrics()
        CompositionLocalProvider(LocalClipboard provides clipboard, LocalTextToolbar provides toolbar) {
          TerminalPaneScreen(session = session, terminalGateway = gateway, viewModel = viewModel)
        }
      }
    }
    compose.runOnUiThread {
      val bytes = text.toByteArray()
      viewModel.emulator.append(bytes, bytes.size)
    }
  }

  private fun firstRow(): String =
    viewModel.emulator.screen.grid.rowAt(0).let { String(it.text, 0, it.spaceUsed) }.trimEnd()

  private fun cellCentreInRoot(column: Int): Offset {
    val measured = requireNotNull(cell)
    val field = compose.onNodeWithTag(TERMINAL_INPUT_TAG).getBoundsInRoot()
    val inset = with(compose.density) { Spacing.md.toPx() }
    val origin = with(compose.density) { Offset(field.left.toPx(), field.top.toPx()) }
    return origin + Offset(inset + measured.width * (column + 0.5f), measured.height * 0.5f)
  }
}

/** The floating toolbar, recorded: where it was shown, the Copy it was handed, and how often it was hidden. */
private class RecordingTextToolbar : TextToolbar {
  var shownOver: Rect? = null
  var onCopy: (() -> Unit)? = null
  var onPaste: (() -> Unit)? = null
  var hides = 0
  override var status: TextToolbarStatus = TextToolbarStatus.Hidden

  override fun showMenu(
    rect: Rect,
    onCopyRequested: (() -> Unit)?,
    onPasteRequested: (() -> Unit)?,
    onCutRequested: (() -> Unit)?,
    onSelectAllRequested: (() -> Unit)?,
  ) {
    shownOver = rect
    onCopy = onCopyRequested
    onPaste = onPasteRequested
    status = TextToolbarStatus.Shown
  }

  override fun hide() {
    hides++
    status = TextToolbarStatus.Hidden
  }
}
