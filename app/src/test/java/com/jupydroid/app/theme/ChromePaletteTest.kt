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
    assertEquals(Color(0xFF2A2B2C), DarkChromePalette.activityBarBorder)
    assertEquals(Color(0xFF2A2B2C), DarkChromePalette.sideBarBorder)
    assertEquals(Color(0xFF191A1B), DarkChromePalette.tabStripBackground)
    assertEquals(Color(0xFF2A2B2C), DarkChromePalette.tabStripBorder)
    assertEquals(Color(0xFF121314), DarkChromePalette.tabActiveBackground)
    assertEquals(Color(0xFF191A1B), DarkChromePalette.tabInactiveBackground)
    assertEquals(Color(0xFF3994BC), DarkChromePalette.tabActiveBorderTop)
    assertEquals(Color(0xFF2A2B2C), DarkChromePalette.tabBorder)
  }

  @Test
  fun lightStatesTheResolvedParts() {
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.activityBarBackground)
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.sideBarBackground)
    // Translucent, and the alpha is the whole point of P7a-1b - see [theLightRowIsAWashAndNotAFill].
    assertEquals(Color(0x99DADADA), LightChromePalette.listInactiveSelectionBackground)
    assertEquals(Color(0xFFF0F1F2), LightChromePalette.activityBarBorder)
    assertEquals(Color(0xFFF0F1F2), LightChromePalette.sideBarBorder)
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.tabStripBackground)
    assertEquals(Color(0xFFF0F1F2), LightChromePalette.tabStripBorder)
    assertEquals(Color(0xFFFFFFFF), LightChromePalette.tabActiveBackground)
    assertEquals(Color(0xFFFAFAFD), LightChromePalette.tabInactiveBackground)
    // Black on a white page is the light theme's own answer for the current tab's top line.
    assertEquals(Color(0xFF000000), LightChromePalette.tabActiveBorderTop)
    assertEquals(Color(0xFFF0F1F2), LightChromePalette.tabBorder)
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
   * **A border is not the part it bounds**, which is the P7a-2 half of [neitherPartIsThePage].
   *
   * The rail and the side panel were drawn as parts by P7a-1 and then abutted the document with
   * nothing between them: `sideBar.border` is stated in both 2026 themes and this app drew it
   * nowhere (`evidence/p7a-2-part-borders/before-the-fix/`, the seam at x662 reading NOT DRAWN).
   * Equality above says each border is the resolved value; this says it is a *separator* - if a
   * border ever collapsed onto the surface it divides, the seam would vanish and every equality
   * assertion here would still hold.
   */
  @Test
  fun neitherBorderIsThePartItSeparates() {
    assertNotEquals(DarkChromePalette.activityBarBackground, DarkChromePalette.activityBarBorder)
    assertNotEquals(DarkChromePalette.sideBarBackground, DarkChromePalette.sideBarBorder)
    assertNotEquals(DarkEditorPalette.editorBackground, DarkChromePalette.sideBarBorder)
    assertNotEquals(LightChromePalette.activityBarBackground, LightChromePalette.activityBarBorder)
    assertNotEquals(LightChromePalette.sideBarBackground, LightChromePalette.sideBarBorder)
    assertNotEquals(LightEditorPalette.editorBackground, LightChromePalette.sideBarBorder)
  }

  /**
   * **The current tab is the page, the others are the strip.** `tab.activeBackground` resolves to
   * `editor.background` and `tab.inactiveBackground` to `editorGroupHeader.tabsBackground` in both
   * default themes - the reference's own way of drawing "this document" vs "still open". JupyterLab
   * states the same relation: `lm-mod-current` wears the main area's `--jp-layout-color1` while its
   * siblings wear `--jp-layout-color2`.
   */
  @Test
  fun theCurrentTabIsThePageAndTheInactiveOnesAreTheStrip() {
    assertEquals(DarkEditorPalette.editorBackground, DarkChromePalette.tabActiveBackground)
    assertEquals(DarkChromePalette.tabStripBackground, DarkChromePalette.tabInactiveBackground)
    assertEquals(LightEditorPalette.editorBackground, LightChromePalette.tabActiveBackground)
    assertEquals(LightChromePalette.tabStripBackground, LightChromePalette.tabInactiveBackground)
  }

  /**
   * **Two ids, and they are kept apart though both themes state them at one hex.**
   *
   * `activityBar.border` *"separating to the side bar"* and `sideBar.border` *"on the side
   * separating to the editor"* (`workbench/common/theme.ts:417,623`) are two registered colours a
   * theme may part; folding them onto one field would make this app unable to follow one that did.
   * The same rule [ChromePalette.sideBarBackground] already carries - and the rule P7a-1b was
   * about, where one role served four consumers and flattened the alpha of the one that had it.
   */
  @Test
  fun theTwoBorderIdsAreSeparateFieldsThoughTheyResolveAlike() {
    assertEquals(DarkChromePalette.activityBarBorder, DarkChromePalette.sideBarBorder)
    assertEquals(LightChromePalette.activityBarBorder, LightChromePalette.sideBarBorder)
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
