package com.jupydroid.app.domain.model

import java.time.Instant

/**
 * A point a file can be put back to, as `list_checkpoints` answers it: an [id] and when it was
 * made ([lastModified], the checkpoint copy's own mtime).
 *
 * The shipped rootfs keeps one per file, always under the id `"checkpoint"`
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`), so the
 * id is carried rather than assumed: it is what a restore is handed back.
 *
 * ```
 * val point = repository.createCheckpoint("notes.py")   // Checkpoint("checkpoint", <now>)
 * ```
 */
data class Checkpoint(val id: String, val lastModified: Instant)
