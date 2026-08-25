package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search panel as P2x-2 leaves it: named, and nothing else.
 *
 * The name is the whole assertion on purpose - it is what the rail's third item lands on and what
 * tells the panel apart from the file browser beside it. P2x-3 brings the query box, the option
 * toggles and the results tree, and their tests come with them.
 */
@RunWith(AndroidJUnit4::class)
class SearchSidePanelTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun thePanelNamesItself() {
    compose.setContent { JupyterDroidTheme { SearchSidePanel() } }

    compose.onNodeWithText("Search").assertIsDisplayed()
  }
}
