package com.jupydroid.app.data.space

import java.io.File

/**
 * A `#!/bin/sh` script installed as `libproot.so`, so a JVM test can start a real Space process
 * without proot or an image.
 *
 * It reads the options [ProotCommand.argv] passes, then does the two translations a test can
 * observe: it starts in `<root><-w>` and execs `<root><program>`. Nothing else is translated - an
 * in-root path in an environment variable still names the host's `/` - so it exports the root as
 * `FAKE_PROOT_ROOT` for a fake program to prefix where real proot would translate. Only shell
 * builtins, because the Space environment replaces `PATH` with in-root directories.
 */
object FakeProotScript {
  /** Installs the script as [ProotCommand.PROOT] in [nativeLibraryDir], which it returns. */
  fun installIn(nativeLibraryDir: File): File = nativeLibraryDir.apply {
    mkdirs()
    File(this, ProotCommand.PROOT).apply {
      writeText(SCRIPT)
      setExecutable(true)
    }
  }

  /** Writes an executable `#!/bin/sh` [body] at in-root [program] of [root], standing in for it. */
  fun program(root: File, program: String, body: String): File = File(root, program.removePrefix("/")).apply {
    parentFile!!.mkdirs()
    writeText("#!/bin/sh\n${body.trimIndent()}\n")
    setExecutable(true)
  }

  private val SCRIPT =
    """
    #!/bin/sh
    root=; cwd=/
    while [ ${'$'}# -gt 0 ]; do
      case "${'$'}1" in
        -r) root=${'$'}2; shift 2 ;;
        -w) cwd=${'$'}2; shift 2 ;;
        -b) shift 2 ;;
        -0|--kill-on-exit) shift ;;
        *) break ;;
      esac
    done
    cd "${'$'}root${'$'}cwd" || exit 125
    program=${'$'}1; shift
    FAKE_PROOT_ROOT=${'$'}root; export FAKE_PROOT_ROOT
    exec "${'$'}root${'$'}program" "${'$'}@"
    """.trimIndent() + "\n"
}
