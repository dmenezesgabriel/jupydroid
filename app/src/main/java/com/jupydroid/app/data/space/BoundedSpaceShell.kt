package com.jupydroid.app.data.space

import kotlin.time.Duration

/**
 * [script] in the Space's shell, bounded by busybox's `timeout`, as argv for
 * [SpaceRuntime.execProcessBuilder]: [arg0] names the outer shell in `ps`, and [script] reaches
 * the inner shell as `$1`, so no quoting of it is needed.
 *
 * **An outer shell runs `timeout` and exits with its status**, because proot passes on a tracee's
 * exit status but reports one killed by a signal as `0` - so a script killed at its bound would
 * read as a success (`SpaceStartupScriptDeviceTest.aScriptPastItsTimeoutIsKilledWithWhatItStartedAndExits137`).
 * The outer shell is never signalled; it sees `137` as an ordinary status. The trailing `exit $?`
 * is what keeps it there: busybox `ash` execs the last command of a `-c` string in place of itself,
 * which would make `timeout` proot's tracee again. The bound is enforced inside the Space because
 * its SIGKILL lands on the script, a tracee, while the app's only handle is proot, which a kill
 * would orphan the script from.
 */
internal fun boundedShellArgs(arg0: String, script: String, timeout: Duration): List<String> = listOf(
  SPACE_SHELL,
  "-c",
  "$SPACE_TIMEOUT -s KILL ${timeout.inWholeSeconds} $SPACE_SHELL -c \"\$1\"; exit \$?",
  arg0,
  script,
)
