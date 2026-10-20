package com.jupydroid.extensions.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which chat the panel has open, or none, when it shows the list.
 *
 * Held by the extension rather than the panel's composition: picking another panel drops this one
 * from the composition, and the shell keeps no saved state for it, so a chat open in the panel would
 * be forgotten on every visit elsewhere. The extension is built once per process (`di/AppContainer`),
 * so a fresh process opens on the list, as jupyter-chat's restored side panel does
 * (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`, `restorer.add(chatPanel)`).
 */
class OpenChatSelection {
  var path by mutableStateOf<String?>(null)
}
