package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide explorer preferences - one field today: whether the Recents section is shut.
 *
 * Its own type rather than a field on [EditorSettings] or [TerminalSettings]: a collapsed section
 * is neither an editor preference nor a terminal one (`AGENTS.md`'s "one home per kind of logic"),
 * which is deliberate rather than growing whichever store already existed.
 *
 * **Shut by default**: open, the section's rows push the listing down a phone's explorer before
 * anything was asked of them. That is the reference's rule for a secondary list in its Explorer
 * container - Outline and Timeline declare `collapsed: true` (vscode@3f3380c668d0
 * `src/vs/workbench/contrib/outline/browser/outline.contribution.ts`,
 * `src/vs/workbench/contrib/timeline/browser/timeline.contribution.ts`), which a view keeps until a
 * stored state overrides it (`viewContainerModel.ts:517`). Shut rather than hidden, as Open Editors
 * is there: the heading is the only way to the section on a phone.
 *
 * New fields must carry a default: an `explorer-settings.json` written before they existed is read
 * with `ignoreUnknownKeys` and has nothing to say about them.
 */
@Serializable
data class ExplorerSettings(val recentsCollapsed: Boolean = true)
