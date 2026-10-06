package com.jupydroid.app.domain.model

/**
 * Every name the app sets on a Space's processes, which a person's own variables may not.
 *
 * Each one is what makes a Space's disk and its processes agree - a `HOME` or `TMPDIR` the
 * directory never had, a `PATH` that misses `pkgs/bin`, a `LD_PRELOAD` without the SIGSYS
 * handler - so none is the person's to override (ADR-0072). Every reference lets a user value win
 * (`winlator@fb66541 GuestProgramLauncherComponent.java` `putAll`s its bag over the derived
 * set); this is the deliberate deviation.
 *
 * A name that only some rootfs builds set - the preloads, the nameservers - is locked whichever
 * rootfs this is. `SpaceRuntimeTest.derivedVariableNamesAreExactlyWhatEveryProcessIsGiven` holds
 * this set equal to what `SpaceRuntime` actually sets, so adding a variable there without adding it
 * here fails a test.
 */
val DERIVED_VARIABLE_NAMES: Set<String> = setOf(
  "PYTHONFAULTHANDLER",
  "HOME",
  "TMPDIR",
  "JUPYTER_RUNTIME_DIR",
  "SHELL",
  "PYTHONUSERBASE",
  "PIP_USER",
  "PIP_ONLY_BINARY",
  "PIP_BREAK_SYSTEM_PACKAGES",
  "PATH",
  "LD_PRELOAD",
  "JUPYDROID_SIGSYS_LOG",
  "JUPYDROID_NAMESERVERS",
  "PROOT_LOADER",
  "PROOT_TMP_DIR",
)

/** What a variable's name must look like: the portable shell name (POSIX.1-2017 §3.235). */
const val VARIABLE_NAME_SHAPE: String = "[A-Za-z_][A-Za-z0-9_]*"

private val VARIABLE_NAME = Regex(VARIABLE_NAME_SHAPE)

/**
 * Why [name] cannot be one of a Space's own variables, or null when it can.
 *
 * One rule for the port and the Settings dialog, so a name the dialog lets through is never one
 * the port refuses - [IDLE_SHUTDOWN_MINUTES_RANGE]'s arrangement, for the other field.
 *
 * ```
 * variableNameRefusal("HOME")       // "'HOME' is derived from the Space's directory and cannot be set"
 * variableNameRefusal("GREETING")   // null
 * ```
 */
fun variableNameRefusal(name: String): String? = when {
  name in DERIVED_VARIABLE_NAMES -> "'$name' is derived from the Space's directory and cannot be set"
  !VARIABLE_NAME.matches(name) -> "variable name '$name'; expected $VARIABLE_NAME_SHAPE"
  else -> null
}

/**
 * Why [value] cannot be the value of [name], or null when it can. A NUL ends a C string, so no
 * process environment can carry one - `ProcessBuilder` throws on it at start.
 */
fun variableValueRefusal(name: String, value: String): String? =
  if ('\u0000' in value) "value of '$name' holds a NUL character; expected text without one" else null
