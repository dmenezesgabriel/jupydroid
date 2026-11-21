package com.jupydroid.app.data.asset

import com.jupydroid.app.domain.model.CHAT_HARNESS_CATALOG
import com.jupydroid.app.domain.port.HarnessScripts
import com.jupydroid.app.domain.port.HarnessScriptsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val BUNDLED_SCRIPTS = mapOf(
  "chat-harnesses/opencode.sh" to "#!/bin/sh\necho installing opencode\n",
  "chat-harnesses/claude-agent-acp.sh" to "#!/bin/sh\necho installing claude-agent-acp\n",
)

class AssetHarnessScriptsTest : HarnessScriptsContract() {
  private fun bundled(files: Map<String, String> = BUNDLED_SCRIPTS) =
    InMemoryAssets(files).let { AssetHarnessScripts(it::open, Dispatchers.Unconfined) }

  override fun scripts(): HarnessScripts = bundled()

  @Test
  fun readsTheScriptAssetNamedForTheHarness() = runTest {
    val assets = InMemoryAssets(BUNDLED_SCRIPTS)
    val scripts = AssetHarnessScripts(assets::open, Dispatchers.Unconfined)
    val opencode = CHAT_HARNESS_CATALOG.first { it.id == "opencode" }

    scripts.installScript(opencode)

    assertEquals(listOf("chat-harnesses/opencode.sh"), assets.opened)
  }

  @Test
  fun returnsTheScriptByteForByte() = runTest {
    val claude = CHAT_HARNESS_CATALOG.first { it.id == "claude-agent-acp" }

    assertEquals(
      "#!/bin/sh\necho installing claude-agent-acp\n",
      bundled().installScript(claude),
    )
  }

  @Test
  fun aMissingScriptNamesItsPath() {
    val without = BUNDLED_SCRIPTS - "chat-harnesses/opencode.sh"
    val opencode = CHAT_HARNESS_CATALOG.first { it.id == "opencode" }

    val failure = assertThrows(IllegalStateException::class.java) {
      runBlocking { bundled(without).installScript(opencode) }
    }

    assertTrue(failure.message.orEmpty(), "chat-harnesses/opencode.sh" in failure.message.orEmpty())
  }
}
