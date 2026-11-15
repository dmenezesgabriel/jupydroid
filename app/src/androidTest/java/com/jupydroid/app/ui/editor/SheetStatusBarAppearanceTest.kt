package com.jupydroid.app.ui.editor

import android.view.View
import android.view.WindowInsetsController
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A bottom sheet is a window of its own, so the status bar's icons answer to what *it* sets, not to
 * what `enableEdgeToEdge()` set on the activity (ADR-0094). Left alone, the icons were dark on the
 * dimmed dark theme: 1.07:1 on a phone.
 *
 * The theme is forced by `JupyterDroidTheme(darkTheme = ...)` and not by the device's night mode,
 * because the sheet must follow the app's choice, which is what the Theme row stores.
 */
@RunWith(AndroidJUnit4::class)
class SheetStatusBarAppearanceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun theTableOfContentsSheetOnTheDarkThemeDrawsLightIcons() {
    assertSheetStatusBarIcons(darkTheme = true) { tableOfContentsSheet() }
  }

  @Test
  fun theTableOfContentsSheetOnTheLightThemeDrawsDarkIcons() {
    assertSheetStatusBarIcons(darkTheme = false) { tableOfContentsSheet() }
  }

  @Test
  fun theCellActionSheetOnTheDarkThemeDrawsLightIcons() {
    assertSheetStatusBarIcons(darkTheme = true) { cellActionSheet() }
  }

  @Test
  fun theCellActionSheetOnTheLightThemeDrawsDarkIcons() {
    assertSheetStatusBarIcons(darkTheme = false) { cellActionSheet() }
  }

  private fun assertSheetStatusBarIcons(darkTheme: Boolean, sheet: @Composable () -> Unit) {
    compose.setContent { JupyterDroidTheme(darkTheme = darkTheme, content = sheet) }
    compose.waitForIdle()

    // `APPEARANCE_LIGHT_STATUS_BARS` means a light bar, and so dark icons.
    val wantsDarkIcons = !darkTheme
    assertEquals(
      "status-bar icons under the sheet on the ${if (darkTheme) "dark" else "light"} theme: dark icons?",
      wantsDarkIcons,
      sheetWindowView().windowInsetsController!!.drawsDarkStatusBarIcons(),
    )
  }

  /** The one window the activity does not own: `WindowInspector` lists every root view of the process. */
  private fun sheetWindowView(): View {
    val activityRoot = compose.activity.window.decorView
    val sheets = WindowInspector.getGlobalWindowViews().filter { it !== activityRoot }
    assertEquals("windows other than the activity's: $sheets, expected exactly the sheet", 1, sheets.size)
    return sheets.single()
  }

  private fun WindowInsetsController.drawsDarkStatusBarIcons(): Boolean =
    systemBarsAppearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0

  @Composable
  private fun tableOfContentsSheet() {
    TableOfContentsPanel(
      expanded = true,
      headings = listOf(TocHeading("h1", 1, "Chapter one")),
      onHeadingSelected = {},
      onDismiss = {},
      presentation = CellActionPresentation.SHEET,
    )
  }

  @Composable
  private fun cellActionSheet() {
    CellActions(
      expanded = true,
      cell = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)"),
      canPaste = false,
      onDismiss = {},
      onAction = {},
      presentation = CellActionPresentation.SHEET,
    )
  }
}
