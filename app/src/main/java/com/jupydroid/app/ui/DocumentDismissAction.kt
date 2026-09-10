package com.jupydroid.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * How a document says how to leave it: back below two panes, close at and above.
 *
 * **The width decides, not the document.** Below two panes the document *is* the window, so leaving
 * it goes back to what was underneath. Beside a browser there is nothing to go back to - the
 * browser is already on screen, and what the arrow would do is empty the document area. That act
 * has a name in both references: `application:close`, labelled *Close Tab*
 * (`application-extension/src/index.tsx:309-310`), and a `Codicon.close` on the tab itself in
 * VS Code. Material draws the same distinction for the navigation slot - an arrow navigates up, a
 * cross dismisses - and only one of those is true of a document sitting beside its browser.
 *
 * **The action is the same either way**, because it is the same act: the detail entry is popped and
 * the browser takes the width. Only what the user is told changes.
 *
 * **One home, three bars.** The notebook's, the plain editor's and the terminal's all draw this;
 * three copies of the branch is three places for it to stop agreeing, which is the defect
 * `ShellSidePane` stopped being handed the width fact twice over for in P7a-2.
 *
 * *"Close Tab"* is not this app's wording: it has no tab strip, so the noun would name something
 * the user cannot see. The reference's own verb is kept and its noun dropped - the same trade
 * `RecentsSection`'s "Recent" makes against *Recently Closed*.
 *
 * @param twoPane whether a browser is drawn beside this document. **A parameter, never measured
 *   here**: `currentWindowAdaptiveInfo()` reads the activity's window, so a
 *   `DeviceConfigurationOverride` cannot pick this branch and a test that tried would drive one arm
 *   twice while appearing to cover both (`PaneDirective.kt:31-38`).
 * @param backDescription where the arrow goes, named by the caller. "Back" alone is the system
 *   navigation bar's own button too, and five Maestro flows once tapped that one instead
 *   (`evidence/u-flow-sweep/`).
 *
 * ```
 * DocumentDismissAction(twoPane, backDescription = "Back to notebooks", onDismiss = onBack)
 * ```
 */
@Composable
fun DocumentDismissAction(twoPane: Boolean, backDescription: String, onDismiss: () -> Unit) {
  IconButton(onClick = onDismiss) {
    if (twoPane) {
      Icon(Icons.Filled.Close, contentDescription = CLOSE_DOCUMENT)
    } else {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
    }
  }
}

/**
 * What the close arm answers to.
 *
 * `application:close`'s label with its noun dropped - see above. Named once here because three bars
 * draw it and every flow that closes a document has to address the same string.
 */
const val CLOSE_DOCUMENT = "Close"
