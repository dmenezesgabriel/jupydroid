# ADR-0012 — Ship the rootfs as zstd, because the decoder was the floor

Status: accepted
Date: 2026-09-27

## Context

Rootfs extraction was measured as the cold-start dominator. The `ExtractProbeDeviceTest` split showed
where the time went: **`GZIPInputStream` decompression alone cost ~8.9 s**. Serial extract was 12.0 s
in total, and even parallel writers could only reach ~7.5 s, because nothing goes under the codec's
floor.

So the question was never how many writer threads to use. It was which codec.

## Decision drivers

- Cold start is the first thing a user experiences, and it was dominated by one stage.
- The APK payload is paid by every download.
- A correctness hazard is not worth a few seconds.

## Options considered

| Option | Pro | Con |
|---|---|---|
| More writer threads on gzip | No format change | Measured 6.3 s — still above the decoder's own 8.9 s floor for the serial part, and it **reorders a stream that carries links pointing at files listed later**. A correctness hazard bought with ~2.7 s |
| zstd | Decoder is far faster and the ratio is better; it is the pairing Winlator's own `rootfs.tzst` installer ships, on a library version line the build host already carried | A new dependency (`zstd-jni`) |
| zstd with `--long` | A slightly better ratio | A 128 MB decode window is not a bargain on a tablet for a ratio win that small |

## Decision

The archive format changed rather than the writer count. `zstd -19 -T0` produces
`rootfs-<abi>.tzst`, and `ZstdCompressorInputStream` (commons-compress + `zstd-jni`) reads it. No
`--long`.

Measured on the device it was slow on: the stage went **16.5 s → ~12.3 s** and the APK payload
**82 MB → 56 MB**.

Write fan-out is **not** taken. It stays a recorded option if the stage still dominates after the
other repairs, but it reorders a stream carrying links that point at files listed later, and that
correctness hazard is not worth ~2.7 s.

## Consequences

**This forbids** going back to gzip for this asset, and compressing `.tzst` in aapt. Both are already
excluded — the first by this decision, the second by `noCompress += "tzst"`, without which the APK
would compress an already-compressed archive and the payload win would be lost.

`TarZstdExtractor` gives files **extraction-time mtimes**, which is not merely cosmetic: it is why
timestamp-invalidated bytecode does not work here, and why
[ADR-0013](0013-ship-bytecode-invalidated-by-checked-hash.md) uses `checked-hash`.

The thing a future reader will be tempted to "fix" is the single-threaded write, which looks like
obvious headroom. It is, and it is declined on correctness, not on effort.
