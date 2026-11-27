package com.jupydroid.app.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.orderedAsOpened
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What sits under the tab strip pads the status bar's inset **once**.
 *
 * The strip takes the inset itself, so the document below it must start where the strip ends. A
 * document that reads the inset again draws a band of the status bar's own height between them -
 * 30dp on the tablet, 24dp on the phone - which no other test here sees, because a test
 * environment has no system bars until one is dispatched, as [STATUS_BAR_PIXELS] does.
 *
 * Two shapes stand for every screen: a `Scaffold` with no top bar, which reads the inset for its
 * content (`SettingsScreen`), and a bar that pads its own inset (`EditorTopBar`).
 */
@RunWith(AndroidJUnit4::class)
class TabbedDocumentPaneInsetsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun aScaffoldBelowTheStripAddsNoSecondStatusBarInset() {
    mountUnderTheStrip {
      Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().testTag(DOCUMENT_TAG))
      }
    }

    assertTheDocumentStartsWhereTheStripEnds()
  }

  @Test
  fun aBarThatPadsItsOwnInsetBelowTheStripAddsNoSecondStatusBarInset() {
    mountUnderTheStrip {
      Column(Modifier.fillMaxSize()) {
        Box(
          Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets).fillMaxWidth().height(BAR_HEIGHT_DP.dp)
            .testTag(DOCUMENT_TAG),
        )
      }
    }

    assertTheDocumentStartsWhereTheStripEnds()
  }

  private fun mountUnderTheStrip(document: @Composable () -> Unit) {
    val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("a.ipynb"))
    val open = OpenDocuments()
    val paths = mutableStateMapOf<NavKey, String>()
    compose.setContent {
      JupydroidTheme {
        val order = remember { mutableStateListOf<NavKey>() }
        val tabs = backStack.mainAreaTabs().orderedAsOpened(order)
        LaunchedEffect(tabs) {
          order.clear()
          order.addAll(tabs)
        }
        val closeTab = rememberMainAreaCloseGate(backStack, tabs, open, paths).closeTab
        TabbedDocumentPane(
          backStack, open, paths, tabs, emptyMap(), FakeWorkspaceRepository(), FakeWorkspaceFiles(),
          twoPane = true, closeTab, onOpenDocument = {}, documentCheckpoints = FakeDocumentCheckpoints(),
          content = { document() },
        )
      }
    }
    dispatchAStatusBar()
  }

  /** `enableEdgeToEdge` is what `MainActivity` does, and without it the decor view consumes the bar. */
  private fun dispatchAStatusBar() {
    compose.runOnUiThread {
      WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
      val statusBar = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PIXELS, 0, 0))
        .setVisible(WindowInsetsCompat.Type.statusBars(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, statusBar)
    }
    compose.waitForIdle()
  }

  private fun assertTheDocumentStartsWhereTheStripEnds() {
    val strip = compose.onNodeWithContentDescription("Tab strip").fetchSemanticsNode().boundsInRoot
    val document = compose.onNodeWithTag(DOCUMENT_TAG).fetchSemanticsNode().boundsInRoot
    // The strip's semantics sit inside its own padding, so its top is where the inset ends.
    assertTrue(
      "the strip must start below the status bar's $STATUS_BAR_PIXELS px or this measures nothing: $strip",
      strip.top >= STATUS_BAR_PIXELS,
    )
    assertEquals(
      "the document must start at the strip's bottom ${strip.bottom}, and starts at ${document.top}",
      strip.bottom,
      document.top,
      1f,
    )
  }

  private companion object {
    const val DOCUMENT_TAG = "document under the strip"
    const val STATUS_BAR_PIXELS = 90
    const val BAR_HEIGHT_DP = 40
  }
}
