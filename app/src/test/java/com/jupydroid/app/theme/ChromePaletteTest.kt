package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The workbench's **parts** are stated colours, and the page is not one of them.
 *
 * Written against a measurement rather than a suspicion. Reading
 * `evidence/p2u-1-chrome/after-tablet/dark/01-the-explorer.png` along y=900, the activity rail
 * (x 0-119) and the file explorer (x 122-699) are both `#121314` - `editor.background` - with only
 * the 1px seam at x=120 between them. The reference draws those two parts a step off the page and
 * the page itself under the document alone.
 *
 * **`ColorRamp` already claimed this and nothing made it true.** `DarkGray2`'s own KDoc reads
 * *"`sideBar.background` - the explorer and the rail, one step up from the page"*, which P2u-1
 * wrote while mapping it onto `surfaceContainer`; no composable ever asked for that role, so the
 * sentence has been false since it was written.
 *
 * **Why a palette and not `colorScheme.surfaceContainer`, which holds the same hex today.**
 * `ColorSchemeTest` asserts only that a role is *somewhere* on the ramp, never which position - so
 * `surfaceContainer` could drift onto `menu.background` and every test would stay green while the
 * rail moved. `docs/DECISIONS.md`, "The palette states its colours; it does not derive them from a
 * Material role", is the rule that anticipates exactly this: *a colour a role could supply is a
 * colour no test can hold.*
 */
class ChromePaletteTest {
  @Test
  fun darkStatesTheResolvedParts() {
    assertEquals(Color(0xFF191A1B), DarkChromePalette.activityBarBackground)
    assertEquals(Color(0xFF191A1B), DarkChromePalette.sideBarBackground)
  }

  @Test
  fun lightStatesTheResolvedParts() {
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.activityBarBackground)
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.sideBarBackground)
  }

  /**
   * **The defect itself, pinned from the other side.** Equality above says each part holds the
   * value the reference resolves; this says the two parts are not the page - which is the thing
   * the screenshot showed and no assertion in this repository could fail on.
   */
  @Test
  fun neitherPartIsThePage() {
    assertNotEquals(DarkEditorPalette.editorBackground, DarkChromePalette.sideBarBackground)
    assertNotEquals(DarkEditorPalette.editorBackground, DarkChromePalette.activityBarBackground)
    assertNotEquals(LightEditorPalette.editorBackground, LightChromePalette.sideBarBackground)
    assertNotEquals(LightEditorPalette.editorBackground, LightChromePalette.activityBarBackground)
  }
}
