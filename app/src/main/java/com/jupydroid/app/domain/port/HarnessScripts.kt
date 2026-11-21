package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ChatHarness

/**
 * The install script each catalog harness ships, as the exact bytes a Space's shell will run.
 *
 * A port because the text is APK data (a platform mechanism a JVM test cannot reach), and because
 * the Settings section's promise - the bytes shown are the bytes run - is only testable when the
 * same string answers both.
 *
 * ```
 * val shown = harnessScripts.installScript(harness)
 * ```
 */
interface HarnessScripts {
  /**
   * [harness]'s install script, byte for byte: a `#!/bin/sh` script that self-checks its install
   * before exiting, so the run's own exit status says whether the executable landed.
   */
  suspend fun installScript(harness: ChatHarness): String
}
