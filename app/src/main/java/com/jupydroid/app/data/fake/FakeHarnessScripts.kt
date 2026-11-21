package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.port.HarnessScripts

/**
 * A [HarnessScripts] whose every answer is a stand-in script, so a screen test expands and "runs"
 * a harness's install where no APK assets stand behind it. `AppContainer`'s default, like every
 * port's fake.
 */
class FakeHarnessScripts : HarnessScripts {
  override suspend fun installScript(harness: ChatHarness): String = """
    #!/bin/sh
    # FakeHarnessScripts' stand-in: no APK assets exist here, so this text is what a screen
    # test reads where the real script would be shown and run.
    echo "fake install of ${harness.executable}"
  """.trimIndent()
}
