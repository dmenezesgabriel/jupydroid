package com.jupydroid.app.domain.model

/**
 * A path that moved, announced so that whatever is open on it can follow.
 *
 * The reference's `Contents.IChangedArgs` with `type: 'rename'`, which the contents layer emits
 * and every open `Context` listens to (`packages/docregistry/src/context.ts:93`). It travels from
 * the repository rather than through the screens because a rename can come from anywhere - the
 * browser beside the document, or a second client on the same tree - and every listener asks the
 * same question of it: [pathAfterRename].
 *
 * ```
 * workspaceRepository.renames.collect { moved -> pathAfterRename(path, moved.from, moved.to) }
 * ```
 */
data class PathRename(val from: String, val to: String)
