package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.SpaceInterpreter
import kotlinx.coroutines.flow.Flow

/**
 * Kernel creation as its own act: an existing interpreter becomes a kernelspec every notebook's
 * picker offers, with no environment made - what [SpaceEnvironments.register] deliberately is
 * not, that one writing a spec for an environment already made and this one for a bare
 * interpreter.
 *
 * The act runs as a script inside the Space's own shell - the same `jupydroid-env register` the
 * terminal runs - so it answers a stopped Space and streams what the script prints.
 * [SpaceShellEvent.Finished]'s exit status is the script's own verdict.
 *
 * ```
 * spaceKernelRegistration.interpreters("base")   // [/usr/bin/python3, /opt/gnu/python/bin/python3.12]
 * spaceKernelRegistration.register("base", "/usr/bin/python3").toList()
 * ```
 */
interface SpaceKernelRegistration {
  /**
   * The interpreters [spaceId] offers for registration, each with the name of the spec that runs
   * it or null - the spec's `argv[0]` being the answer, so it answers a stopped Space and never
   * starts one.
   *
   * @throws IllegalArgumentException when no Space has that id.
   */
  suspend fun interpreters(spaceId: String): List<SpaceInterpreter>

  /**
   * Makes [interpreter] - a path a process in the Space execs - a kernel of [spaceId]'s server,
   * streaming the script's output lines then one [SpaceShellEvent.Finished]. An env's own
   * `bin/python` registers the environment; the refusals are the script's - the name held, no
   * ipykernel to import, the path nothing at all - not this port's.
   *
   * @throws IllegalArgumentException collected when no Space has [spaceId].
   */
  fun register(spaceId: String, interpreter: String): Flow<SpaceShellEvent>
}
