package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelSpecModels
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `notebook:change-kernel` - the picker's answer landing on the session.
 *
 * The reference's `SessionContextDialogs.selectKernel` hands the choice to
 * `sessionContext.changeKernel`, which patches the session that exists rather than ending it
 * (`sessioncontext.tsx:956-960`) - the kernel swaps, the session's own id survives, and
 * `kernelChanged` is what every *other* holder of the session follows it by.
 */
class NotebookKernelChangeTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun changingTheKernelRebindsTheSessionItAlreadyHad() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    val before = viewModel.kernel.attachConsole().opened

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    val after = viewModel.kernel.attachConsole().opened
    assertEquals(listOf(KernelChoice.Spec("p8g")), gateway.kernelChanges)
    assertEquals("p8g", after?.kernelName)
    assertEquals(before?.serverSessionId, after?.serverSessionId)
    assertEquals(before?.id, after?.id)
  }

  /** With no session yet, the pick is a start with the choice, not a patch of nothing. */
  @Test
  fun changingTheKernelOnANotebookThatNeverRanStartsTheChosenOne() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    assertEquals(listOf(KernelChoice.Spec("p8g")), gateway.connectsAskedFor)
    assertEquals("p8g", viewModel.kernel.attachConsole().opened?.kernelName)
  }

  /**
   * `kernelChanged` in the reference (`sessioncontext.tsx`'s `session.kernelChanged`): a console
   * sharing the session, or the running panel having changed it, means the kernel under this
   * notebook's own handle was replaced. [KernelSessionRegistry.rebound] is the announcement and
   * this copy's session has to follow it - the old kernel id points at a process the server
   * already shut down.
   */
  @Test
  fun aKernelChangeFromElsewhereIsTakenUpHere() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = FakeKernelGateway(registry)
    val viewModel = loadedEditor(
      gateway = gateway,
      kernelSessions = FakeKernelSessions(registry),
      applicationScope = this,
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    val open = viewModel.kernel.attachConsole().opened!!

    gateway.changeKernel(open, KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    assertEquals("p8g", viewModel.kernel.attachConsole().opened?.kernelName)
  }

  /** A rebound about *another* session - a second open document - is not this one's business. */
  @Test
  fun aReboundForSomebodyElsesSessionLeavesThisOneAlone() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = FakeKernelGateway(registry)
    val viewModel = loadedEditor(
      gateway = gateway,
      kernelSessions = FakeKernelSessions(registry),
      applicationScope = this,
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    val open = viewModel.kernel.attachConsole().opened!!

    gateway.changeKernel(gateway.connect(BASE_SPACE, "Other.ipynb"), KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    assertEquals(open, viewModel.kernel.attachConsole().opened)
  }

  /**
   * `Context._populate` seeds `kernelPreference` from the saved document's
   * `defaultKernelName`/`defaultKernelLanguage` (`docregistry/src/context.ts:587-595`), so the
   * first connect of a reopened notebook asks for the kernelspec it was last saved with rather
   * than the server's default.
   */
  @Test
  fun theKernelAFileWasSavedWithIsWhatAFirstRunAsksFor() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(
      gateway = gateway,
      notebook = savedWithKernel("p8g"),
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(listOf(KernelChoice.Spec("p8g")), gateway.connectsAskedFor)
  }

  /**
   * `startKernel` answering needsSelection (`sessioncontext.tsx:746-747`): the persisted spec is
   * not in the Space's catalogue and its language matches nothing - the file was saved against a
   * kernelspec that has since been uninstalled. The reference opens `selectKernel` at load
   * (`context.ts:599-603`), which is what [EditorUiState.kernelPickRequired] counts; the run is
   * owed the same pick, and **nothing reaches the server** - POSTing "xeus-python" would only 501.
   */
  @Test
  fun aPersistedSpecTheSpaceDoesNotHaveOwesAPickAndNeverReachesTheServer() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(
      gateway = gateway,
      notebook = savedWithKernel("xeus-python"),
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )

    assertEquals("the pick is owed at load, before anything runs", 1, viewModel.uiState.value.kernelPickRequired)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(emptyList<KernelChoice>(), gateway.connectsAskedFor)
    assertEquals(2, viewModel.uiState.value.kernelPickRequired)
    assertEquals(ExecutionStatus.NOT_STARTED, viewModel.uiState.value.statusOf(codeCell.id))
  }

  /**
   * `kernelLanguage`'s half of the resolution: the saved spec is gone, but `language_info` names
   * a language exactly one surviving spec speaks, so it stands in
   * (`sessioncontext.tsx:1949-1961`) rather than asking.
   */
  @Test
  fun aSpecThatIsGoneResolvesThroughTheSavedLanguage() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = loadedEditor(
      gateway = gateway,
      notebook = savedWithKernel("xeus-python", language = "python"),
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(listOf(KernelChoice.Spec("python3")), gateway.connectsAskedFor)
    assertEquals(0, viewModel.uiState.value.kernelPickRequired)
  }

  /**
   * `NotebookPanel._updateSpec` (`panel.ts:248-258`): the kernel that binds writes
   * `metadata.kernelspec = {name, display_name, language}` back to the document - dirty, then
   * autosaved. The catalogue's own `display_name`/`language` go with it, not guesses.
   */
  @Test
  fun aBoundKernelWritesItsSpecBackToTheNotebook() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(
      repository = repository,
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    val kernelspec = repository.readNotebook("nb-1").kernelspecJson()
    assertEquals("p8g", kernelspec["name"]?.jsonPrimitive?.content)
    assertEquals("P8g Julia", kernelspec["display_name"]?.jsonPrimitive?.content)
    assertEquals("julia", kernelspec["language"]?.jsonPrimitive?.content)
  }

  /** The reopening half of `_updateSpec`: what was written is what the next sitting asks for. */
  @Test
  fun reopeningANotebookHonoursTheKernelItWasLastBoundTo() = runTest {
    val repository = FakeNotebookRepository()
    val first = loadedEditor(repository = repository, kernelSpecs = FakeKernelSpecs(CATALOGUE), applicationScope = this)
    first.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    val secondGateway = FakeKernelGateway()
    val second = loadedEditor(
      gateway = secondGateway,
      repository = repository,
      notebook = repository.readNotebook("nb-1"),
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )
    second.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(listOf(KernelChoice.Spec("p8g")), secondGateway.connectsAskedFor)
  }

  /**
   * `_changeKernel` sets `_pendingKernelName` the moment the PATCH is issued
   * (`sessioncontext.tsx:938`), and `kernelDisplayName` resolves it through the catalogue - for
   * the seconds a change takes, the bar names the kernel *arriving*, not the one being replaced
   * and not "No Kernel".
   */
  @Test
  fun aKernelBeingChangedToIsNamedWhileThePatchIsOut() = runTest {
    val gateway = SlowToChangeKernelGateway()
    val viewModel = loadedEditor(
      gateway = gateway,
      kernelSpecs = FakeKernelSpecs(CATALOGUE),
      applicationScope = this,
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()
    assertEquals("P8g Julia", viewModel.uiState.value.kernelDisplayName)

    gateway.letItChange()
    advanceUntilIdle()
    assertEquals("P8g Julia", viewModel.uiState.value.kernelDisplayName)
  }

  /**
   * `kernelDisplayName` (`sessioncontext.tsx`): what the indicator draws is the *bound* kernel's
   * spec `display_name` - not the file's saved one, not a constant - and "No Kernel" once the
   * session is gone (`noKernelName`).
   */
  @Test
  fun theNameShownFollowsTheBoundKernel() = runTest {
    val viewModel = loadedEditor(kernelSpecs = FakeKernelSpecs(CATALOGUE), applicationScope = this)
    assertEquals("No Kernel", viewModel.uiState.value.kernelDisplayName)

    viewModel.kernel.picker.pick(KernelChoice.Spec("p8g"))
    advanceUntilIdle()
    assertEquals("P8g Julia", viewModel.uiState.value.kernelDisplayName)

    viewModel.kernel.shutdown()
    advanceUntilIdle()
    assertEquals("No Kernel", viewModel.uiState.value.kernelDisplayName)
  }

  /**
   * `kernelDisplayName`'s fallback: a kernel the catalogue has no spec for - a "Connect to
   * Existing" attach carries a kernel the server started for somebody else - shows its raw
   * `kernel.name` rather than nothing or a borrowed display name.
   */
  @Test
  fun aKernelTheCatalogueDoesNotKnowShowsItsOwnName() = runTest {
    val viewModel = loadedEditor(kernelSpecs = FakeKernelSpecs(CATALOGUE), applicationScope = this)

    viewModel.kernel.picker.pick(KernelChoice.Running("kernel-live-9"))
    advanceUntilIdle()

    assertEquals("kernel-live-9", viewModel.uiState.value.kernelDisplayName)
  }

  private fun Notebook.kernelspecJson(): JsonObject =
    (preserved["metadata"] as? JsonObject)?.get("kernelspec") as? JsonObject ?: JsonObject(emptyMap())

  private companion object {
    /** The Space's catalogue for these cases: the shipped spec plus a second that a test picks. */
    val CATALOGUE = KernelSpecModels(
      default = "python3",
      specs = listOf(
        KernelSpec(name = "python3", displayName = "Python 3", language = "python"),
        KernelSpec(name = "p8g", displayName = "P8g Julia", language = "julia"),
      ),
    )

    /**
     * A notebook as it is read from a file saved against [name] - `metadata.kernelspec.name`,
     * and `metadata.language_info.name` when the kernel answered one.
     */
    fun savedWithKernel(name: String, language: String? = null): Notebook = Notebook(
      id = "nb-1",
      title = "Data",
      kernelId = name,
      cells = listOf(codeCell),
      preserved = JsonObject(
        mapOf(
          "metadata" to buildJsonObject {
            put(
              "kernelspec",
              buildJsonObject {
                put("name", name)
                put("display_name", name)
              },
            )
            if (language != null) put("language_info", buildJsonObject { put("name", language) })
          },
        ),
      ),
    )
  }
}
