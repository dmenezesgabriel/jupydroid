package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The workspace as files, which is the view Recents cannot give: folders, and the data a notebook
 * wrote beside itself.
 *
 * No path in the key. The explorer keeps its own directory in its `ViewModel`, so descending is
 * not a back-stack push - `filebrowser:go-up` is one directory, not one screen, and the reference
 * navigates the same way: one `DirListing` whose contents are replaced.
 *
 * **The Space it browses is on the key**, the way Winlator puts the container id on the Intent of
 * the activity that runs it (`winlator@fb66541 app/src/main/java/com/winlator/ContainersFragment.java`,
 * `runContainer`). Several Spaces run at once (ADR-0075), so which one was entered cannot be a global;
 * on the key it survives process death with the rest of the stack.
 *
 * ```
 * backStack.add(Explorer("space-1"))
 * ```
 */
@Serializable data class Explorer(val spaceId: String) : NavKey
