package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.CHAT_HARNESS_CATALOG
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [HarnessScripts] must do, whoever implements it: every catalog harness answers a
 * script that is a shell script naming the executable it installs. The section shows these bytes
 * and `SpaceShell` runs them, so "it installs opencode" has to be visible in the text itself -
 * the self-check's `command -v` is what names it.
 */
abstract class HarnessScriptsContract {
  protected abstract fun scripts(): HarnessScripts

  @Test
  fun everyCatalogHarnessHasAScript() = runTest {
    CHAT_HARNESS_CATALOG.forEach { harness ->
      assertTrue("${harness.id} has no install script", scripts().installScript(harness).isNotBlank())
    }
  }

  @Test
  fun theScriptIsAShellScriptNamingItsExecutable() = runTest {
    CHAT_HARNESS_CATALOG.forEach { harness ->
      val script = scripts().installScript(harness)

      assertTrue("${harness.id}: $script", script.startsWith("#!/bin/sh"))
      assertTrue("${harness.id}: $script", harness.executable in script)
    }
  }
}
