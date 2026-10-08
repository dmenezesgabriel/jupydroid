package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceImageInstaller
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.data.space.tarZst
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.BUNDLED_PYTHON_VERSION
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceRepositoryContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskSpaceRepositoryTest : SpaceRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val spacesDir: File get() = File(temp.root, "spaces")

  /**
   * The state an installed device is in: `files/spaces/base/` exists because the rootfs was
   * extracted into it, and it carries no `config.json` because nothing has ever written one.
   */
  override fun repository(): SpaceRepository {
    File(spacesDir, BASE_SPACE.id).mkdirs()
    return openStore()
  }

  private fun openStore(image: ByteArray = IMAGE) = DiskSpaceRepository(
    spacesDir,
    SpaceImageInstaller(archive = { image.inputStream() }),
    clock = Clock.fixed(CREATED, ZoneOffset.UTC),
  )

  @Test
  fun survivesTheNextLaunch() = runTest {
    val created = repository().create("Thesis")

    val reopened = openStore().spaces().single { it.id == created.id }

    assertEquals(created, reopened)
  }

  /**
   * A Space's own record lands inside its directory - beside the copied image, which is the rest
   * of what a create puts there.
   */
  @Test
  fun createWritesTheConfigInsideTheNewDirectoryAndNothingBeside() = runTest {
    val created = repository().create("Thesis")

    val directory = spaceDirIn(spacesDir, created.id)
    assertTrue(File(directory, SPACE_CONFIG_FILE_NAME).isFile)
    assertTrue("the image was not copied", File(directory, "usr/bin/python3").isFile)
    assertEquals(setOf(BASE_SPACE.id, created.id), spacesDir.list()?.toSet())
  }

  /** The copy is staged under a dot name and renamed into place, so a half copy is never listed. */
  @Test
  fun aSpaceUnderConstructionIsInvisibleToTheScan() = runTest {
    val repository = repository()
    val sightings = mutableListOf<Boolean>()

    repository.create("Thesis") { sightings += repository.spaces().any { it.id == "space-1" } }

    assertTrue(sightings.isNotEmpty())
    assertFalse("a staging directory was listed as a Space", sightings.dropLast(1).any { it })
    assertTrue("the finished Space must be listed at 1f", sightings.last())
  }

  /** An interrupted copy leaves only the staging directory - and the listing never shows it. */
  @Test
  fun aFailedCopyLeavesNothingListedAndSweepsItsStaging() = runTest {
    repository()
    val brokenStore = openStore(image = IMAGE.copyOf(16))

    val refusal = runCatching { brokenStore.create("Thesis") }.exceptionOrNull()

    assertTrue("expected the broken archive to refuse", refusal != null)
    assertEquals(listOf(BASE_SPACE.id), brokenStore.spaces().map { it.id })
    assertEquals(
      "staging must not linger: ${spacesDir.list()?.toList()}",
      listOf(BASE_SPACE.id),
      spacesDir.list()?.toList(),
    )
  }

  /** Termux's rule: a leftover staging directory is a killed create's, cleared before the next. */
  @Test
  fun aKilledCreatesStagingDirectoryIsSweptOnTheNextCreate() = runTest {
    val repository = repository()
    val leftover = File(spacesDir, ".installing").apply { mkdirs() }
    File(leftover, "half").writeText("a killed copy")

    repository.create("Thesis")

    assertFalse("staging must not survive a successful create", leftover.exists())
    assertEquals("elf", File(spaceDirIn(spacesDir, "space-1"), "usr/bin/python3").readText())
  }

  /**
   * A config that cannot be parsed reads as the default rather than throwing.
   *
   * The same rule `DiskEditorSettingsRepository.loadValidated` and `DiskRecentDocumentsRepository`
   * apply, and for a harder reason here: the alternative is a Space list that cannot be drawn at
   * all because one directory out of ten holds a half-written file.
   */
  @Test
  fun anUnparseableConfigReadsAsTheDefault() = runTest {
    val created = repository().create("Thesis")
    File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME).writeText("{ not json")

    val read = openStore().spaces().single { it.id == created.id }

    assertEquals(created.id, read.name)
    assertEquals(BUNDLED_PYTHON_VERSION, read.pythonVersion)
  }

  /** A directory nobody here created is still a Space, named after itself until it is renamed. */
  @Test
  fun aDirectoryWithNoConfigIsNamedAfterItself() = runTest {
    repository()
    File(spacesDir, "scratch").mkdirs()

    assertEquals("scratch", openStore().spaces().single { it.id == "scratch" }.name)
  }

  /** A Space is a *directory*; a stray file under `spaces/` is not one. */
  @Test
  fun aFileUnderSpacesIsNotASpace() = runTest {
    repository()
    File(spacesDir, "notes.txt").writeText("stray")

    assertEquals(listOf(BASE_SPACE.id), openStore().spaces().map { it.id })
  }

  /** `.`-prefixed entries are skipped here as they are in the workspace listing. */
  @Test
  fun aDotPrefixedDirectoryIsNotASpace() = runTest {
    repository()
    File(spacesDir, ".trash").mkdirs()

    assertEquals(listOf(BASE_SPACE.id), openStore().spaces().map { it.id })
  }

  /** Before the rootfs is extracted there is no `spaces/` at all, and that is not an error. */
  @Test
  fun noSpacesDirectoryAtAllListsNothing() = runTest {
    assertEquals(emptyList<String>(), openStore().spaces().map { it.id })
  }

  /**
   * A field a later version of the app writes survives a rename by this one.
   *
   * `Container.extraData` is round-tripped the same way (`Container.java:370-373`, `:306`), and the
   * cost of not doing it is the shredder `PreservedJson`'s own doc describes.
   */
  @Test
  fun unknownFieldsUnderExtraSurviveARename() = runTest {
    val created = repository().create("Thesis")
    val configFile = File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME)
    configFile.writeText("""{"name":"Thesis","created":"$CREATED","extra":{"activeKernel":"julia-1.11"}}""")

    openStore().rename(created.id, "Dissertation")

    assertTrue("extra was shredded: ${configFile.readText()}", configFile.readText().contains("julia-1.11"))
    assertEquals("Dissertation", openStore().spaces().single { it.id == created.id }.name)
  }

  /**
   * A config that states only some of its fields yields the ones it states.
   *
   * The half that made the whole-file fallback a defect rather than a policy: before this, a config
   * missing `pythonVersion` lost its *name* too, because one unparseable field threw the file away.
   */
  @Test
  fun aConfigStatingOnlySomeFieldsKeepsTheOnesItStates() = runTest {
    val created = repository().create("Thesis")
    File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME).writeText("""{"name":"Dissertation"}""")

    val read = openStore().spaces().single { it.id == created.id }

    assertEquals("Dissertation", read.name)
    assertEquals(BUNDLED_PYTHON_VERSION, read.pythonVersion)
  }

  /** A Space's settings are in its own `config.json`, so the next launch reads what was saved. */
  @Test
  fun settingsSurviveTheNextLaunch() = runTest {
    val created = repository().create("Thesis")

    openStore().configure(created.id, SpaceSettings(idleShutdownMinutes = 2))

    assertEquals(2, openStore().spaces().single { it.id == created.id }.settings.idleShutdownMinutes)
  }

  @Test
  fun variablesSurviveTheNextLaunch() = runTest {
    val created = repository().create("Thesis")

    openStore().configure(created.id, SpaceSettings(environmentVariables = mapOf("GREETING" to "hello")))

    val read = openStore().spaces().single { it.id == created.id }.settings
    assertEquals(mapOf("GREETING" to "hello"), read.environmentVariables)
  }

  @Test
  fun aThreadCapSurvivesTheNextLaunch() = runTest {
    val created = repository().create("Thesis")

    openStore().configure(created.id, SpaceSettings(threadCap = 1))

    assertEquals(1, openStore().spaces().single { it.id == created.id }.settings.threadCap)
  }

  @Test
  fun aStartupScriptSurvivesTheNextLaunch() = runTest {
    val created = repository().create("Thesis")

    openStore().configure(created.id, SpaceSettings(startupScript = "echo started > ~/startup.txt"))

    val read = openStore().spaces().single { it.id == created.id }.settings
    assertEquals("echo started > ~/startup.txt", read.startupScript)
  }

  @Test
  fun aDefaultKernelSurvivesTheNextLaunch() = runTest {
    val created = repository().create("Thesis")

    openStore().configure(created.id, SpaceSettings(defaultKernel = "stats"))

    assertEquals("stats", openStore().spaces().single { it.id == created.id }.settings.defaultKernel)
  }

  /** Settings written before a Space had variables, a cap or a script keep their idle time and read none. */
  @Test
  fun settingsWithNoVariablesReadNone() = runTest {
    val created = repository().create("Thesis")
    File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME)
      .writeText("""{"name":"Thesis","settings":{"idleShutdownMinutes":2}}""")

    val read = openStore().spaces().single { it.id == created.id }.settings

    assertEquals(
      SpaceSettings(idleShutdownMinutes = 2, environmentVariables = emptyMap(), threadCap = null, startupScript = ""),
      read,
    )
  }

  /** A config written before Spaces had settings reads the default and keeps its name. */
  @Test
  fun aConfigWithNoSettingsReadsTheDefaultSettings() = runTest {
    val created = repository().create("Thesis")
    File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME).writeText("""{"name":"Thesis"}""")

    val read = openStore().spaces().single { it.id == created.id }

    assertEquals("Thesis", read.name)
    assertEquals(SpaceSettings(), read.settings)
  }

  /** A hand-edited `created` nobody can parse falls back to the directory's own mtime. */
  @Test
  fun aMalformedCreatedReadsAsTheDirectorysMtime() = runTest {
    val created = repository().create("Thesis")
    File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME).writeText("""{"name":"Thesis","created":"last week"}""")

    val read = openStore().spaces().single { it.id == created.id }

    assertEquals("Thesis", read.name)
    assertEquals(Instant.ofEpochMilli(File(spacesDir, created.id).lastModified()), read.created)
  }

  /** The stored shape is ISO-8601, so a config file is readable by eye and by any other reader. */
  @Test
  fun createdIsStoredAsIso8601() = runTest {
    val created = repository().create("Thesis")

    val stored = File(File(spacesDir, created.id), SPACE_CONFIG_FILE_NAME).readText()

    assertTrue("expected '$CREATED' in $stored", stored.contains(CREATED.toString()))
    assertEquals(CREATED, openStore().spaces().single { it.id == created.id }.created)
  }

  @Test
  fun deletingASpaceTakesItsDirectoryAndContentsWithIt() = runTest {
    val repository = repository()
    val created = repository.create("Scratch")
    File(File(spacesDir, created.id), "pkgs").mkdirs()

    repository.delete(created.id)

    assertFalse(File(spacesDir, created.id).exists())
  }

  private companion object {
    /** Fixed, so `created` is an assertion rather than a race with the clock. */
    val CREATED: Instant = Instant.parse("2026-09-28T12:00:00Z")

    /** The smallest "rootfs" that still proves a copy happened: one file plus a directory. */
    val IMAGE: ByteArray = tarZst {
      directory("usr/bin")
      file("usr/bin/python3", "elf")
    }
  }
}
