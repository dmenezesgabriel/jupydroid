package com.jupydroid.app.data.space

/**
 * The programs the app starts in a Space, named by their paths inside its root.
 *
 * A Space is its own `/` under proot (ADR-0073), so the image's own files are simply themselves:
 * these are the paths `tools/rootfs/Dockerfile` installs them at, and the same ones in every
 * Space. Written down once, so the server, `SHELL`, the kernelspec and the language server cannot
 * each find their own.
 */

/** The interpreter: the server, every kernel and every `PythonRunner` command run as this. */
const val SPACE_INTERPRETER = "/usr/bin/python3"

/** The shell terminado spawns and `SHELL` names; busybox's `sh` applet in the image. */
const val SPACE_SHELL = "/bin/sh"

/** The language server's console script, which proot execs through its `#!` line. */
const val SPACE_PYLSP = "/usr/bin/pylsp"

/** busybox's `timeout` applet, which bounds a Space's startup script from inside the Space. */
const val SPACE_TIMEOUT = "/usr/bin/timeout"
