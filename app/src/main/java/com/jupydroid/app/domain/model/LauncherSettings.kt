package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide launcher bookkeeping - one field today: whether the launcher has ever presented
 * itself.
 *
 * Its own type rather than a field on [ExplorerSettings] or [EditorSettings]: a first-run flag is
 * neither an explorer preference nor an editor one (`AGENTS.md`'s "one home per kind of logic"),
 * following the precedent `ExplorerSettings` records.
 *
 * **False by default** - a fresh install has never been shown the launcher, and it is the first
 * run's own presentation that flips it. New fields must carry a default: a
 * `launcher-settings.json` written before they existed is read with `ignoreUnknownKeys` and has
 * nothing to say about them.
 */
@Serializable
data class LauncherSettings(val launcherSeen: Boolean = false)
