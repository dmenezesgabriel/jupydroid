# ADR-0051 — An export is named by the server, and the menu offers what was measured

Status: accepted
Date: 2026-08-20

## Context

`GET /nbconvert/<format>/<path>?download=true` answers with three things and **none may be derived from the
format**:

- the `Content-Disposition` is RFC 5987 — `filename*=utf-8''name.html` — and never a plain `filename=`, on
  every one of the six formats that converted;
- exporting a notebook **with an image output** to `markdown` returns a **zip** named `<stem>.zip`, because
  that exporter writes each image beside the document and `respond_zip` packs both. The same notebook to
  `html` is not a zip, since that exporter inlines images as data URIs;
- so the shape depends on the format **and** on the notebook, and only the headers know.

And `GET /api/nbconvert` names every exporter that *constructs* — `get_export_names` keeps one if
`e.enabled` — and **constructing is not converting.** On the shipped rootfs **fourteen are listed and six
convert**: `asciidoc`, `latex`, `pdf` and `rst` route every markdown cell through `convert_pandoc` and there
is no pandoc; `webpdf` wants playwright and `qtpdf`/`qtpng` want PyQtWebEngine.

## Decision drivers

- A row that can only fail is a control that lies.
- The Space is started on demand, so nothing may boot a runtime to populate a menu.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Derive the file name and type from the format | No header parsing | Wrong on `markdown` with an image, which is a zip; and wrong on every name, since the header is RFC 5987 |
| Offer all fourteen, as the reference does, and let conversion fail | Parity, and no list to maintain | Eight of the fourteen can only fail on this device |
| Offer the intersection of what the Space lists with what has a label and a measurement | Every row works | A Space that gains pandoc gains nothing until the list is updated |
| Fetch the format list when the notebook opens | Menu is instant | Boots the runtime — seconds — to populate a menu nobody has opened |

## Decision

**`ExportedDocument` carries the file name, the media type and the bytes**, all read off the response.
`WorkspaceRepository` grew `writeBytes` beside `writeText`, because a zip written through a `String` is a
corrupt zip.

**The menu draws the intersection** of what the Space lists with what this app has a label and a measurement
for — four rows. A Space that loses an exporter loses its row.

**The format list is fetched when the picker opens, not before.** The reference caches on the first notebook
because its server is always up; here that would boot a runtime to populate a menu.

That leaves a loading state the reference has no name for, and it is a **row of text** rather than a spinner:
Android's accessibility tree carries a `DropdownMenuItem`'s label, so a `CircularProgressIndicator` in a menu
is a control no flow can read — the same reading that made every toggle in this app two labels rather than
one and a check mark.

**"Executable Script" is served by `python`, not by `script`.** `ScriptExporter` reads
`nb.metadata.language_info` to pick a language exporter and its extension, and this app writes `kernelspec`
alone — so on the device `script` answered `text/plain`, named `.txt`, holding the cell sources concatenated
with none of `python`'s `# In[1]` structure. The reference excludes `python` only because its own `script`
already dispatches to it. **Writing `language_info` is the fidelity fix**, it is not built, and it is the
trigger that would let `script` be offered as itself.

**An export lands beside the notebook and replaces what was there.** The reference hands the bytes to the
browser; there is no browser here, and the workspace is the one tree everything shares
([ADR-0022](0022-kernels-start-through-sessions-and-one-tree-is-the-root.md)).

**A file leaves the app through a grant, never through a path** — `FileProvider` plus `ACTION_SEND`, because
another app cannot read this app's `filesDir` and should not be handed a path into it.

## Consequences

The four-row menu is a measured list, so it is the kind of thing that goes stale silently when the rootfs
changes. A Space that gains an exporter needs the measurement re-run, not just the list edited.

Writing `language_info` would improve fidelity in two places at once — `script` export, and anything else
reading a notebook's language. It is recorded as the trigger rather than left as a mystery.

The thing a future reader will be tempted to "fix" is offering all fourteen formats for parity. Eight of them
can only fail here.
