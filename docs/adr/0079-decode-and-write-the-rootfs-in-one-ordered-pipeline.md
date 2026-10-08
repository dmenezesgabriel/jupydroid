# ADR-0079 — Decode and write the rootfs in one ordered pipeline

Status: accepted
Date: 2026-10-08

## Context

Base's cold path is dominated by its extraction, `space.installing → server.starting`.
[ADR-0012](0012-ship-the-rootfs-as-zstd.md) fixed the codec because, at the time, decoding was
the floor: on the 56 MB archive, decode alone took 7.0 s and the serial extract 9.0 s.

`ExtractProbeDeviceTest` re-split the stage on the same tablet (`SM-X216B`) against today's
image: 76.4 MB compressed, 20,730 entries, 18,235 files, 371 MB of file bytes. Medians of three
runs:

| part | seconds |
|---|---:|
| decode + walk, nothing written | 4.4 |
| serial extract (decode, then write, entry by entry) | 14.7 |
| every file created empty, no bytes | 1.4 |
| serial, 1 MiB buffers on both sides of the decoder | 14.1 |
| decoder feeding **one** writer through a channel, archive order | 11.9 |
| decoder feeding four writers (reorders) | 9.8 |

Decoding is no longer the floor. **Writing the bytes is**, at about 40 MB/s on that device's
f2fs `/data`, 83% full when measured. Creating the files is cheap, and larger buffers change
nothing. In the serial loop the decoder idles while a file is written, and the writer idles
while the next entry is decoded.

## Decision drivers

- The stage is the first thing a user waits for, and most of it is a cost the app can overlap
  but not remove.
- Archive order must survive. A hard link is materialised by copying a file the archive listed
  earlier, which is why ADR-0012 declined write fan-out.
- In-flight memory must not depend on the image. The heap limit is 256 MB, and the image ships
  files up to 28.7 MB.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Larger buffers | One-line change | Measured flat |
| One ordered writer, whole entries through the channel | Keeps order | Memory scales with the largest files in flight |
| One ordered writer, **an entry per step, larger files in 1 MiB chunks** | Keeps order; in flight is bounded by the channel, not by the image | The step type has two shapes |
| One ordered writer, header, content and end as separate steps | One shape | ~60k thread hand-offs: measured **slower than serial** (15.5–16.3 s) |
| Four writers, hard links deferred until they join | ~2 s more off | Reverses ADR-0012's decline on correctness; a larger change |

## Decision

`TarZstdExtractor` decodes on one coroutine and writes on another (`TarEntryWriter`), in archive
order, through a channel of 8 steps. An entry that fits in 1 MiB crosses as one step. A larger
file crosses as its header, 1 MiB chunks, then its end. So no more than a few MiB are ever in
flight, whatever the image holds.

Measured through the probe on the tablet: 11.5–14.1 s over six runs (median about 12.4 s),
against 14.7 s serial.

## Consequences

- **This forbids** sending header, content and end as separate steps for every entry. Each step
  is a hand-off between two threads, and that shape measured slower than not overlapping at all.
- The single writer keeps ADR-0012's ordering guarantee. Write fan-out stays declined; it is now
  the remaining recorded option, worth about 2 s more on the same probe.
- What is left of the stage is the device's write speed for 371 MB. The next lever is fewer
  bytes in the image, not a different extractor.
