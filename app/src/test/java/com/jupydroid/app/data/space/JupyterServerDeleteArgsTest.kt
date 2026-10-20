package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file the server deletes is gone, as one the explorer deletes is.
 *
 * jupyter_server's `FileContentsManager.delete_to_trash` is true by default, and the rootfs ships
 * `send2trash`, so a `DELETE api/contents/<path>` would move the file into `~/.local/share/Trash`:
 * hidden from the explorer, never emptied, and not what the "permanently delete" confirm says
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py`, `delete_file`).
 */
class JupyterServerDeleteArgsTest {
  @Test
  fun theServerDeletesAFileRatherThanSendItToTheTrash() {
    val args =
      jupyterServerArgs(rootDir = "/home/jupydroid", token = "t", idleShutdownMinutes = DEFAULT_IDLE_SHUTDOWN_MINUTES)

    assertTrue("expected delete_to_trash off, got $args", args.contains("--FileContentsManager.delete_to_trash=False"))
  }
}
