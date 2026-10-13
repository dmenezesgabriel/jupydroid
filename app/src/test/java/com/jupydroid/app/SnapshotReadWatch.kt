package com.jupydroid.app

import androidx.compose.runtime.snapshots.Snapshot

/**
 * Whether something Compose read is invalidated by what happens afterwards - the question a
 * composable that read it asks before it redraws, answered without a composition.
 *
 * The [read] runs under a read observer, the way a composition records the state objects it read;
 * [sawChange] then flushes the global snapshot's applied writes and reports whether any of them
 * touched one of those objects. A read of a plain `var` records nothing, so it never sees a change.
 *
 * ```
 * val watch = SnapshotReadWatch { document.path }
 * workspace.rename("a.py", "b.py")
 * assertTrue(watch.sawChange())
 * ```
 */
class SnapshotReadWatch(read: () -> Unit) {
  private val readObjects = mutableSetOf<Any>()
  private var changed = false
  private val registration = Snapshot.registerApplyObserver { applied, _ ->
    if (applied.any { it in readObjects }) changed = true
  }

  init {
    Snapshot.observe(readObserver = { readObjects += it }, block = read)
  }

  /** Flushes pending writes, stops watching, and answers whether a write reached the read. */
  fun sawChange(): Boolean {
    Snapshot.sendApplyNotifications()
    registration.dispose()
    return changed
  }
}
