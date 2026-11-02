# ADR-0127 — A shared document is read to a bound, and refused past it

Status: accepted
Date: 2026-10-31

## Context

`ContentResolverSharedDocumentReader` read a shared stream whole with `readBytes()`. A provider can
report no length, or a wrong one, so a file or an endless stream could fill the heap before anything
looked at its size, and `importNotebook` ended in `runCatching{}.getOrNull()`, so every refusal was
silent.

## Decision

- **The bound is 15 MiB**, `SharedDocumentReader.MAX_BYTES`: `jupyterlab@7f0e690
  packages/filebrowser/src/model.ts` `LARGE_FILE_SIZE`, the size past which JupyterLab will not take
  a file without asking. An import has no one to ask. Measured on the arm64 tablet (`SM-X216B`):
  `dalvik.vm.heapgrowthlimit` is 256m (`heapsize` 512m), so a document at the bound held as bytes,
  as a UTF-16 string and as a parsed tree is a fraction of the limit.
- **The read is a 4 KiB loop** (`readBoundedBytes`) that stops one byte past the bound, so no length
  from the provider is trusted and none is needed.
- **The refusal is typed**: `SharedDocumentTooLarge` carries the limit, and `importNotebook` tells it
  from an unreadable stream, which stays silent as before.
- **It is told in a snackbar**, `SnackbarDuration.Long`, drawn above the system bars
  (`navigationBarsPadding`): "<file> is over the 15 MiB limit for an import". Measured on the
  tablet, a `Short` snackbar had expired before a screenshot of it was taken, and one with no inset
  padding was drawn behind the taskbar's icons.

## Consequences

The size named is the limit, not the file's own: the loop stops at the bound and never learns more.
A refused import leaves nothing in the workspace, on the device, after a relaunch
(`scripts/import-too-large-driver.sh`).
