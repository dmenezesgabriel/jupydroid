package com.jupydroid.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.DocumentDismissAction

/** [SettingsScreen]'s own root, so a test can find it without depending on its title text. */
const val SETTINGS_SCREEN_TAG = "settings_screen"

/**
 * The app-wide Settings screen - P6c-1's own skeleton, empty until P6c-2 gives it its first rows.
 *
 * Reached by [openSettings][com.jupydroid.app.openSettings], stacked over whatever document is
 * open rather than replacing it - see that function's own doc comment.
 *
 * ```
 * SettingsScreen(onBack = { backStack.removeLastOrNull() })
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, twoPane: Boolean = false, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier.fillMaxSize().testTag(SETTINGS_SCREEN_TAG),
    // The document's own background, not a Material role - every other document screen in this
    // tree already follows this rule (`docs/DECISIONS.md`).
    containerColor = editorPalette.editorBackground,
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          DocumentDismissAction(twoPane = twoPane, backDescription = "Back to files", onDismiss = onBack)
        },
      )
    },
  ) { padding ->
    Box(modifier = Modifier.padding(padding).fillMaxSize())
  }
}
