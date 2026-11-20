package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.model.spaceProcessVariables
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.TerminalGateway
import kotlinx.coroutines.CoroutineScope
import java.io.File

fun baseSpaceDir(context: Context): File = spaceDirIn(spacesDir(context), BASE_SPACE.id)

/**
 * Where notebooks live: the kernel's `HOME`, which is also the server's root and the repository's.
 *
 * See [notebookWorkspaceIn] for why one definition rather than three.
 */
fun notebookWorkspace(context: Context): File = notebookWorkspaceIn(baseSpaceDir(context))

/**
 * Runs jupyter_server in whichever Space `start` is handed, on loopback, under proot, with the
 * settings [settingsFor] answers at that start and the default kernel [defaultKernelFor] does.
 */
fun spaceServerLauncher(
  context: Context,
  settingsFor: suspend (Space) -> SpaceSettings,
  defaultKernelFor: suspend (Space) -> String,
): JupyterServerLauncher = JupyterServerLauncher(
  runtimeFor = { space, settings -> spaceRuntime(context, space, settings.spaceProcessVariables()) },
  dirsFor = { space -> spaceDirs(context, space) },
  settingsFor = settingsFor,
  defaultKernelFor = defaultKernelFor,
  log = LogcatSpaceLifecycleLog,
)

/**
 * Every Space's server, each brought up on first use - or null where no Space can run.
 *
 * Null rather than a throwing object, so the caller decides: on an x86_64 emulator the runtime
 * cannot work at all and the UI is better served by the
 * fakes than by controls that always fail.
 *
 * **One per process, and base's entry shared by every client of base**, because
 * `JupyterServerLauncher.start` begins with `stop()` - see [SpaceServer]. base installs on first
 * use; any other Space had the image copied into it when it was created (`DiskSpaceRepository`),
 * and gets it again only if it has none, as a restore leaves it
 * ([SpaceImageInstaller.extractIfAbsent]). [watchScope] is where each server's exit is waited on, so it must
 * outlive every screen. Each start reads the Space's settings from [spaceRepository] - the same
 * record the home's Settings writes - so what was saved is what the next start honours - and its
 * default kernel from [kernelSpecsOnDisk], the catalogue a stopped Space's picker reads.
 */
fun spaceServers(
  context: Context,
  watchScope: CoroutineScope,
  spaceRepository: SpaceRepository,
  kernelSpecsOnDisk: KernelSpecs,
): JupyterSpaceServers? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return JupyterSpaceServers { id, reportRunning ->
    SpaceServer(
      space = spaceWithId(id),
      installSpace = installOnStartOf(context, id),
      launcher = spaceServerLauncher(
        context,
        settingsFor = { space -> spaceRepository.storedSettingsOf(space) },
        defaultKernelFor = { space -> kernelSpecsOnDisk.specs(space).default },
      ),
      watchScope = watchScope,
      reportRunning = reportRunning,
      log = LogcatSpaceLifecycleLog,
      // The Space's spare kernel, started while the user walks to the first cell (ADR-0080).
      prestartKernel = { connection -> OkHttpKernelLifecycle(connection).startBareKernel() },
    )
  }
}

/**
 * `pylsp` out of a Space's own image as the app's [LanguageServerPort], over stdio - one process
 * per Space, each in that Space's own environment and workspace ([startPylsp]).
 *
 * The Space's own variables reach it as they reach a kernel, read from [spaceRepository] at each
 * spawn, so a `PYTHONPATH` resolves an `import` the same in the editor as in a cell. A spawn never
 * holds stale ones: Settings is editable only while the Space is stopped, and leaving a Space stops
 * every `pylsp` (`StdioLanguageServer.stopAll`).
 *
 * The console script execs under proot through its `#!` line ([SpaceRuntime]'s
 * `execProcessBuilder`, which must not prefix the interpreter), no `jupyter_server` in between.
 *
 * Null where no Space can run, the same guard [spaceServers] keeps. The process is lazier
 * than the server's own: `installSpace` runs on the port's *first use*, so an app that only
 * browses notebooks never pays for a Python runtime it did not ask for.
 */
fun baseSpaceLanguageServerPort(context: Context, spaceRepository: SpaceRepository): LanguageServerPort? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return StdioLanguageServer(
    installSpace = { baseSpaceInstaller(context).installIfNeeded() },
    spawn = { space, _ ->
      val variables = spaceRepository.storedSettingsOf(space).spaceProcessVariables()
      startPylsp(spaceRuntime(context, space, variables), spaceDirs(context, space))
    },
    log = LogcatSpaceLifecycleLog,
  )
}

/** [space]'s settings as its `config.json` holds them now - what the home's Settings last saved. */
internal suspend fun SpaceRepository.storedSettingsOf(space: Space): SpaceSettings =
  spaces().spaceWithId(space.id).settings

/** The exporter backed by the nbconvert endpoints of [server]'s `jupyter_server`. */
fun baseSpaceNotebookExporter(server: SpaceServer): NotebookExporter =
  BaseSpaceNotebookExporter(server) { OkHttpNotebookExporter(it) }

/** The terminals of [server]'s `jupyter_server`, over one socket per attached shell. */
fun baseSpaceTerminalGateway(server: SpaceServer): TerminalGateway = BaseSpaceTerminalGateway(
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
internal fun deviceAbi(): SpaceAbi = SpaceAbi.of(Build.SUPPORTED_ABIS.first())
