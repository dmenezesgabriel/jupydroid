package com.jupydroid.app.domain.model

import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSpecModels
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `SessionContextDialogs.kernelOptions` (`packages/apputils/src/sessioncontext.tsx:1627-1833`)
 * as pure domain: which groups the picker lists, in which order, and which row is marked.
 *
 * The reference builds one `<optgroup>` per group inside a `<select>`; the groups and what marks
 * a row are the testable half, the dialog the other.
 */
class KernelPickerGroupsTest {
  /** `Start Kernel` / `Use No Kernel` / `Connect to Existing Kernel` - the no-language shape. */
  @Test
  fun withoutALanguageGroupsAreSpecsThenNoKernelThenRunning() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(XEUS, PYTHON)),
      running = listOf(RUNNING_R),
      preference = KernelPreference(),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals(listOf("Start Kernel", "Use No Kernel", "Connect to Existing Kernel"), groups.map { it.label })
    assertEquals(listOf("Python 3", "Xeus Python"), groups[0].options.map { it.text })
    assertEquals(listOf("No Kernel"), groups[1].options.map { it.text })
    assertEquals(null, groups[1].options.single().choice)
    assertEquals(listOf(KernelChoice.Running("rid-aaaa-1")), groups[2].options.map { it.choice })
  }

  /** With a language the specs split into preferred and other, and so do the running kernels. */
  @Test
  fun aLanguagePreferenceSplitsEveryListInTwo() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(XEUS, PYTHON, R_SPEC)),
      running = listOf(RUNNING_R, RUNNING_PYTHON),
      preference = KernelPreference(language = "python"),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals(
      listOf(
        "Start python Kernel",
        "Use No Kernel",
        "Start Kernel",
        "Connect to Existing python Kernel",
        "Connect to Other Kernel",
      ),
      groups.map { it.label },
    )
  }

  /** The same split, one level down: which rows each of the language groups actually holds. */
  @Test
  fun theLanguageGroupsHoldTheirOwnRows() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(XEUS, PYTHON, R_SPEC)),
      running = listOf(RUNNING_R, RUNNING_PYTHON),
      preference = KernelPreference(language = "python"),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals(listOf("Python 3", "Xeus Python"), groups[0].options.map { it.text })
    assertEquals(listOf("R"), groups[2].options.map { it.text })
    assertEquals(listOf("Analysis.ipynb (py7d)"), groups[3].options.map { it.text })
    assertEquals(listOf("scratch (rid)"), groups[4].options.map { it.text })
  }

  /** `optionForKernel`'s row: `session.name || basename(path)` plus the id's first UUID segment. */
  @Test
  fun aRunningKernelIsNamedForItsSessionAndIdPrefix() {
    val option = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON)),
      running = listOf(RUNNING_PYTHON),
      preference = KernelPreference(),
      kernelId = null,
      kernelDisplayName = null,
    ).last().options.single()

    assertEquals("Analysis.ipynb (py7d)", option.text)
    assertEquals(KernelChoice.Running("py7d-0000-0000-0000-000000000000"), option.choice)
    // `title` carries the four lines a hover would say (`:1711-1715`).
    val expectedTitle =
      "Path: work/Analysis.ipynb\nName: Analysis.ipynb\n" +
        "Kernel Name: Python 3\nKernel Id: py7d-0000-0000-0000-000000000000"
    assertEquals(expectedTitle, option.title)
  }

  /** `preference.id` wins - the running kernel this session is already on is the marked row. */
  @Test
  fun theBoundKernelIsTheMarkedRow() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON, R_SPEC)),
      running = listOf(RUNNING_R, RUNNING_PYTHON),
      preference = KernelPreference(language = "python"),
      kernelId = "py7d-0000-0000-0000-000000000000",
      kernelDisplayName = "Python 3",
    )

    val marked = groups.flatMap { it.options }.single { it.selected }
    assertEquals(KernelChoice.Running("py7d-0000-0000-0000-000000000000"), marked.choice)
  }

  /** `preference.name` marks the spec row - a persisted kernelspec that is still in the catalogue. */
  @Test
  fun aPersistedSpecNameMarksItsRow() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON, XEUS)),
      running = emptyList(),
      preference = KernelPreference(name = "xeus-python"),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals("Xeus Python", groups.flatMap { it.options }.single { it.selected }.text)
  }

  /**
   * `currentKernelDisplayName` marks the spec whose `display_name` matches - the reference's
   * second lookup, for a bound kernel whose id did not reach the option list.
   */
  @Test
  fun theLiveDisplayNameMarksItsSpec() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON, R_SPEC)),
      running = emptyList(),
      preference = KernelPreference(),
      kernelId = null,
      kernelDisplayName = "R",
    )

    assertEquals(KernelChoice.Spec("r"), groups.flatMap { it.options }.single { it.selected }.choice)
  }

  /** An empty "Connect" group is dropped - the language branch only pushes the groups it fills. */
  @Test
  fun emptyRunningGroupsAreNotListed() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON)),
      running = emptyList(),
      preference = KernelPreference(language = "python"),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals(listOf("Start python Kernel", "Use No Kernel", "Start Kernel"), groups.map { it.label })
  }

  /** The language a spec's name maps to stands in for an unset preference - `languages[name]`. */
  @Test
  fun aPersistedSpecNameLendsItsLanguage() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON, XEUS, R_SPEC)),
      running = emptyList(),
      preference = KernelPreference(name = "r"),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals("Start r Kernel", groups.first().label)
    assertEquals(listOf("R"), groups.first().options.map { it.text })
  }

  /** No row marked and no preference: the `<select>` shows its first option - `initialPick`. */
  @Test
  fun nothingMarkedMeansTheFirstRowAnswers() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON)),
      running = emptyList(),
      preference = KernelPreference(),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals(KernelChoice.Spec("python3"), groups.initialPick())
  }

  /** The marked row is the initial pick when there is one - the `<select>`'s `selected` option. */
  @Test
  fun theMarkedRowIsTheInitialPick() {
    val groups = kernelPickerGroups(
      specs = KernelSpecModels(default = "python3", specs = listOf(PYTHON, XEUS)),
      running = emptyList(),
      preference = KernelPreference(name = "xeus-python"),
      kernelId = null,
      kernelDisplayName = null,
    )

    assertEquals(KernelChoice.Spec("xeus-python"), groups.initialPick())
  }

  private companion object {
    val PYTHON = KernelSpec(name = "python3", displayName = "Python 3", language = "python")
    val XEUS = KernelSpec(name = "xeus-python", displayName = "Xeus Python", language = "python")
    val R_SPEC = KernelSpec(name = "r", displayName = "R", language = "r")

    /** A session row the way `GET api/sessions` reports one: kernel id, spec name, path, name. */
    fun session(kernelId: String, kernelName: String, path: String, name: String) = KernelSessionModel(
      serverSessionId = "s-$kernelId",
      kernelId = kernelId,
      kernelName = kernelName,
      path = path,
      name = name,
      type = "notebook",
    )

    val RUNNING_PYTHON = session("py7d-0000-0000-0000-000000000000", "python3", "work/Analysis.ipynb", "Analysis.ipynb")
    val RUNNING_R = session("rid-aaaa-1", "r", "scratch", "")
  }
}
