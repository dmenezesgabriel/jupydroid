package com.jupydroid.app.ui.editor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard to type source verbatim on every text field in [content], because source is not
 * prose. Clearing autocorrect is not enough: on SM-X216B, Gboard split a typed `import getpass` into
 * `import get pass` with `TYPE_TEXT_FLAG_AUTO_CORRECT` already cleared. Android code editors ask for
 * the same pair this does - Acode's default `keyboardMode`
 * (Acode-Foundation/Acode@644df30a `hooks/modify-java-files.js`, `src/lib/settings.js`) and
 * termux-app's `TerminalView.onCreateInputConnection` for keyboards that ignore `TYPE_NULL`.
 *
 * `KeyboardOptions` has no lever for the flag, and its `KeyboardType.Password` also makes the field a
 * password one, which turns off copy and cut. So the flag is added to the `EditorInfo` Compose
 * already filled in, which is what Compose's own `InterceptPlatformTextInputSample` shows
 * (androidx@5a2352ff `compose/ui/ui/samples/.../PlatformTextInputModifierNodeSample.kt`).
 *
 * ```
 * SourceKeyboard { BasicTextField(state) }
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SourceKeyboard(content: @Composable () -> Unit) {
  InterceptPlatformTextInput(
    interceptor = { request, next -> next.startInputMethod(SourceInputRequest(request)) },
    content = content,
  )
}

private class SourceInputRequest(private val request: PlatformTextInputMethodRequest) :
  PlatformTextInputMethodRequest {
  override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
    val connection = request.createInputConnection(outAttributes)
    val variationless = outAttributes.inputType and InputType.TYPE_MASK_VARIATION.inv()
    outAttributes.inputType = variationless or SOURCE_INPUT_FLAGS
    return connection
  }
}

// NO_SUGGESTIONS alone was measured not to be enough: Gboard on SM-X216B still split `getpass` with
// the flag set (inputType 0xa0001). The visible-password variation is what Acode defaults to and
// termux-app falls back to for keyboards that ignore the flag. It is not Compose's
// `KeyboardType.Password`, so the field is no password field: copy, cut and the text stay as they are.
private const val SOURCE_INPUT_FLAGS =
  InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
