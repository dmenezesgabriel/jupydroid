package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway

/**
 * One [KernelSockets] per [server] generation, shared by [baseSpaceKernelGateway] and
 * [baseSpaceKernelDebugger] - a `debug_request` reuses the identical per-session socket
 * `execute`/`complete`/`history` already share, rather than opening a second one.
 */
fun baseSpaceKernelSockets(server: BaseSpaceServer): PerRunningServer<KernelSockets> =
  PerRunningServer(server) { connection ->
    KernelSockets(
      opener = OkHttpKernelSocketOpener(connection),
      // The lifecycle gets the same log: a shutdown that found the session already gone is the
      // one thing it reports rather than raises, and on device that line is the only account of it.
      lifecycle = OkHttpKernelLifecycle(connection, log = LogcatSpaceLifecycleLog),
      // The same tag the rest of the runtime reports under, so a stall is one filter to read:
      // `adb logcat -s JupydroidSpace`. Without this the execute path is silent, which is why
      // three sightings of the stall produced no diagnosis. See [KernelLog].
      log = LogcatSpaceLifecycleLog,
    )
  }

/** The gateway backed by a real kernel over [sockets]. */
fun baseSpaceKernelGateway(sockets: PerRunningServer<KernelSockets>): KernelGateway = BaseSpaceKernelGateway(
  sharedSockets = sockets,
  gatewayFor = { RealKernelGateway(it, log = LogcatSpaceLifecycleLog) },
  log = LogcatSpaceLifecycleLog,
)

/** The debugger backed by DAP over the same kernel session's control channel, over [sockets]. */
fun baseSpaceKernelDebugger(sockets: PerRunningServer<KernelSockets>): KernelDebugger = BaseSpaceKernelDebugger(
  sharedSockets = sockets,
  debuggerFor = { RealKernelDebugger(it) },
)
