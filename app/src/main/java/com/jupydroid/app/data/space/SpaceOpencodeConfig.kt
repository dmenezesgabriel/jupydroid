package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.SPACE_WORKSPACE_PATH
import java.io.File

/**
 * Makes opencode ask before it edits a file or runs a command in the Space, as jupyter-ai's
 * opencode persona means it to.
 *
 * The persona hands opencode its bundled config (`edit` and `bash`: `ask`) only while
 * `~/.config/opencode/opencode.json(c)` is absent (`_has_user_config`,
 * `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.py` in the shipped
 * rootfs). opencode's first start writes a `$schema`-only `opencode.jsonc` there whenever no global
 * config exists (`loadGlobal`, opencode@e3c4eb2 `packages/opencode/src/config/config.ts`, tag
 * v1.19.3), so from then on the persona injected nothing and every tool ran unasked. With this file
 * in place opencode writes no stub, and it merges an `opencode.jsonc` over it, so a person's own
 * `.jsonc` still wins.
 *
 * Written only when missing: once there it is the Space's own file to edit (ADR-0073).
 *
 * ```
 * SpaceOpencodeConfig.ensure(File(filesDir, "spaces/base"))
 * ```
 */
object SpaceOpencodeConfig {
  private const val PERSONA_CONFIG_PATH =
    "usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.json"

  private val GLOBAL_CONFIG_PATH = "${SPACE_WORKSPACE_PATH.removePrefix("/")}/.config/opencode/opencode.json"

  /** Copies the persona's config to the Space's global opencode config unless one is already there. */
  fun ensure(spaceDir: File) {
    val personaConfig = File(spaceDir, PERSONA_CONFIG_PATH)
    val globalConfig = File(spaceDir, GLOBAL_CONFIG_PATH)
    if (!personaConfig.isFile || globalConfig.exists()) return
    globalConfig.parentFile?.mkdirs()
    personaConfig.copyTo(globalConfig)
  }
}
