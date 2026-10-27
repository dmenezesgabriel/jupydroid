# ADR-0120 — The Chat's chips keep their whole width, and where they do not fit they fold into one chip naming the model

Status: accepted
Date: 2026-10-27

## Context

ADR-0116 put a chip per choice in the Chat input's toolbar row, beside Send: the agent (ADR-0119), its
model, and each general setting the persona reports, such as the mode and opencode's effort. Each chip
had `weight(1f, fill = false)` in a `Row`, which capped it at an equal share of the row whether or not
the other chips used theirs. On SM-X216B (240 dpi, portrait, one pane) the model in use could not be
read without opening its list:

- Claude's model read "Default (recomm…".
- opencode's read "OpenRouter/Nort…" and "Default / OpenCode/Big …".
- In the landscape side panel, "Default / OpenCode/Big Pickle" had 47 dp.

Two of the seven references have a chat input with pickers:

- **jupyter-ai-persona-manager@d5ed04b** (the frontend the image ships).
  - `src/persona-controls.tsx` `ControlsRow` measures a hidden full-width copy and keeps as many
    controls as fit.
  - It moves the rest into `OverflowControlsMenu`, a "…" popover with a labelled section per control.
  - `style/base.css` caps a value at `15ch`, and the button's `title` is the control's label.
- **vscode@3f3380c.**
  - `src/vs/workbench/contrib/chat/browser/widget/input/chatInputPickerResponsiveLayout.ts` keeps
    every picker expanded while the lane fits, then compacts them one at a time, rightmost first.
  - `modelPicker/modelPickerActionItem.ts` hovers "Models • <name>".
  - On a phone, `chatPhoneInputPresenter.ts` `_updateTrigger` draws one chip whose text is the model's
    name and whose aria label names the mode and the model. A tap opens one sheet for both:
    `src/vs/sessions/contrib/providers/agentHost/browser/mobile/mobileChatPhoneInputPresenter.ts`
    `showCombinedModeAndModelSheet`.

jupyterlab, vscode-jupyter, intellij-community, termux-app and winlator have no chat input with pickers.

## Decision drivers

- Visibility of system status (Nielsen #1): the model the next question goes with is read at a glance.
- Recognition rather than recall (#6): no value hidden behind a gesture nothing on screen advertises.
- A pick costs no more taps than it did (two).
- The row must not grow (ADR-0116).
- Nothing on screen misleads: a cut name, or a glyph that names nothing, is not a value.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Width while there is room | natural width (both references); an equal share each (ours) | natural width |
| Width under pressure | compact to an icon (VS Code); ellipsize the longest first; overflow the rest into "…" (jupyter-ai) | none in the row: chips stay whole until their whole widths do not fit. ACP models and modes carry no icon, and cutting the longest name first cut the model to keep a short chip whole |
| When they do not fit | a "…" menu with a section per control (jupyter-ai); one chip naming the model, opening one sheet (VS Code's phone); a hover or long-press tooltip | VS Code's phone shape with jupyter-ai's sections. A hover has no touch form, and a long press is a gesture nothing advertises |

## Decision

**While their whole names fit beside Send, the Chat's chips are drawn side by side at the width their
names need. Where they do not fit, they fold into one chip whose text is the model in use. A tap on it
opens one menu with a labelled section per control, the value in use checked, and a pick there closes
it.**

- `ChipRow` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/AgentControls.kt`) does the
  layout:
  - It measures the chips' whole row without placing it, keeping that copy out of the semantics tree.
  - It draws either the chips or the one chip, never a cut chip.
- `ChatControl` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatControl.kt`) holds one
  control's label, choice and pick, and has no Compose, so it is tested on the JVM:
  - `chatControls` gives jupyter-ai's order (the agent, the model, each setting).
  - `controlsName` is the one chip's text: the model, or the agent while there is no model.
  - `controlsLabel` is its accessibility label, "Agent: OpenCode, Model: …, Session Mode: build", as
    VS Code's aria label names every value.
- `ControlsChip` (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ControlsChip.kt`) draws
  the one chip and its menu:
  - The menu has a section headed by each control's label: "Agent", each setting's own name, and
    "Model".
  - The Model section is last, as VS Code's sheet puts "Model" after the mode: OpenRouter lists hundreds
    of models, which would push a mode's two values out of reach.
  - The Model section opens on its search field unfocused, so the keyboard does not cover the menu.

Tests:

- `ChatControlTest` (`theOneChipNamesTheModelInUse`, `theLabelNamesEveryControlAndTheValueItUses`).
- `ChatAgentControlsTest.aChipsNameIsWholeWhileTheRowHasRoom`, at 800 dp.
- `ChatControlsChipTest` (`atAPhonesWidthTheControlsFoldIntoOneChipBesideSend`,
  `aPickInItsMenuIsOneTapAndTheNextQuestionIsAskedWithIt`), at 320 dp.

Measured on SM-X216B (240 dpi). Text widths are in dp:

| Where | Text | Equal share | Now |
|---|---|---|---|
| side panel, landscape | Default / OpenCode/Big Pickle | 47, cut | 213, whole, as the one chip (261) |
| one pane, portrait, OpenCode | Default / OpenCode/Big Pickle | 190, cut | 214, whole |
| one pane, portrait, Claude | Default (recommended) | 130, cut | 166, whole |

- **The row did not grow.** Chips and Send sit at y 1112–1136 dp in portrait and 601–623 dp in the side
  panel, with the equal share and now.
- **It holds after a kill.** In the chat reopened after a kill and relaunch, OpenCode's model again had
  214 dp in portrait, with the row at 1112–1136 dp. In the side panel the one chip again had 261 dp.

## Consequences

- **Every value in portrait at one pane on a tablet is read without a tap.** In the side panel and on a
  phone, the model is read without a tap and the rest with one. That tap is the one that also picks.
- **Where the chips fit, there is no one chip.** Each stays its own chip, a pick still two taps, so no
  value is hidden on a screen with room to show it.
- **There is no icon compaction.** ACP models and modes carry no icon, and a glyph we drew would name
  nothing. Revisit this if the protocol gives them one.
- **The one chip names the model, not the agent.** Which agent answers is drawn above every answer
  (ADR-0119), but nothing else on screen names the model.
- **What a model is called is the agent's own name.** Claude names its default "Default (recommended)",
  which does not say which model it is. The chip shows it whole, and only the agent can name it better.
- **The chips are measured on every layout.** `SubcomposeLayout` composes them a second time to measure
  them. That is a handful of chips beside Send, not a list.

## Amends

[ADR-0116](0116-the-chats-model-mode-and-commands-come-from-the-personas-events-and-ride-the-next-question.md)'s
"Phone layout" row: a chip per choice stays in the toolbar row only while the chips fit whole. Where they
do not fit, they become the one chip described above.
