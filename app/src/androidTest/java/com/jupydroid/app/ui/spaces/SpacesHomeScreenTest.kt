package com.jupydroid.app.ui.spaces

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
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
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.MAX_IDLE_SHUTDOWN_MINUTES
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Spaces home: every Space, base first, each named over its id and age, with a bar while an
 * act runs and the port's own refusal when one fails; a Space made and renamed by name, deleted
 * only after asking, base's name and existence locked, and only base open to enter. Every row reads
 * Running or Stopped and offers the one of Run and Stop that applies.
 *
 * What the ViewModel decides - order, one act at a time, re-listing - is `SpacesViewModelTest`'s.
 * This holds what only a composition can: that the rows, the bar and the refusal reach the screen.
 */
@RunWith(AndroidJUnit4::class)
class SpacesHomeScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theBaseSpaceIsListedWithItsIdUnderItsName() {
    show(FakeSpaceRepository())

    compose.onNodeWithText("Spaces").assertIsDisplayed()
    compose.onNodeWithText(BASE_SPACE.name).assertIsDisplayed()
    compose.onNodeWithText("${BASE_SPACE.id} · ", substring = true).assertIsDisplayed()
  }

  /** Below base, in the port's order - the home draws what it is handed and re-sorts nothing. */
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
    val gated = GatedSpaceRepository(FakeSpaceRepository())
    val model = show(gated)

    compose.runOnIdle { model.create(THESIS) }

    compose.onNodeWithContentDescription(SPACES_BUSY).assertIsDisplayed()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription(SPACES_BUSY).fetchSemanticsNodes().isEmpty()
    }
    compose.onNodeWithText(THESIS).assertIsDisplayed()
  }

  /**
   * A create is a ~14 s image copy, so its bar is determinate: the semantics carry the port's
   * fraction, which is what a flow can now catch that the old record-only create never drew.
   */
  @Test
  fun theBarShowsTheCopysFraction() {
    val copying = CopyingSpaceRepository(FakeSpaceRepository())
    val model = show(copying)

    compose.runOnIdle { model.create(THESIS) }

    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f))).assertIsDisplayed()
    copying.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(THESIS).fetchSemanticsNodes().isNotEmpty() }
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
    val gated = GatedSpaceRepository(FakeSpaceRepository(listOf(base, thesis)))
    val model = show(gated)

    compose.runOnIdle { model.create("Scratch") }

    compose.onNodeWithContentDescription(NEW_SPACE).assertIsNotEnabled()
    compose.onNodeWithContentDescription("Rename $THESIS (space-1)").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Delete $THESIS (space-1)").assertIsNotEnabled()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { !model.uiState.value.busy }
    compose.onNodeWithContentDescription(NEW_SPACE).assertIsEnabled()
  }

  /** A Space is entered only running, so each row's Open follows its own Running and no other's. */
  @Test
  fun openIsEnabledExactlyWhileTheRowIsRunning() {
    show(FakeSpaceRepository(listOf(base, thesis)))
    compose.onNodeWithContentDescription(openBase).assertIsNotEnabled()
    compose.onNodeWithContentDescription(openThesis).assertIsNotEnabled()

    compose.onNodeWithContentDescription("Run $THESIS (space-1)").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(RUNNING).fetchSemanticsNodes().size == 1 }
    compose.onNodeWithContentDescription(openThesis).assertIsEnabled()
    compose.onNodeWithContentDescription(openBase).assertIsNotEnabled()
  }

  @Test
  fun openingARunningSpaceEntersItById() {
    val servers = FakeSpaceServers()
    runBlocking { servers.run("space-1") }
    val opened = mutableListOf<String>()
    show(FakeSpaceRepository(listOf(base, thesis)), servers, onOpen = { opened += it })

    compose.onNodeWithContentDescription(openThesis).performClick()

    compose.runOnIdle { assertEquals(listOf("space-1"), opened) }
  }

  /** Entering a Space mid-act would leave the act's result unseen, so Open is held with the rest. */
  @Test
  fun openIsHeldWhileAnActRuns() {
    val gated = GatedSpaceRepository(FakeSpaceRepository())
    val servers = FakeSpaceServers()
    runBlocking { servers.run(BASE_SPACE.id) }
    val model = show(gated, servers)

    compose.runOnIdle { model.create(THESIS) }

    compose.onNodeWithContentDescription(openBase).assertIsNotEnabled()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { !model.uiState.value.busy }
    compose.onNodeWithContentDescription(openBase).assertIsEnabled()
  }

  /** The port refuses to delete base; the screen does not offer it, as VS Code does not. */
  @Test
  fun baseHasNoDeleteControlAndAUserSpaceHasOne() {
    show(FakeSpaceRepository(listOf(base, thesis)))

    compose.onAllNodesWithContentDescription("Delete ${BASE_SPACE.name}", substring = true)
      .assertCountEquals(0)
    compose.onNodeWithContentDescription("Delete $THESIS (space-1)").assertIsDisplayed()
  }

  /** The question names the id as well, so two Spaces called Thesis cannot be confused in it. */
  @Test
  fun cancellingADeleteKeepsTheSpace() {
    val repository = FakeSpaceRepository(listOf(base, thesis))
    show(repository)

    compose.onNodeWithContentDescription("Delete $THESIS (space-1)").performClick()
    compose.onNodeWithText("Are you sure you want to permanently delete: $THESIS (space-1)?").assertIsDisplayed()
    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText(THESIS).assertIsDisplayed()
    assertEquals(listOf(BASE_SPACE.id, "space-1"), runBlocking { repository.spaces() }.map { it.id })
  }

  @Test
  fun aConfirmedDeleteShowsProgressThenRemovesTheRow() {
    val gated = GatedSpaceRepository(FakeSpaceRepository(listOf(base, thesis)))
    show(gated)

    compose.onNodeWithContentDescription("Delete $THESIS (space-1)").performClick()
    confirmDelete()

    compose.onNodeWithContentDescription(SPACES_BUSY).assertIsDisplayed()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(THESIS).fetchSemanticsNodes().isEmpty() }
    compose.onNodeWithText(BASE_SPACE.name).assertIsDisplayed()
  }

  @Test
  fun everyRowReadsStoppedOnAColdStart() {
    show(FakeSpaceRepository(listOf(base, thesis)))

    compose.onAllNodesWithText(STOPPED).assertCountEquals(2)
    compose.onNodeWithContentDescription(runBase).assertIsDisplayed()
    compose.onNodeWithContentDescription("Run $THESIS (space-1)").assertIsDisplayed()
  }

  @Test
  fun runFlipsTheRowToRunningAndOffersStop() {
    show(FakeSpaceRepository(listOf(base, thesis)))

    compose.onNodeWithContentDescription(runBase).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(RUNNING).fetchSemanticsNodes().size == 1 }
    compose.onNodeWithContentDescription(stopBase).assertIsDisplayed()
    compose.onNodeWithContentDescription("Run $THESIS (space-1)").assertIsDisplayed()
  }

  @Test
  fun stopFlipsTheRowBackToStopped() {
    val servers = FakeSpaceServers()
    runBlocking { servers.run(BASE_SPACE.id) }
    show(FakeSpaceRepository(), servers)

    compose.onNodeWithContentDescription(stopBase).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(STOPPED).fetchSemanticsNodes().size == 1 }
    compose.onNodeWithContentDescription(runBase).assertIsDisplayed()
  }

  /** Run is an act like the rest, and a server takes seconds to boot - so it is held with them. */
  @Test
  fun runIsHeldWhileAnActRuns() {
    val gated = GatedSpaceRepository(FakeSpaceRepository())
    val model = show(gated)

    compose.runOnIdle { model.create(THESIS) }

    compose.onNodeWithContentDescription(runBase).assertIsNotEnabled()
    gated.release.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { !model.uiState.value.busy }
    compose.onNodeWithContentDescription(runBase).assertIsEnabled()
  }

  /**
   * Settings is the mirror of Open: offered only while the row is stopped, base included, since
   * nothing is applied to a running process.
   */
  @Test
  fun settingsIsEnabledExactlyWhileTheRowIsStopped() {
    val servers = FakeSpaceServers()
    runBlocking { servers.run(BASE_SPACE.id) }
    show(FakeSpaceRepository(listOf(base, thesis)), servers)

    compose.onNodeWithContentDescription(settingsBase).assertIsNotEnabled()
    compose.onNodeWithContentDescription(settingsThesis).assertIsEnabled()
  }

  @Test
  fun theSettingsDialogShowsTheStoredIdleShutdownAndSavesTheNewOne() {
    val repository = FakeSpaceRepository(listOf(base, thesis.copy(settings = SpaceSettings(idleShutdownMinutes = 7))))
    show(repository)

    compose.onNodeWithContentDescription(settingsThesis).performClick()
    compose.onNodeWithTag(IDLE_SHUTDOWN_FIELD_TAG).assertTextContains("7")
    compose.onNodeWithTag(IDLE_SHUTDOWN_FIELD_TAG).performTextReplacement("2")
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) {
      runBlocking { repository.spaces().last().settings.idleShutdownMinutes == 2 }
    }
  }

  /** What the port would refuse cannot be saved: past a day, below zero, or not a number at all. */
  @Test
  fun anIdleShutdownOutsideTheRangeCannotBeSaved() {
    show(FakeSpaceRepository(listOf(base, thesis)))
    compose.onNodeWithContentDescription(settingsThesis).performClick()

    listOf("${MAX_IDLE_SHUTDOWN_MINUTES + 1}", "-1", "", "two").forEach { typed ->
      compose.onNodeWithTag(IDLE_SHUTDOWN_FIELD_TAG).performTextReplacement(typed)
      compose.onNodeWithText(SAVE_SETTINGS).assertIsNotEnabled()
    }
    compose.onNodeWithTag(IDLE_SHUTDOWN_FIELD_TAG).performTextReplacement("0")
    compose.onNodeWithText(SAVE_SETTINGS).assertIsEnabled()
  }

  /** The dialog's button, not its title - both say "Delete" (`ExplorerScreenTest.confirmDelete`). */
  private fun confirmDelete() = compose.onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()

  private fun show(
    repository: SpaceRepository,
    servers: FakeSpaceServers = FakeSpaceServers(),
    onOpen: (String) -> Unit = {},
  ): SpacesViewModel {
    val model = SpacesViewModel(repository, servers)
    compose.setContent {
      JupyterDroidTheme {
        SpacesHomeScreen(spaceRepository = repository, spaceServers = servers, onOpen = onOpen, viewModel = model)
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { model.uiState.value.spaces.isNotEmpty() }
    return model
  }

  private companion object {
    const val THESIS = "Thesis"
    val openBase = "Open ${BASE_SPACE.name} (${BASE_SPACE.id})"
    val openThesis = "Open $THESIS (space-1)"
    val runBase = "Run ${BASE_SPACE.name} (${BASE_SPACE.id})"
    val stopBase = "Stop ${BASE_SPACE.name} (${BASE_SPACE.id})"
    val settingsBase = "Settings ${BASE_SPACE.name} (${BASE_SPACE.id})"
    val settingsThesis = "Settings $THESIS (space-1)"
    const val TIMEOUT_MILLIS = 5_000L
    val base: SpaceConfig = defaultSpaceConfig(BASE_SPACE.id, Instant.EPOCH)
    val thesis = SpaceConfig(id = "space-1", name = THESIS, created = Instant.EPOCH)
  }
}

/** Holds every `create` and `delete` until [release] completes, so a test can look at the home mid-act. */
private class GatedSpaceRepository(private val fake: SpaceRepository) : SpaceRepository by fake {
  val release = CompletableDeferred<Unit>()

  override suspend fun create(name: String, onProgress: suspend (Float) -> Unit): SpaceConfig {
    release.await()
    return fake.create(name, onProgress)
  }

  override suspend fun delete(id: String) {
    release.await()
    fake.delete(id)
  }
}

/** Reports a half-done copy, then holds until [release] - a create caught mid-copy. */
private class CopyingSpaceRepository(private val fake: SpaceRepository) : SpaceRepository by fake {
  val release = CompletableDeferred<Unit>()

  override suspend fun create(name: String, onProgress: suspend (Float) -> Unit): SpaceConfig {
    onProgress(0.5f)
    release.await()
    return fake.create(name, onProgress)
  }
}
