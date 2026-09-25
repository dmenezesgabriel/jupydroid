package com.jupydroid.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The whole `saveBeforeClose` gate (`docmanager/src/dialogs.ts:155`) in one place, shared by the
 * notebook and plain-file editors: owns the ask's visibility, registers the system-Back handler,
 * and hands back the lambda every other exit - a bar's dismiss, `notebook:close-and-shutdown`'s
 * continuation - goes through.
 *
 * [backAsks] is the screen's own call about whether Back reaches the gate at all: the file editor
 * passes its `dirty`, while the notebook stands down whenever one of its contextual handlers -
 * edit mode, the selection bar, the find bar - is up, because those are registered later and an
 * enabled callback answers first.
 *
 * `context.save()` rejecting propagates out of `onClose` (`widgetmanager.ts:353-365`), so the
 * close is the flush's own continuation: a save that cannot be made never runs it and the
 * document stays.
 *
 * The returned lambda is rebuilt on every recomposition, so it always reads the caller's current
 * [dirty] rather than a remembered snapshot of it.
 */
@Composable
fun SaveBeforeCloseGate(
  dirty: Boolean,
  backAsks: Boolean,
  title: String,
  saveNow: (onSaved: () -> Unit) -> Unit,
  discardChanges: () -> Unit,
  onClose: () -> Unit,
): () -> Unit {
  var asking by remember { mutableStateOf(false) }
  BackHandler(enabled = backAsks) { asking = true }
  if (asking) {
    SaveBeforeCloseDialog(
      name = title,
      onSave = {
        asking = false
        saveNow(onClose)
      },
      onDiscard = {
        asking = false
        discardChanges()
        onClose()
      },
      onCancel = { asking = false },
    )
  }
  return { if (dirty) asking = true else onClose() }
}
