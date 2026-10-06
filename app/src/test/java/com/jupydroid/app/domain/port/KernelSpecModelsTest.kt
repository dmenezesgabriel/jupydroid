package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.KernelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [KernelSpecModels.defaultSpec]: the spec a new notebook records, resolved from the catalogue's `default`. */
class KernelSpecModelsTest {
  @Test
  fun theDefaultSpecIsTheOneTheDefaultNames() {
    val models = KernelSpecModels(default = "xpython", specs = listOf(PYTHON3, XPYTHON))

    assertEquals(XPYTHON, models.defaultSpec)
  }

  /** A default the catalogue does not list is the server contradicting itself, said with both halves. */
  @Test
  fun aDefaultTheCatalogueLacksIsRefusedNamingIt() {
    val models = KernelSpecModels(default = "julia", specs = listOf(PYTHON3))

    val refusal = assertThrows(IllegalStateException::class.java) { models.defaultSpec }

    assertTrue(refusal.message!!, "julia" in refusal.message!! && "python3" in refusal.message!!)
  }

  private companion object {
    val PYTHON3 = KernelSpec("python3", "Python 3", "python")
    val XPYTHON = KernelSpec("xpython", "Python 3.13 (XPython)", "python")
  }
}
