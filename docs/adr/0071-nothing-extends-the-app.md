# ADR-0071 — Nothing extends the app; the feature set is fixed

Status: superseded by [ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)
Date: 2026-10-03

## Context

JupyterLab is a plugin host. Every window of its chrome — the shell areas, the menus, the
status bar, the settings editor, the log console — is a `JupyterFrontEndPlugin` that activates
against tokens (`ILauncher`, `IStatusBar`, `IThemeManager`, `ISettingRegistry`) and contributes
commands to one registry, and two of its own packages exist to *manage* that fact:
`pluginmanager` lists the loaded plugins for enable/disable
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`
`packages/pluginmanager-extension/src/index.ts:119-170`) and `extensionmanager` surfaces
third-party extensions with its own enable toggle
(`packages/extensionmanager-extension/src/index.ts:158-200`). A plugin is a JavaScript bundle
running in the page, contributing Lumino widgets, mime renderers and commands.

This app has no such surface to contribute to. The UI is one Compose tree
([ADR-0039](0039-a-compose-native-cell-editor-not-a-webview.md)) — no DOM, no JavaScript module
loader, no widget registry — and the act set is closed (`GAP-120` records it: *every act is a
named call wired to its own reach point*). Nothing an extension could *register into* exists.

The AUD-009 audit reached `pluginmanager:open`/`refresh` and
`extensionmanager:show-panel`/`toggle` and found the reason for their absence written nowhere
durable: the PRD called it a corollary of ADR-0039, and a corollary nobody writes down is the
shape `undocumented` exists to catch
([ADR-0067](0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)).

## Decision drivers

- A manager with nothing to manage is not a missing capability; it is machinery answering a
  question the construction refuses to ask.
- Recording the *why* durably means the next audit does not re-derive it — every later surface
  whose premise is an extension point (plugin settings, contributed themes, mime renderers)
  inherits this record.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Verdict the four ids `absent` with a `GAP-NNN` | Fits the letter of the table | A gap implies a repair; there is nothing to repair — the operand is missing by construction, not by oversight |
| Verdict `adapted` citing ADR-0039 | No new document | ADR-0039 decided Compose over WebView for the *cell editor*; "therefore no extension mechanism" is a step it never takes |

## Decision

**The app is not extensible by construction, and that is a decision, not an omission.** A
plugin here would be JavaScript contributing to a registry that does not exist, inside a UI
toolkit it cannot draw into. The Compose-native decision and the closed act set together are
the whole of it: there is no plugin surface because there is nothing for a plugin to enter.

## Consequences

- `pluginmanager:*` and `extensionmanager:*` have no operand and never will; the parity ledger
  reads them `undocumented` resolved by this record.
- Any future surface that presupposes third-party contribution — a plugin-written theme, a
  mime renderer beyond the built-in set, a status-bar item a package would add — is absent for
  the same recorded reason, not re-litigated per row.
- The cost is named: a capability we did not build cannot arrive by writing a plugin for it.
  Every feature is ours to ship or ours to lack.
