package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Spaces panel: every Space, base first, each named over its id and age, with a bar while an
 * act runs and the port's own refusal when one fails; a Space made and renamed by name, base's
 * name locked.
 *
 * What the ViewModel decides - order, one act at a time, re-listing - is `SpacesViewModelTest`'s.
 * This holds what only a composition can: that the rows, the bar and the refusal reach the screen.
 */
@RunWith(AndroidJUnit4::class)
class SpacesSidePanelTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theBaseSpaceIsListedWithItsIdUnderItsName() {
    show(FakeSpaceRepository())

    compose.onNodeWithText("Spaces").assertIsDisplayed()
    compose.onNodeWithText(BASE_SPACE.name).assertIsDisplayed()
    compose.onNodeWithText("${BASE_SPACE.id} · ", substring = true).assertIsDisplayed()
  }

  /** Below base, in the port's order - the panel draws what it is handed and re-sorts nothing. */
  @Test
  fun aUserSpaceIsListedBelowBase() {
    show(FakeSpaceRepository(listOf(thesis, base)))

    val baseTop = compose.onNodeWithText(BASE_SPACE.name).fetchSemanticsNode().boundsInRoot.top
    val thesisTop = compose.onNodeWithText(THESIS).fetchSemanticsNode().boundsInRoot.top
    assertTrue("base at $baseTop should be above Thesis at $thesisTop", baseTop < thesisTop)
    compose.onNodeWithText("space-1 · ", substring = true).assertIsDisplayed()
  }

  @Test
  fun theBarShowsWhileAnActRunsAndGoesWhenItEnds() {
    val gated = GatedCreateSpaceRepository(FakeSpaceRepository())
    val model = show(gated)

    compose.runOnIdle { model.create(THESIS) }

    compose.onNodeWithContentDescription(SPACES_BUSY).assertIsDisplayed()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription(SPACES_BUSY).fetchSemanticsNodes().isEmpty()
    }
    compose.onNodeWithText(THESIS).assertIsDisplayed()
  }

  /** The port's message verbatim - it names the id at fault, which a generic "failed" would not. */
  @Test
  fun aRefusedActShowsThePortsOwnMessage() {
    val model = show(FakeSpaceRepository())

    compose.runOnIdle { model.delete("space-9") }

    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("'space-9'", substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("'space-9'", substring = true).assertIsDisplayed()
  }

  /** VS Code's rule for its built-in profile: the name is locked, so there is nothing to press. */
  @Test
  fun baseHasNoRenameControlAndAUserSpaceHasOne() {
    show(FakeSpaceRepository(listOf(base, thesis)))

    compose.onAllNodesWithContentDescription("Rename ${BASE_SPACE.name}", substring = true)
      .assertCountEquals(0)
    compose.onNodeWithContentDescription("Rename $THESIS (space-1)").assertIsDisplayed()
  }

  @Test
  fun aSpaceCreatedByNameIsListed() {
    show(FakeSpaceRepository())

    compose.onNodeWithContentDescription(NEW_SPACE).performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput(THESIS)
    compose.onNodeWithText("Create").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(THESIS).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("space-1 · ", substring = true).assertIsDisplayed()
  }

  /** Blank after trimming is the one name refused; a duplicate is allowed, the id tells them apart. */
  @Test
  fun aBlankNameCannotBeCreated() {
    show(FakeSpaceRepository())

    compose.onNodeWithContentDescription(NEW_SPACE).performClick()
    compose.onNodeWithText("Create").assertIsNotEnabled()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("   ")

    compose.onNodeWithText("Create").assertIsNotEnabled()
  }

  @Test
  fun aRenamedSpaceShowsItsNewName() {
    show(FakeSpaceRepository(listOf(base, thesis)))

    compose.onNodeWithContentDescription("Rename $THESIS (space-1)").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextReplacement("Dissertation")
    compose.onNodeWithText("Rename").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("Dissertation").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onAllNodesWithText(THESIS).assertCountEquals(0)
  }

  /** One act at a time: while one runs, the controls that would start another are held. */
  @Test
  fun theActControlsAreHeldWhileAnActRuns() {
    val gated = GatedCreateSpaceRepository(FakeSpaceRepository(listOf(base, thesis)))
    val model = show(gated)

    compose.runOnIdle { model.create("Scratch") }

    compose.onNodeWithContentDescription(NEW_SPACE).assertIsNotEnabled()
    compose.onNodeWithContentDescription("Rename $THESIS (space-1)").assertIsNotEnabled()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { !model.uiState.value.busy }
    compose.onNodeWithContentDescription(NEW_SPACE).assertIsEnabled()
  }

  private fun show(repository: SpaceRepository): SpacesViewModel {
    val model = SpacesViewModel(repository)
    compose.setContent {
      JupyterDroidTheme { SpacesSidePanel(spaceRepository = repository, viewModel = model) }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { model.uiState.value.spaces.isNotEmpty() }
    return model
  }

  private companion object {
    const val THESIS = "Thesis"
    const val TIMEOUT_MILLIS = 5_000L
    val base: SpaceConfig = defaultSpaceConfig(BASE_SPACE.id, Instant.EPOCH)
    val thesis = SpaceConfig(id = "space-1", name = THESIS, created = Instant.EPOCH)
  }
}

/** Holds every `create` until [release] completes, so a test can look at the panel mid-act. */
private class GatedCreateSpaceRepository(private val fake: SpaceRepository) : SpaceRepository by fake {
  val release = CompletableDeferred<Unit>()

  override suspend fun create(name: String): SpaceConfig {
    release.await()
    return fake.create(name)
  }
}
