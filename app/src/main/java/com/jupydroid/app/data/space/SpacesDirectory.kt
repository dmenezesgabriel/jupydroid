package com.jupydroid.app.data.space

import android.content.Context
import java.io.File

/**
 * Where every Space lives: one directory per Space, which is what a Space *is*.
 *
 * One definition rather than two, now that both [baseSpaceDir] and `DiskSpaceRepository` need the
 * segment - the same reason [notebookWorkspaceIn] has a file of its own.
 *
 * ```
 * DiskSpaceRepository(spacesDir(context))
 * ```
 */
fun spacesDir(context: Context): File = spacesDirIn(context.filesDir)

/**
 * [spacesDir] for a caller that holds `filesDir` but not the app's [Context]: a `BackupAgent` is
 * its own `Context`, built by the platform, and walks the same tree.
 *
 * ```
 * spacesDirIn(agent.filesDir)
 * ```
 */
fun spacesDirIn(filesDir: File): File = File(filesDir, "spaces")

/**
 * The directory of the Space named [spaceId]: its id *is* the directory's name, which is how
 * `DiskSpaceRepository`'s scan derives the id back (ADR-0010). One home for the convention, so the
 * repository that creates a Space and the runtime that launches one cannot disagree on where it is.
 *
 * ```
 * spaceDirIn(spacesDir(context), BASE_SPACE.id)
 * ```
 */
fun spaceDirIn(spacesDir: File, spaceId: String): File = File(spacesDir, spaceId)
