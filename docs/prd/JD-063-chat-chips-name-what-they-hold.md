# JD-063 — The Chat's chips name what they hold in full, or say how to see it

Milestone: `M7` · State: in-progress · Depends on: JD-050 · Closes: —

## Outcome

A user reads the agent, model, mode and effort the next question goes with straight from the Chat's
toolbar. Where a chip still has to shorten, a long press shows its full value.

## Demo

On SM-X216B (the chips appear only once a Space's server lists its personas, and only arm64 runs one),
in base with opencode and Claude's ACP adapter installed and signed in:

1. Cold start, open base, More → Chat → New chat, pick Claude. The chips read "Claude",
   "Default (recommended)", and its mode and effort in full, with no ellipsis. The row is as tall as it
   was before.
2. Pick OpenCode. The chips read "OpenCode", "Default / OpenCode/Big Pickle" and "build" in full.
3. Narrow the Chat to the side panel (two panes). A chip that no longer fits ends in an ellipsis.
   Long-pressing it shows "Model • <full name>".
4. Kill and relaunch, then reopen the chat. Steps 1–3 still hold.

Evidence goes in `evidence/jd-063-chat-chips/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyter-ai-persona-manager` | FULL | `src/persona-controls.tsx` (`ControlsRow`, `ControlMenu`), `style/base.css` | Each control is shown at its natural width (value capped at `15ch`, then ellipsis). Controls that do not fit move into a "…" popover. The button's `title` is the control's label |
| `vscode` | FULL | `src/vs/workbench/contrib/chat/browser/widget/input/chatInputPickerResponsiveLayout.ts`, `modelPicker/modelPickerActionItem.ts`, `modelPicker/media/modelPicker.css`, `media/chatPhoneInputPresenter.css` | Pickers stay full width while the lane fits, then compact right-to-left. The model name ellipsizes. The hover always reads "Models • <full name>" |
| `jupyterlab` | ABSENT | — | no chat input with pickers (ADR-0116) |
| `vscode-jupyter` | ABSENT | — | as above |
| `intellij-community` | ABSENT | — | as above |
| `termux-app` | ABSENT | — | as above |
| `winlator` | ABSENT | — | as above |

**Count:** 2 of 7 have this, and they are the two with a chat input at all. Both agree that a value
keeps its full width while there is room. They differ on what happens under pressure. That difference
is the decision below.

## Implementation inventory

### jupyter-ai-persona-manager@d5ed04b

| Concern | Files |
|---|---|
| Fit-or-overflow | `src/persona-controls.tsx` `ControlsRow`: measures a hidden full-width copy, keeps as many controls as fit, puts the rest in `OverflowControlsMenu` |
| Value width | `style/base.css` `.jp-jai-personaControls-control-value`: `max-width: 15ch`, `text-overflow: ellipsis` |
| What the full value is | `currentControlLabel`; button `title={control.label}` |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Fit-or-compact | `chatInputPickerResponsiveLayout.ts` `layout`: all items expanded, then compact one at a time, rightmost first, until it fits |
| Hover | `modelPicker/modelPickerActionItem.ts` `_getHoverContents`: "Models • <name>" |
| Ellipsis | `modelPicker/media/modelPicker.css` (`.model-picker-name .chat-input-picker-label`), `media/chatPhoneInputPresenter.css` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Width while there is room | natural width (both) | — | natural width. Ours capped each chip at `1/n` of the row while room was left |
| Width under pressure | overflow "…" popover (jupyter-ai); compact to an icon (VS Code); ellipsis (VS Code's model name and phone chip) | a popover hides controls behind a new surface. Our chips have no icon to compact to | the longest chip shortens first (a max-min fair share) and ellipsizes. Short chips stay whole |
| Seeing a shortened value | hover/`title` (both); open the list | a list commits the user to a menu | a long-press `PlainTooltip`, "<label> • <value>", always. This is touch's hover, and `CodeBlockActions.kt` already uses one |

Heuristics: visibility of system status (Nielsen #1), so the model in use is readable at a glance, and
recognition rather than recall (#6).

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Width | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/AgentControls.kt`: `weight(1f, fill = false)` on each chip | vscode@3f3380c `chatInputPickerResponsiveLayout.ts` | present but defective: cut at ¼ of the row while it has room |
| Full value | the list, opened by a tap: the value in use first and checked | vscode@3f3380c `modelPickerActionItem.ts` hover | partial: there is no way to read it without opening the menu |

## Deviations

There is no overflow popover and no icon compaction, for the reasons in the trade-offs table.

## Open decisions

None. The person settled them on 2026-10-27: the standard, non-misleading option, with no workaround
and no overengineering.

## Surface

| Layer | Files |
|---|---|
| `ui` (ai extension) | `ui/AgentControls.kt`, new `ui/ChipWidths.kt` (no Compose, JVM-testable) |
| unit tests | `extensions/ai/src/test/java/com/jupydroid/extensions/ai/ui/ChipWidthsTest.kt` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatAgentControlsTest.kt` |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-chips-full-width.yaml`, `scripts/chat-chips-full-width-driver.sh` |

No port changes, so there is no new fake.

## Tasks

- [x] T1 — The chips take their full width while the row has room. Only when they do not fit does the
      longest give way first. Tested on the JVM (`chipWidths`), instrumented at 800 dp, and on
      SM-X216B before and after.
- [ ] T2 — Long-pressing a chip shows "<label> • <full value>" as a plain tooltip ("Agent", "Model",
      the setting's own name). Instrumented long-click test, and a device flow at the side panel's
      width.
- [ ] T3 — The demo on SM-X216B, an ADR for the chip width and the tooltip (superseding ADR-0116's
      "each chip shrinks"), PRD deleted, backlog row shipped.

## Out of scope

- What Claude names its default model ("Default (recommended)" does not say which model it is). That
  name is the agent's own, from its ACP config options.
- An overflow menu or a different toolbar layout.
