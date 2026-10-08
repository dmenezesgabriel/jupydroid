package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
import com.jupydroid.app.domain.model.MAX_IDLE_SHUTDOWN_MINUTES
import com.jupydroid.app.domain.model.MAX_THREAD_CAP
import com.jupydroid.app.domain.model.SPACE_ID_PREFIX
import com.jupydroid.app.domain.model.SpaceSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [SpaceRepository] must do, whoever implements it.
 *
 * Run against the fake and the disk one alike, for the reason every contract here exists: two
 * implementations that agree only by inspection drift, and this pair has to agree about three
 * things a ViewModel test would never notice - how an id is minted, what order the listing comes
 * in, and which id refuses to be deleted.
 *
 * **[repository] starts holding exactly the base Space**, because that is the state of every
 * installed device: `files/spaces/base/` is created by the rootfs extraction and nothing else is
 * there until someone makes a Space.
 */
abstract class SpaceRepositoryContract {
  /** A repository holding exactly the base Space, and nothing created yet. */
  protected abstract fun repository(): SpaceRepository

  /**
   * The Space every document already runs against is listed, named, before anything is created.
   *
   * It has no `config.json` on a real device - the installer has never written one - so this is
   * also the assertion that a directory *is* a Space and its config file is optional.
   */
  @Test
  fun theBaseSpaceIsListedBeforeAnythingIsCreated() = runTest {
    val spaces = repository().spaces()

    assertEquals(listOf(BASE_SPACE.id), spaces.map { it.id })
    assertEquals(BASE_SPACE.name, spaces.single().name)
  }

  @Test
  fun theFirstCreatedSpaceIsSpaceOneAndCarriesTheNameItWasGiven() = runTest {
    val repository = repository()

    val created = repository.create("Thesis")

    assertEquals(SPACE_ID_PREFIX + "1", created.id)
    assertEquals("Thesis", created.name)
    assertEquals(listOf(BASE_SPACE.id, created.id), repository.spaces().map { it.id })
  }

  @Test
  fun aSecondCreatedSpaceIsSpaceTwo() = runTest {
    val repository = repository()
    repository.create("Thesis")

    assertEquals(SPACE_ID_PREFIX + "2", repository.create("Scratch").id)
  }

  /**
   * The copy is ~14 s, so create reports its fraction - and a Space under construction is never
   * listed: every emission below `1f` comes before the scan could see the id, `1f` once it can.
   * The fake's copy is instant, so the contract asks the pair to agree on the *edges* - the
   * rising order and which listing each side of `1f` sees.
   */
  @Test
  fun createReportsRisingProgressAndListsTheSpaceOnlyAtTheEnd() = runTest {
    val repository = repository()
    val seen = mutableListOf<Pair<Float, List<String>>>()

    val created = repository.create("Thesis") { fraction ->
      seen += fraction to repository.spaces().map { it.id }
    }

    val fractions = seen.map { it.first }
    assertTrue("expected progress emissions, got none", fractions.isNotEmpty())
    assertTrue("fractions out of [0,1]: $fractions", fractions.all { it in 0f..1f })
    assertEquals("fractions must not decrease: $fractions", fractions.sorted(), fractions)
    assertEquals("the last emission must be the whole copy, was $fractions", 1f, fractions.last())
    seen.dropLast(1).forEach { (fraction, ids) ->
      assertFalse("${created.id} was listed at fraction $fraction: $ids", created.id in ids)
    }
    assertTrue("the final emission must see ${created.id} listed: $seen", created.id in seen.last().second)
  }

  /**
   * Two Spaces may be called the same thing, because the id is the identity.
   *
   * The reference names a duplicate `"<name> (copy)"` rather than refusing it
   * (`ContainerManager.duplicateContainer`), so a name is a label and never a key here either.
   */
  @Test
  fun twoSpacesMayShareAName() = runTest {
    val repository = repository()

    val first = repository.create("Thesis")
    val second = repository.create("Thesis")

    assertTrue("ids must differ, were '${first.id}' and '${second.id}'", first.id != second.id)
    assertEquals(2, repository.spaces().count { it.name == "Thesis" })
  }

  /** A rename touches the name and nothing else - above all, not the id the directory carries. */
  @Test
  fun renamingChangesTheNameAndLeavesEverythingElseAlone() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    val renamed = repository.rename(created.id, "Dissertation")

    assertEquals(created.copy(name = "Dissertation"), renamed)
    assertEquals("Dissertation", repository.spaces().single { it.id == created.id }.name)
  }

  @Test
  fun renamingAnUnknownIdSaysWhichId() = runTest {
    val refusal = runCatching { repository().rename("space-99", "Ghost") }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue("message must name the id, was '${refusal?.message}'", refusal?.message?.contains("space-99") == true)
  }

  /** Five minutes, the timers every server ran on before a Space could choose its own. */
  @Test
  fun aNewSpaceIdlesOutAfterTheDefaultFiveMinutes() = runTest {
    val created = repository().create("Thesis")

    assertEquals(DEFAULT_IDLE_SHUTDOWN_MINUTES, created.settings.idleShutdownMinutes)
    assertEquals(5, DEFAULT_IDLE_SHUTDOWN_MINUTES)
  }

  /** Configuring writes the settings and nothing else - the name, the id and `created` stay. */
  @Test
  fun configuringChangesTheSettingsAndLeavesEverythingElseAlone() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    val configured = repository.configure(created.id, SpaceSettings(idleShutdownMinutes = 2))

    assertEquals(created.copy(settings = SpaceSettings(idleShutdownMinutes = 2)), configured)
    assertEquals(configured, repository.spaces().single { it.id == created.id })
  }

  /** The base Space is configured like any other: only deleting and renaming it are refused. */
  @Test
  fun theBaseSpaceCanBeConfigured() = runTest {
    val repository = repository()

    repository.configure(BASE_SPACE.id, SpaceSettings(idleShutdownMinutes = 0))

    assertEquals(0, repository.spaces().single().settings.idleShutdownMinutes)
  }

  @Test
  fun configuringAnUnknownIdSaysWhichId() = runTest {
    val refusal = runCatching { repository().configure("space-99", SpaceSettings()) }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue("message must name the id, was '${refusal?.message}'", refusal?.message?.contains("space-99") == true)
  }

  /** A minute count outside `0..1440` is refused by name, and the stored record is not touched. */
  @Test
  fun anIdleShutdownOutsideTheRangeIsRefusedAndNothingIsWritten() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    listOf(-1, MAX_IDLE_SHUTDOWN_MINUTES + 1).forEach { minutes ->
      val refusal = runCatching {
        repository.configure(created.id, SpaceSettings(idleShutdownMinutes = minutes))
      }.exceptionOrNull()

      assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
      assertTrue(
        "message must name $minutes, was '${refusal?.message}'",
        refusal?.message?.contains("$minutes") == true,
      )
    }
    assertEquals(created, repository.spaces().single { it.id == created.id })
  }

  @Test
  fun aConfiguredThreadCapIsStored() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    repository.configure(created.id, SpaceSettings(threadCap = 1))

    assertEquals(1, repository.spaces().single { it.id == created.id }.settings.threadCap)
  }

  @Test
  fun aConfiguredStartupScriptIsStoredVerbatim() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")
    val script = "echo started > ~/startup.txt\npip install --quiet rich"

    repository.configure(created.id, SpaceSettings(startupScript = script))

    assertEquals(script, repository.spaces().single { it.id == created.id }.settings.startupScript)
  }

  @Test
  fun aConfiguredDefaultKernelIsStored() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    repository.configure(created.id, SpaceSettings(defaultKernel = "stats"))

    assertEquals("stats", repository.spaces().single { it.id == created.id }.settings.defaultKernel)
  }

  /** A name no kernelspec directory can carry is refused by value, and the stored record is not touched. */
  @Test
  fun aDefaultKernelNoSpecCanBeNamedIsRefusedAndNothingIsWritten() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    val refusal = runCatching { repository.configure(created.id, SpaceSettings(defaultKernel = "my env")) }
      .exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue("message must name it, was '${refusal?.message}'", refusal?.message?.contains("'my env'") == true)
    assertEquals(created, repository.spaces().single { it.id == created.id })
  }

  /** A cap outside `1..64` is refused by value, and the stored record is not touched. */
  @Test
  fun aThreadCapOutsideTheRangeIsRefusedAndNothingIsWritten() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    listOf(0, MAX_THREAD_CAP + 1).forEach { cap ->
      val refusal = runCatching { repository.configure(created.id, SpaceSettings(threadCap = cap)) }.exceptionOrNull()

      assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
      assertTrue("message must name $cap, was '${refusal?.message}'", refusal?.message?.contains("$cap") == true)
    }
    assertEquals(created, repository.spaces().single { it.id == created.id })
  }

  /** The cap's names are the cap's: a variable cannot set one around it. */
  @Test
  fun aThreadCapNameIsRefusedAsAVariable() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")

    val refusal = runCatching {
      repository.configure(created.id, SpaceSettings(environmentVariables = mapOf("OMP_NUM_THREADS" to "8")))
    }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue(
      "message must name it, was '${refusal?.message}'",
      refusal?.message?.contains("'OMP_NUM_THREADS'") == true,
    )
  }

  /** A Space's own variables are stored in the order they were given, and read back as written. */
  @Test
  fun configuredVariablesAreStoredInTheirOrder() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")
    val variables = linkedMapOf("ZETA" to "last letter", "GREETING" to "hello world", "EMPTY" to "")

    repository.configure(created.id, SpaceSettings(environmentVariables = variables))

    val stored = repository.spaces().single { it.id == created.id }.settings.environmentVariables
    assertEquals(variables.toList(), stored.toList())
  }

  /**
   * A name the app derives from the Space's directory is refused **by name**, since a `HOME` that
   * disagrees with the disk breaks the Space (ADR-0072) - and so is a name no shell could export, and a
   * value no process environment can carry.
   */
  @Test
  fun aDerivedOrMalformedVariableIsRefusedByNameAndNothingIsWritten() = runTest {
    val repository = repository()
    val created = repository.create("Thesis")
    val refused = mapOf("HOME" to "/tmp", "PATH" to "/bin", "1X" to "v", "A=B" to "v", "" to "v", "NUL" to "a\u0000b")

    refused.forEach { (name, value) ->
      val refusal = runCatching {
        repository.configure(created.id, SpaceSettings(environmentVariables = mapOf(name to value)))
      }.exceptionOrNull()

      assertTrue("expected IllegalArgumentException for '$name', was $refusal", refusal is IllegalArgumentException)
      assertTrue("message must name '$name', was '${refusal?.message}'", refusal?.message?.contains("'$name'") == true)
    }
    assertEquals(created, repository.spaces().single { it.id == created.id })
  }

  @Test
  fun deletingASpaceTakesItOutOfTheListing() = runTest {
    val repository = repository()
    val created = repository.create("Scratch")

    repository.delete(created.id)

    assertEquals(listOf(BASE_SPACE.id), repository.spaces().map { it.id })
  }

  /**
   * The base Space is the one id that cannot go: it holds the rootfs every other Space shares.
   *
   * Enforced here rather than by the screen that offers the control, because a second caller would
   * bypass a UI guard and there is no undo for 268 MiB of extracted rootfs.
   */
  @Test
  fun theBaseSpaceCannotBeDeleted() = runTest {
    val repository = repository()

    val refusal = runCatching { repository.delete(BASE_SPACE.id) }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue(
      "message must name the id, was '${refusal?.message}'",
      refusal?.message?.contains(BASE_SPACE.id) == true,
    )
    assertEquals(listOf(BASE_SPACE.id), repository.spaces().map { it.id })
  }

  @Test
  fun deletingAnUnknownIdSaysWhichId() = runTest {
    val refusal = runCatching { repository().delete("space-99") }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue("message must name the id, was '${refusal?.message}'", refusal?.message?.contains("space-99") == true)
  }

  /** `space-2` before `space-10`: the listing is ordered by number, not by the id as text. */
  @Test
  fun theListingIsOrderedByNumberRatherThanByText() = runTest {
    val repository = repository()
    repeat(10) { repository.create("Space $it") }

    val ids = repository.spaces().map { it.id }

    assertEquals(BASE_SPACE.id, ids.first())
    assertEquals((1..10).map { SPACE_ID_PREFIX + it }, ids.drop(1))
  }
}
