package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The [SpaceServer] a test of one of its *clients* stands on - a kernel gateway, an exporter, a
 * terminal gateway - where whether the Space reads Running is not the question. Nothing is
 * installed and nothing is reported, and the exit watcher runs inline: these tests' launchers
 * return from `awaitExit` at once, so it never outlives the call that started it.
 *
 * ```
 * PerRunningServer(clientTestSpaceServer(launcher)) { "a client for ${it.port}" }
 * ```
 */
fun clientTestSpaceServer(
  launcher: ServerLauncher,
  log: SpaceLifecycleLog = RecordingSpaceLifecycleLog(),
): SpaceServer = SpaceServer(
  space = BASE_SPACE,
  installSpace = {},
  launcher = launcher,
  watchScope = CoroutineScope(Dispatchers.Unconfined),
  reportRunning = {},
  log = log,
)
