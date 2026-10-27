# JD-063 — The Chat's chips name what they hold in full, or say how to see it

Milestone: `M7` · State: in-progress · Depends on: JD-050 · Closes: —

## Outcome

A user reads the agent, model, mode and effort the next question goes with straight from the Chat's
toolbar. Where the chips cannot all fit, one chip names the model in full and opens every control at once.

## Demo

On SM-X216B (the chips appear only once a Space's server lists its personas, and only arm64 runs one),
in base with opencode and Claude's ACP adapter installed and signed in:

1. Cold start, open base, More → Chat → New chat, pick Claude. The chips read "Claude",
   "Default (recommended)", and its mode and effort in full, with no ellipsis. The row is as tall as it
   was before.
2. Pick OpenCode. The chips read "OpenCode", "Default / OpenCode/Big Pickle" and "build" in full.
3. Turn the tablet to landscape, so the Chat is the side panel. The chips that do not fit become one
   chip naming the model. Tapping it opens one menu: a section each for the agent, the model and each
   setting, the value in use checked. A pick there is one tap.
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
| Phone: one chip, one sheet | `chatPhoneInputPresenter.ts` `_updateTrigger`: the mode's icon, then the model's name as the chip's only text, and its aria label "<mode>, <model>"; a tap opens one sheet for mode and model (`showCombinedModeAndModelSheet`) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Width while there is room | natural width (both) | — | natural width. Ours capped each chip at `1/n` of the row while room was left |
| Width under pressure | overflow "…" popover (jupyter-ai); compact to an icon (VS Code); ellipsis (VS Code's model name and phone chip) | our chips have no icon to compact to | none in the row: chips stay whole until their whole widths do not fit, then the one-chip form below. Decided with the person on 2026-10-27 over T1's longest-first shrink, which cut a name to keep a short chip whole |
| When they still do not fit | one "…" menu, a labelled section per control (jupyter-ai `OverflowControlsMenu`); one chip naming the model, opening one sheet for mode and model (VS Code's phone presenter); a hover (desktop only in both) | a hover has no touch form anyone can discover, so a long-press tooltip would hide the value behind a gesture nothing advertises | VS Code's phone shape, on the evidence of both: one chip whose text is the model's name and whose tap opens one menu, a labelled section per control, the value in use checked. A pick costs the two taps it costs today |

Heuristics: visibility of system status (Nielsen #1), so the model in use is readable at a glance, and
recognition rather than recall (#6).

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Width | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/AgentControls.kt`: `weight(1f, fill = false)` on each chip | vscode@3f3380c `chatInputPickerResponsiveLayout.ts` | present but defective: cut at ¼ of the row while it has room |
| Full value | the list, opened by a tap: the value in use first and checked | vscode@3f3380c `modelPickerActionItem.ts` hover | partial: there is no way to read it without opening the menu |

## Deviations

There is no icon compaction: ACP models and modes carry no icon, and a glyph we drew would name nothing.

## Open decisions

None. The person asked on 2026-10-27 for the choice to be made on evidence, for clarity and
discoverability, without raising a touch's cost, and without misleading. The trade-offs table records it.

## Surface

| Layer | Files |
|---|---|
| `ui` (ai extension) | `ui/AgentControls.kt`, new `ui/ChatControl.kt` (no Compose, JVM-testable), new `ui/ControlsChip.kt` |
| unit tests | `extensions/ai/src/test/java/com/jupydroid/extensions/ai/ui/ChatControlTest.kt` |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ui/shell/ChatAgentControlsTest.kt`, `ChatControlsChipTest.kt` |
| `.maestro/` flow + `scripts/` driver | `.maestro/chat-chips-full-width.yaml`, `scripts/chat-chips-full-width-driver.sh` |

No port changes, so there is no new fake.

## Tasks

- [x] T1 — The chips take their full width while the row has room. Only when they do not fit does the
      longest give way first. Tested on the JVM (`chipWidths`), instrumented at 800 dp, and on
      SM-X216B before and after.
- [x] T2 — When the chips' whole widths do not fit, they become one chip naming the model, whose tap
      opens one menu with a labelled section per control ("Agent", "Model", each setting's own name),
      the value in use checked (vscode@3f3380c `chatPhoneInputPresenter.ts`; jupyter-ai-persona-manager@d5ed04b
      `OverflowControlsMenu`). Its accessibility label names every value, as VS Code's aria label does.
      Instrumented test at 320 dp and in the side panel, and a device flow in landscape. The decision
      was made on 2026-10-27 over a long-press tooltip, which nothing on a touch screen advertises. The
      menu's Model section is last and opens on the model search, unfocused (decided 2026-10-27: OpenRouter
      lists hundreds).
- [ ] T3 — The demo on SM-X216B, an ADR for the chip width and the one-chip form (superseding
      ADR-0116's "each chip shrinks"), PRD deleted, backlog row shipped.

## Out of scope

- What Claude names its default model ("Default (recommended)" does not say which model it is). That
  name is the agent's own, from its ACP config options.
- A bottom sheet for the wide case: while the chips fit, each stays its own chip.
