package com.jupydroid.app.data.space

import android.content.Context
import java.io.File

/**
 * Where every Space lives: one directory per Space, which is what a Space *is* (P3a).
 *
 * One definition rather than two, now that both [baseSpaceDir] and `DiskSpaceRepository` need the
 * segment - the same reason [notebookWorkspaceIn] and [spaceShellIn] each have a file of their own.
 *
 * ```
 * DiskSpaceRepository(spacesDir(context))
 * ```
 */
fun spacesDir(context: Context): File = File(context.filesDir, "spaces")
