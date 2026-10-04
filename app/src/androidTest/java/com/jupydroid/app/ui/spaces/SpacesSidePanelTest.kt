package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Spaces panel's list: every Space, base first, each named over its id and age, with a bar
 * while an act runs and the port's own refusal when one fails.
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
