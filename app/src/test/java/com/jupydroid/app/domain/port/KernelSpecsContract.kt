package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What every [KernelSpecs] must answer, whoever implements it: the catalogue a Space can start,
 * and `getDefaultKernel`'s preference resolution against it
 * (`packages/apputils/src/sessioncontext.tsx:1912-1966`).
 *
 * Run against the fake and the base-Space one alike, for the reason [KernelGatewayContract]
 * gives: a port whose implementations agree only by inspection is a port that will drift.
 */
abstract class KernelSpecsContract {
  protected abstract fun specs(): KernelSpecs

  @Test
  fun specs_listsWhatTheSpaceCanStart() = runTest {
    val models = specs().specs(SPACE)

    assertEquals(listOf(PYTHON_SPEC), models.specs)
    assertEquals(PYTHON_SPEC.name, models.default)
  }

  /**
   * `Private.getDefaultKernel`'s own first branch: the persisted `metadata.kernelspec.name`,
   * resolved only when the catalogue actually carries it. A spec the Space does not have is
   * never answered - the server would only 501 it, which is why the reference asks instead.
   */
  @Test
  fun specs_resolvesAPersistedNameThatTheCatalogueHas() = runTest {
    val models = specs().specs(SPACE)

    assertEquals("python3", models.preferredKernelName(KernelPreference(name = "python3")))
  }

  @Test
  fun specs_refusesANameTheCatalogueDoesNotHave() = runTest {
    val models = specs().specs(SPACE)

    assertNull(models.preferredKernelName(KernelPreference(name = "xeus-python")))
  }

  /**
   * `getDefaultKernel`'s language half: no exact name match, one spec answering the persisted
   * `language_info.name` - an imported notebook saved under a kernelspec this Space does not
   * ship still lands on its own language.
   */
  @Test
  fun specs_resolvesByLanguageWhenTheNameIsUnknown() = runTest {
    val models = specs().specs(SPACE)

    assertEquals("python3", models.preferredKernelName(KernelPreference(name = "xeus-python", language = "python")))
  }

  /**
   * And its ending: no name match, no single language match, and no `autoStartDefault` - the
   * reference's answer is the picker, so this one's is *no answer*.
   */
  @Test
  fun specs_doesNotFallBackToTheDefaultForAWrongName() = runTest {
    val models = specs().specs(SPACE)

    assertNull(models.preferredKernelName(KernelPreference(name = "xeus-python", language = "julia")))
  }

  protected companion object {
    val PYTHON_SPEC = KernelSpec(name = "python3", displayName = "Python 3", language = "python")
    val SPACE = Space(id = "base", name = "Base", kernels = listOf(PYTHON_SPEC))
  }
}
