package com.jupydroid.app.ui.editor

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.awaitCancellation

private const val KEYBOARD_REQUEST_TIMEOUT_MS = 5_000L

/**
 * The `inputType` [field] hands the keyboard once tapped, read off the [EditorInfo] through
 * Compose's own interception hook rather than assumed from the options the field wrote.
 *
 * ```
 * val asked = compose.inputTypeAskedBy { CodeCellField(state = rememberTextFieldState(), onSourceEdited = {}) }
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class)
fun ComposeContentTestRule.inputTypeAskedBy(field: @Composable () -> Unit): Int {
  var asked: EditorInfo? = null

  setContent {
    JupyterDroidTheme {
      InterceptPlatformTextInput(
        interceptor = { request, _ ->
          asked = EditorInfo().also { request.createInputConnection(it) }
          awaitCancellation()
        },
      ) { field() }
    }
  }
  onNode(hasSetTextAction()).performClick()
  waitUntil(KEYBOARD_REQUEST_TIMEOUT_MS) { asked != null }
  return requireNotNull(asked).inputType
}
