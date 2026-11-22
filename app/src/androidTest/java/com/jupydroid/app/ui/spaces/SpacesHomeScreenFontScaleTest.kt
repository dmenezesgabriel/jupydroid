package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeHarnessScripts
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.PHONE_WIDTH
import com.jupydroid.app.ui.breaksInsideAWord
import com.jupydroid.app.ui.isCut
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * What a Space's row does at the narrowest phone under a large font scale: its status word and its
 * id stay whole words, and every control stays inside the screen. The row's behaviour at the default
 * size is `SpacesHomeScreenTest`'s.
 *
 * A row holds up to five fixed-width controls, so at 320 dp and twice the font scale they need nearly
 * the whole width; a name column left with the remainder broke "Stopped" inside its word.
 */
@RunWith(AndroidJUnit4::class)
class SpacesHomeScreenFontScaleTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun everyRowKeepsItsStatusWordWholeOnAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt(fontScale = 2f, listOf(base, thesis))

    assertEveryRowKeepsWhole(hasText(STOPPED))
  }

  @Test
  fun everyRowKeepsItsIdWholeOnAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt(fontScale = 2f, listOf(base, thesis))

    assertEveryRowKeepsWhole(hasText(ID_AND_AGE_SEPARATOR, substring = true))
  }

  @Test
  fun everyControlStaysInsideAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt(fontScale = 2f, listOf(base, thesis))

    val screenWidth = with(compose.density) { PHONE_WIDTH.toPx() }
    CONTROLS.forEach { name ->
      val bounds = boundsOfControl(name)
      val inside = bounds.left >= 0f && bounds.right <= screenWidth
      assertTrue("$name at $bounds leaves the 0..$screenWidth px screen", inside)
    }
  }

  @Test
  fun aBaseRowKeepsItsControlsBesideItsNameAtTheDefaultFontScale() {
    showOnAPhoneAt(fontScale = 1f, listOf(base))

    val name = compose.onNodeWithText(BASE_SPACE.name).fetchSemanticsNode().boundsInRoot
    val run = boundsOfControl("Run ${BASE_SPACE.name} (${BASE_SPACE.id})")
    assertTrue("Run at $run should sit beside the name at $name", run.center.y in name.top..name.bottom)
    assertTrue("Run at $run should start right of the name at $name", run.left >= name.right)
  }

  /**
   * An unbroken name is cut with an ellipsis; it must not be what moves the controls off its line.
   * At 412 dp, where a user Space's five controls fit beside any text.
   */
  @Test
  fun aLongNameIsCutBesideItsControlsOnACommonPhoneAtTheDefaultFontScale() {
    showOnAPhoneAt(fontScale = 1f, listOf(base, longNamed), width = COMMON_PHONE_WIDTH)

    val name = compose.onNodeWithText(LONG_NAME).fetchSemanticsNode().boundsInRoot
    val run = boundsOfControl("Run $LONG_NAME (space-2)")
    assertTrue("Run at $run should sit beside the name at $name", run.center.y in name.top..name.bottom)
    assertTrue("Run at $run should start right of the name at $name", run.left >= name.right)
  }

  private fun showOnAPhoneAt(fontScale: Float, spaces: List<SpaceConfig>, width: Dp = PHONE_WIDTH) {
    val repository = FakeSpaceRepository(spaces)
    val servers = FakeSpaceServers()
    val environments = FakeSpaceEnvironments(spaces.associate { it.id to emptyList() })
    val kernelSpecs = FakeKernelSpecs()
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    compose.setContent {
      JupydroidTheme {
        Box(Modifier.requiredWidth(width)) {
          AtFontScale(fontScale) {
            SpacesHomeScreen(
              repository,
              servers,
              environments,
              kernelSpecs,
              spaceShell = FakeSpaceShell(),
              harnessScripts = FakeHarnessScripts(),
              onOpen = {},
              viewModel = model,
            )
          }
        }
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { model.uiState.value.spaces.size == spaces.size }
  }

  /** The text nodes of the unmerged tree: a row merges its descendants, so only they carry a text layout. */
  private fun assertEveryRowKeepsWhole(matcher: SemanticsMatcher) {
    val nodes = compose.onAllNodes(matcher, useUnmergedTree = true)
    nodes.assertCountEquals(ROWS)
    (0 until ROWS).forEach { row ->
      assertFalse("row $row is cut", nodes[row].isCut())
      assertFalse("row $row breaks inside a word", nodes[row].breaksInsideAWord())
    }
  }

  private fun boundsOfControl(name: String): Rect =
    compose.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val ROWS = 2
    val COMMON_PHONE_WIDTH = 412.dp
    const val ID_AND_AGE_SEPARATOR = " · "
    const val THESIS = "Thesis"
    const val LONG_NAME = "a-name-with-no-space-in-it-that-is-far-wider-than-a-phone"
    val base: SpaceConfig = defaultSpaceConfig(BASE_SPACE.id, Instant.EPOCH)
    val thesis = SpaceConfig(id = "space-1", name = THESIS, created = Instant.EPOCH)
    val longNamed = SpaceConfig(id = "space-2", name = LONG_NAME, created = Instant.EPOCH)
    val CONTROLS = listOf(
      "Run ${BASE_SPACE.name} (${BASE_SPACE.id})",
      "Open ${BASE_SPACE.name} (${BASE_SPACE.id})",
      "Settings ${BASE_SPACE.name} (${BASE_SPACE.id})",
      "Run $THESIS (space-1)",
      "Open $THESIS (space-1)",
      "Settings $THESIS (space-1)",
      "Rename $THESIS (space-1)",
      "Delete $THESIS (space-1)",
    )
  }
}
