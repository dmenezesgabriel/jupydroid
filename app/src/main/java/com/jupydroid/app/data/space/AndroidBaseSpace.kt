package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.TerminalGateway
import java.io.File

/**
 * Wires [BaseSpaceInstaller] to the Android app's own directories.
 *
 * Kept apart from the installer so the installer stays a plain JVM class that unit
 * tests can drive against a temp folder, with no Context in sight.
 */
fun baseSpaceInstaller(context: Context, abi: SpaceAbi = deviceAbi()): BaseSpaceInstaller = BaseSpaceInstaller(
  archive = { context.assets.open("spaces/base/rootfs-${abi.androidAbi}.tgz") },
  baseDir = baseSpaceDir(context),
  nativeLoader = File(context.applicationInfo.nativeLibraryDir, "libloader.so"),
  nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
  nativeBusyBox = File(context.applicationInfo.nativeLibraryDir, "libbusybox.so"),
  nativeTrampoline = File(context.applicationInfo.nativeLibraryDir, "libshebang.so"),
  loaderLinkPath = abi.loaderLinkPath,
)

/** Command line and environment for the installed base Space's interpreter. */
fun baseSpaceRuntime(context: Context): SpaceRuntime = SpaceRuntime(
  baseDir = baseSpaceDir(context),
  nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
)

fun baseSpaceDir(context: Context): File = File(context.filesDir, "spaces/base")

/**
 * Where notebooks live: the kernel's `HOME`, which is also the server's root and the repository's.
 *
 * See [notebookWorkspaceIn] for why one definition rather than three.
 */
fun notebookWorkspace(context: Context): File = notebookWorkspaceIn(baseSpaceDir(context))

/** Runs jupyter_server out of the installed base Space, on loopback. */
fun baseSpaceServerLauncher(context: Context): JupyterServerLauncher = JupyterServerLauncher(
  runtime = baseSpaceRuntime(context),
  runtimeDir = File(baseSpaceDir(context), "run"),
  rootDir = notebookWorkspace(context),
  shell = spaceShellIn(baseSpaceDir(context)),
)

/**
 * The base Space's server, brought up on first use - or null where no Space can run.
 *
 * Null rather than a throwing object, so the caller decides: on an x86_64 emulator the runtime
 * cannot work at all (evidence/step8.3/musl-is-arm64-only.txt) and the UI is better served by the
 * fakes than by controls that always fail.
 *
 * **One per process, and shared by every client of the Space**, because
 * `JupyterServerLauncher.start` begins with `stop()` - see [BaseSpaceServer]. [launcher] is the
 * caller's for the same reason from the other side: P1c's backstop stops the process this starts.
 */
fun baseSpaceServer(context: Context, launcher: JupyterServerLauncher): BaseSpaceServer? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return BaseSpaceServer(
    installSpace = { baseSpaceInstaller(context).installIfNeeded() },
    launcher = launcher,
    log = LogcatSpaceLifecycleLog,
  )
}

/** The exporter backed by the nbconvert endpoints of [server]'s `jupyter_server`. */
fun baseSpaceNotebookExporter(server: BaseSpaceServer): NotebookExporter =
  BaseSpaceNotebookExporter(server) { OkHttpNotebookExporter(it) }

/** The gateway backed by a real kernel in [server]'s Space. */
fun baseSpaceKernelGateway(server: BaseSpaceServer): KernelGateway = BaseSpaceKernelGateway(
  server = server,
  gatewayFor = { connection ->
    RealKernelGateway(
      opener = OkHttpKernelSocketOpener(connection),
      // The lifecycle gets the same log: a shutdown that found the session already gone is the
      // one thing it reports rather than raises, and on device that line is the only account of it.
      lifecycle = OkHttpKernelLifecycle(connection, log = LogcatSpaceLifecycleLog),
      // The same tag the rest of the runtime reports under, so a stall is one filter to read:
      // `adb logcat -s JupydroidSpace`. Without this the execute path is silent, which is why
      // three sightings of the stall produced no diagnosis. See [KernelLog].
      log = LogcatSpaceLifecycleLog,
    )
  },
  log = LogcatSpaceLifecycleLog,
)

/** The terminals of [server]'s `jupyter_server`, over one socket per attached shell. */
fun baseSpaceTerminalGateway(server: BaseSpaceServer): TerminalGateway = BaseSpaceTerminalGateway(
  server = server,
  gatewayFor = { connection ->
    RealTerminalGateway(
      opener = OkHttpTerminalSocketOpener(connection),
      // The lifecycle gets the log for the one thing it reports rather than raises: a terminal that
      // was already gone when we asked to end it, which on device is the only account of it.
      lifecycle = OkHttpTerminalLifecycle(connection, log = LogcatSpaceLifecycleLog),
      // The same tag the rest of the runtime reports under, so a shell that shows nothing is one
      // filter to read: `adb logcat -s JupydroidSpace`. See [TerminalLog].
      log = LogcatSpaceLifecycleLog,
    )
  },
)

/**
 * The rootfs must match the ABI Android picked for the APK's native libraries, which
 * is the first supported one - not necessarily the only one the device can run.
 */
private fun deviceAbi(): SpaceAbi = SpaceAbi.of(Build.SUPPORTED_ABIS.first())
