package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    assertEquals(Color(0xFF2C2D2E), DarkChromePalette.listInactiveSelectionBackground)
  }

  @Test
  fun lightStatesTheResolvedParts() {
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.activityBarBackground)
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.sideBarBackground)
    // Translucent, and the alpha is the whole point of P7a-1b - see [theLightRowIsAWashAndNotAFill].
    assertEquals(Color(0x99DADADA), LightChromePalette.listInactiveSelectionBackground)
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

  /**
   * **2026 Light states this row translucent, and the ramp used to flatten it.**
   *
   * `list.inactiveSelectionBackground` resolves `#DADADA99` - 60% - where the ramp held an opaque
   * `#DADADA` (`ColorRamp.kt:96` before this step). So the app drew a fill where the reference
   * draws a wash, and no test could fail on it: `ColorSchemeTest` asserts only that a role is
   * *somewhere* on the ramp.
   *
   * **Measured after compositing, which is the only value a pixel ever takes** - the rule P2u-3c
   * had to adopt for dark's 70%-opaque `focusBorder`. Over the side bar the row paints `#E7E7E8`,
   * and that is the number `check_node_fill.py` holds the device frame to, so the constant here and
   * the pixel there cannot drift apart.
   *
   * Dark's own value is opaque in the reference and is pinned by equality above; there is no
   * composite to take.
   */
  @Test
  fun theLightRowIsAWashAndNotAFill() {
    val painted = LightChromePalette.listInactiveSelectionBackground
      .compositeOver(LightChromePalette.sideBarBackground)
    assertEquals(0xFFE7E7E8.toInt(), painted.toArgb())
  }
}
