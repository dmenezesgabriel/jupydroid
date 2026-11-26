package com.jupydroid.app.domain.model

/**
 * A Python interpreter a Space offers for kernel registration - one of its own pythons, named by
 * the path a process in the Space execs. Unlike [PythonEnvironment], it is no environment the
 * scan finds: the Space's pythons are known, and registering one is kernel creation as its own
 * act - no `pyvenv.cfg`, no env to name the spec after.
 *
 * ```
 * SpaceInterpreter(path = "/usr/bin/python3", isGnu = false)
 * ```
 *
 * @property path the interpreter as a process in the Space names it - `/usr/bin/python3`.
 * @property isGnu whether it is the gnu payload's interpreter, so a kernel started on it needs the
 *   glibc half of the Space's environment split rather than the server's musl one (ADR-0166).
 * @property registeredAs the name of the kernelspec that runs this interpreter, or null when none
 *   does - the spec's `argv[0]` is the answer, as it is for an environment's.
 */
data class SpaceInterpreter(val path: String, val isGnu: Boolean, val registeredAs: String? = null)
