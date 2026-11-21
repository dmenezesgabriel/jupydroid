package com.jupydroid.app.data.space

import android.content.Context
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.model.spaceProcessVariables
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.time.Duration

/**
 * [SpaceShell] over proot: [SpaceShell.run]'s script runs in the Space's own `/bin/sh`, bounded
 * by busybox's `timeout` inside the Space ([boundedShellArgs]), its process killed with everything
 * it started by proot's `--kill-on-exit`, and its merged output streamed line by line.
 *
 * The Space is prepared in the order a start needs it: the settings are read first, so an id
 * nothing lists is refused before [installSpace] could extract an image into a directory no
 * record names; then the image and the writable directories, and only then the process.
 * Cancelling the collection destroys the process - forcibly, because proot ignores SIGTERM
 * (`JupyterServerLauncher`) - and whatever the script left running stays bounded by the
 * Space-side `timeout`, the scripts being idempotent so a re-run resumes.
 */
class ProotSpaceShell(
  private val installSpace: suspend (spaceId: String) -> Unit,
  private val settingsFor: suspend (space: Space) -> SpaceSettings,
  private val runtimeFor: (space: Space, settings: SpaceSettings) -> SpaceRuntime,
  private val dirsFor: (space: Space) -> SpaceDirs,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpaceShell {
  /**
   * The prepare half of [run], one collection at a time: the settings section probes every
   * catalog harness the moment it opens, so several runs prepare the same Space together, and
   * [installSpace]'s atomic kernelspec rewrite (`.~kernel.json`, `SpaceKernelSpec.install`) and
   * [SpaceDirs.ensure]'s `isDirectory || mkdirs()` check both lose to a sibling's interleaving.
   */
  private val prepareMutex = Mutex()

  override fun run(spaceId: String, script: String, timeout: Duration): Flow<SpaceShellEvent> = callbackFlow {
    val space = spaceWithId(spaceId)
    val settings = settingsFor(space)
    val process = prepareMutex.withLock {
      withContext(ioDispatcher) { installSpace(spaceId) }
      withContext(ioDispatcher) {
        val dirs = dirsFor(space)
        dirs.ensure()
        runtimeFor(space, settings)
          .execProcessBuilder(boundedShellArgs(SHELL_ARG0, script, timeout), killOnExit = true)
          .redirectErrorStream(true)
          .start()
      }
    }
    drain(process, spaceId)
    awaitClose {
      runCatching { process.inputStream.close() }
      if (process.isAlive) process.destroyForcibly()
    }
  }

  /**
   * The process's merged output as [SpaceShellEvent.Output] lines, then its exit status as
   * [SpaceShellEvent.Finished]. A daemon thread, because a `readLine` blocked on a silent script
   * must not hold the collector; `trySendBlocking` keeps a fast script from outrunning it, and a
   * closed pipe on cancel is the stream's end, not an error.
   */
  private fun ProducerScope<SpaceShellEvent>.drain(process: Process, spaceId: String) {
    thread(isDaemon = true, name = "space-shell-$spaceId") {
      try {
        process.inputStream.bufferedReader().forEachLine { line ->
          trySendBlocking(SpaceShellEvent.Output(line))
        }
        // `trySendBlocking`, not `trySend`: a fast script can leave the channel's buffer full of
        // unconsumed lines, and a dropped Finished would end the run with no terminal event.
        trySendBlocking(SpaceShellEvent.Finished(process.waitFor()))
        close()
      } catch (e: IOException) {
        // A broken pipe mid-run is the flow's error, not a clean end; on cancel the channel is
        // already closed and this is a no-op.
        close(e)
      }
    }
  }

  private companion object {
    /** The outer shell's `$0`, which names it in `ps` and in any message the shell prints. */
    const val SHELL_ARG0 = "space-shell"
  }
}

/**
 * [SpaceShell] over the real proot, or null where no Space can run - the same guard
 * [spaceServers] and [baseSpaceLanguageServerPort] keep, so an x86_64 device falls back to the
 * fake rather than a control that always fails.
 *
 * Nothing is ensured or installed until a run's flow is collected: the image install is
 * [installOnStartOf]'s - base's on first use, any other Space's `extractIfAbsent` - and each run
 * reads the Space's settings fresh, so what Settings last saved is what the script's environment
 * carries.
 */
fun spaceShell(
  context: Context,
  spaceRepository: SpaceRepository,
  nativeLibraryDir: File = File(context.applicationInfo.nativeLibraryDir),
): SpaceShell? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return ProotSpaceShell(
    installSpace = { id -> installOnStartOf(context, id).invoke() },
    settingsFor = { space -> spaceRepository.storedSettingsOf(space) },
    runtimeFor = { space, settings ->
      spaceRuntime(context, space, settings.spaceProcessVariables(), nativeLibraryDir)
    },
    dirsFor = { space -> spaceDirs(context, space) },
  )
}
