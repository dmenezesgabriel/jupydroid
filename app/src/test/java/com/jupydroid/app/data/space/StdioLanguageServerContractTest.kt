package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageServerPortContract
import java.io.File

/**
 * The real [StdioLanguageServer] held to the port contract over a [ScriptedLanguageServer] -
 * actual pipes, actual framing, actual process lifecycle; only the ELF is absent.
 */
class StdioLanguageServerContractTest : LanguageServerPortContract() {
  override fun languageServer(): LanguageServerPort = StdioLanguageServer(
    installSpace = { InstalledSpace(File("/dev/null"), 7, false) },
    spawn = { ScriptedLanguageServer() },
  )
}
