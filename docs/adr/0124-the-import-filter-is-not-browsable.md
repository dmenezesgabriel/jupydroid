# ADR-0124 — The import filter is not BROWSABLE: a web page has no business starting an import

Status: accepted
Date: 2026-10-29

## Context

`MainActivity` has one inbound door besides the launcher: a `VIEW` filter on any `content://` URI
of any MIME type. It copies the `.ipynb` it names into a Space. The filter has carried
`android.intent.category.BROWSABLE` since the commit that added it (`f0259dc4`, "import .ipynb from
VIEW intent"), and nothing anyone wrote says why. The manifest's comment explains the scheme and the
MIME type, not the category.

The category matters. Chrome's [Android Intents](https://developer.chrome.com/docs/android/intents)
page says "only activities with the category filter, android.intent.category.BROWSABLE can be
invoked using this method", meaning an `intent:` URL on a web page, after one user gesture. So a link
on any web page can start this import, with a `content://` URI the page chooses. The app then
resolves that URI **with its own identity**. That includes this app's own `…exports` provider, which
is unexported precisely so that nobody else can reach it. The display name must still end in
`.ipynb`, and the copy lands in the user's own workspace, so nothing goes back to the page. But the
page chose the bytes and started a Space, and `ContentResolverSharedDocumentReader.readBytes` reads
the stream whole.

What the import exists for is a file manager or another app handing over a notebook. Those apps send
the intent with `DEFAULT`, which the filter already carries. `termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4`
`app/src/main/AndroidManifest.xml` has the same kind of door, its `FileViewReceiverActivity` alias, and
declares it without `BROWSABLE`.

## Decision drivers

- An exported component admits only the callers its purpose needs.
- Every difference from the reference at a boundary has its reason written down.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep `BROWSABLE` and document it | No change | No caller needs it; it widens the one inbound door to every web page |
| Drop `BROWSABLE` | The door admits what it exists for: apps sending `VIEW` with `DEFAULT` | A browser that downloads a `.ipynb` and offers "Open" still sends `DEFAULT`, so nothing is lost. The demo has to show that |

## Decision

**The import filter is not `BROWSABLE`.** A notebook reaches the app from another app's `VIEW`
under `DEFAULT`, never from an `intent:` URL on a web page. The removal is a repair, so it is not
made in the audit that found it. It is `GAP-189`, repaired by `JD-072`. Its demo opens a downloaded
`.ipynb` from the browser's downloads and from My Files, and fires a Chrome `intent:` link at the app,
which must now open nothing.

## Consequences

The inbound door narrows to apps installed on the device. Its bound on what it reads is a separate
question (`GAP-190`) because an installed app can still name any URI it serves.

A future reader may add `BROWSABLE` back so that a link "just opens" a notebook. A `content://` URI
from a web page is never the page's to serve, and an `https://` link to a `.ipynb` is a download the
browser makes first.
