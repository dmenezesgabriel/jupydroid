package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.SPACE_WORKSPACE_PATH
import java.io.File

/**
 * The one directory the app, the server and the kernel all mean by "the notebooks".
 *
 * It is the kernel's `HOME` ([SpaceRuntime.environment]), the server's `--ServerApp.root_dir`
 * ([jupyterServerArgs]) and the notebook repository's root. Written down once because three
 * components agreeing by coincidence is not agreement: before Phase X the repository used
 * `filesDir/notebooks` and the kernel used this, so a notebook could not open a file the user
 * could see and the user could not see a file a notebook wrote.
 *
 * **Safe to put user files in**, and that was checked rather than assumed: the bundled rootfs
 * archive carries `home/` and `home/jupydroid/` as bare directory entries with no files beneath
 * them, and `TarZstdExtractor` never wipes its destination - so a rootfs version bump re-extracts
 * over the tree without touching a notebook.
 */
fun notebookWorkspaceIn(baseDir: File): File = File(baseDir, WORKSPACE_RELATIVE)

private val WORKSPACE_RELATIVE = SPACE_WORKSPACE_PATH.removePrefix("/")
