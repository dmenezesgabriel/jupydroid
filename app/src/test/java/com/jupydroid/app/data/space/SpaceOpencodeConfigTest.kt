package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpaceOpencodeConfigTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * The opencode persona's own config, which asks before an edit or a shell command, becomes the
   * Space's global opencode config - where opencode reads it whoever starts it.
   */
  @Test
  fun ensure_makesThePersonasConfigTheSpacesGlobalOne() {
    val spaceDir = spaceWithPersonaConfig()

    SpaceOpencodeConfig.ensure(spaceDir)

    assertEquals(PERSONA_CONFIG, File(spaceDir, "home/jupydroid/.config/opencode/opencode.json").readText())
  }

  /** The file is the Space's own once written (ADR-0073): a start never overwrites a person's edit. */
  @Test
  fun ensure_leavesAnOpencodeJsonAlreadyThereUntouched() {
    val spaceDir = spaceWithPersonaConfig()
    val edited = File(spaceDir, "home/jupydroid/.config/opencode/opencode.json").apply {
      parentFile?.mkdirs()
      writeText("{\"permission\": {\"bash\": \"allow\"}}")
    }

    SpaceOpencodeConfig.ensure(spaceDir)

    assertEquals("{\"permission\": {\"bash\": \"allow\"}}", edited.readText())
  }

  /** An image without jupyter-ai's ACP client has no persona to take a default from. */
  @Test
  fun ensure_writesNothingWhereTheImageHasNoPersonaConfig() {
    val spaceDir = tempFolder.newFolder("space")

    SpaceOpencodeConfig.ensure(spaceDir)

    assertFalse(File(spaceDir, "home/jupydroid/.config/opencode").exists())
  }

  private fun spaceWithPersonaConfig(): File = tempFolder.newFolder("space").also { spaceDir ->
    File(spaceDir, PERSONA_CONFIG_PATH).apply { parentFile?.mkdirs() }.writeText(PERSONA_CONFIG)
  }

  private companion object {
    const val PERSONA_CONFIG_PATH =
      "usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.json"
    const val PERSONA_CONFIG = "{\"permission\": {\"edit\": \"ask\", \"bash\": \"ask\"}}"
  }
}
