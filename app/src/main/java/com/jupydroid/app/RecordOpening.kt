package com.jupydroid.app

import com.jupydroid.app.domain.model.recentsForOpening
import com.jupydroid.app.domain.port.RecentDocumentsRepository

/**
 * Remembers that the document at [path] was opened, and the folder it was opened from.
 *
 * One seam rather than one per screen, which is the reference's shape too: recents are recorded
 * from the widget manager's `activate-request` hook (`packages/docmanager/src/widgetmanager.ts:268-285`),
 * not from the file browser or the launcher. Ours hangs off navigation, which Home's rows, the
 * explorer's rows and the VIEW-intent import all already go through.
 *
 * Recorded on *every* open, including one that reveals a document already on screen - the
 * reference's hook fires on activation, not only on creation, which is what keeps the list ordered
 * by when something was last worked in rather than by when it was first opened.
 *
 * ```
 * applicationScope.launch { recentDocuments.recordOpening("reports/summary.ipynb") }
 * ```
 */
suspend fun RecentDocumentsRepository.recordOpening(path: String) {
  recentsForOpening(path).forEach { record(it) }
}
