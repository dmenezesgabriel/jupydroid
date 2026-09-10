package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide explorer preferences - one field today: whether the Recents section is shut.
 *
 * Its own type rather than a field on [EditorSettings] or [TerminalSettings]: a collapsed section
 * is neither an editor preference nor a terminal one (`CLAUDE.md`'s "one home per kind of logic"),
 * and P4j-3c set that precedent deliberately rather than growing whichever store already existed.
 *
 * **False by default**, which is the reference's own: a view's `collapsed` is
 * `!!viewDescriptor.collapsed` unless a stored state overrides it
 * (`viewContainerModel.ts:517`), and no descriptor here asks to arrive shut.
 *
 * New fields must carry a default: an `explorer-settings.json` written before they existed is read
 * with `ignoreUnknownKeys` and has nothing to say about them.
 */
@Serializable
data class ExplorerSettings(val recentsCollapsed: Boolean = false)
