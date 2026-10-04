package com.jupydroid.app.data.space

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguageServerPort
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
  archive = { context.assets.open("spaces/base/rootfs-${abi.androidAbi}.tzst") },
  baseDir = baseSpaceDir(context),
  nativeLoader = File(context.applicationInfo.nativeLibraryDir, "libloader.so"),
  nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
  nativeBusyBox = File(context.applicationInfo.nativeLibraryDir, "libbusybox.so"),
  nativeTrampoline = File(context.applicationInfo.nativeLibraryDir, "libshebang.so"),
  loaderLinkPath = abi.loaderLinkPath,
)

/** Command line and environment for the installed base Space's interpreter. */
fun baseSpaceRuntime(context: Context): SpaceRuntime = SpaceRuntime(
  spaceDir = baseSpaceDir(context),
  nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
  dnsServers = androidDnsServerSource(context),
)

/**
 * The device's current DNS servers, read fresh on every call rather than cached: they can
 * legitimately differ across networks (home Wi-Fi vs. mobile data), and [SpaceRuntime] reads
 * this on every process launch rather than once at install.
 */
fun androidDnsServerSource(context: Context): DnsServerSource = DnsServerSource {
  val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  val link = connectivityManager?.getLinkProperties(connectivityManager.activeNetwork)
  link?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
}

fun baseSpaceDir(context: Context): File = File(spacesDir(context), BASE_SPACE.id)

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
 * cannot work at all and the UI is better served by the
 * fakes than by controls that always fail.
 *
 * **One per process, and shared by every client of the Space**, because
 * `JupyterServerLauncher.start` begins with `stop()` - see [BaseSpaceServer]. [launcher] is the
 * caller's for the same reason from the other side: the idle backstop stops what this starts.
 */
fun baseSpaceServer(context: Context, launcher: JupyterServerLauncher): BaseSpaceServer? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return BaseSpaceServer(
    installSpace = { baseSpaceInstaller(context).installIfNeeded() },
    launcher = launcher,
    log = LogcatSpaceLifecycleLog,
  )
}

/**
 * The base Space's `pylsp` as the app's [LanguageServerPort], over stdio.
 *
 * The console script execs straight through the `shebang-exec` trampoline ([SpaceRuntime]'s
 * `execProcessBuilder`, which must not prefix the interpreter), no `jupyter_server` in between.
 *
 * Null where no Space can run, the same guard [baseSpaceServer] keeps. The process is lazier
 * than the server's own: `installSpace` runs on the port's *first use*, so an app that only
 * browses notebooks never pays for a Python runtime it did not ask for.
 */
fun baseSpaceLanguageServerPort(context: Context): LanguageServerPort? {
  if (runCatching { deviceAbi() }.isFailure) return null
  val runtime = baseSpaceRuntime(context)
  return StdioLanguageServer(
    installSpace = { baseSpaceInstaller(context).installIfNeeded() },
    spawn = { installed ->
      runtime.execProcessBuilder(listOf(StdioLanguageServer.pylspIn(installed.baseDir).path))
        .directory(notebookWorkspaceIn(installed.baseDir))
        .start()
    },
    log = LogcatSpaceLifecycleLog,
  )
}

/** The exporter backed by the nbconvert endpoints of [server]'s `jupyter_server`. */
fun baseSpaceNotebookExporter(server: BaseSpaceServer): NotebookExporter =
  BaseSpaceNotebookExporter(server) { OkHttpNotebookExporter(it) }

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
