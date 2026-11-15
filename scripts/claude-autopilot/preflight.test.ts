import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  hostBusyReason,
  leftoversIn,
  nextBacklogRow,
  waitUntilReady,
  type HostReadings,
  type PreflightDeps,
  type ProcEntry,
} from "./preflight.ts";
import type { LoopEvent } from "./mailbox.ts";
import { FakeClock } from "./testing/fake-mailbox.ts";
import { assertContains, assertPartial } from "./testing/assertions.ts";

const REPO = "/home/user/jupydroid";
const CALM: HostReadings = { load1: 2, cores: 8, memoryStallAvg60: 0 };
const BACKLOG_WITH_NEXT =
  "| Milestone | Status |\n| `JD-085` | big text | open ← next | [PRD](prd/x.md) | — |\n";

describe("hostBusyReason", () => {
  it("passes a host whose load fits the cores and whose memory is not stalling", () => {
    assert.equal(hostBusyReason(CALM), null);
    assert.equal(hostBusyReason({ ...CALM, load1: 8 }), null);
  });

  it("blocks on load above the core count and on any memory stall", () => {
    assertContains(
      hostBusyReason({ ...CALM, load1: 18.7 }),
      "18.7 exceeds 8 cores",
    );
    assertContains(
      hostBusyReason({ ...CALM, memoryStallAvg60: 1.5 }),
      "memory",
    );
  });
});

describe("nextBacklogRow", () => {
  it("returns the id in the first column of the row marked next", () => {
    assert.equal(nextBacklogRow(BACKLOG_WITH_NEXT), "JD-085");
  });

  it("finds the marker behind any state, because a partly done item is still the one to continue", () => {
    assert.equal(
      nextBacklogRow("| `AUD-014` | audit | partial ← next | — | — |\n"),
      "AUD-014",
    );
  });

  it("does not take the prose that explains the marker for a row", () => {
    const prose = "`← next` **after** its state, whichever that is.\n";

    assert.equal(
      nextBacklogRow(prose + "| `JD-1` | done | shipped | — | — |\n"),
      null,
    );
  });

  it("returns null once no row is marked next", () => {
    assert.equal(nextBacklogRow("| `JD-1` | shipped |\n"), null);
  });
});

describe("leftoversIn", () => {
  const entry = (cwd: string, cmdline: string): ProcEntry => ({
    pid: 10,
    cwd,
    cmdline,
  });

  it("finds an emulator or a Maestro run the repo's session left behind", () => {
    const left = leftoversIn(
      [
        entry(`${REPO}`, "qemu-system-x86_64 -avd dev36"),
        entry(`${REPO}/scripts`, "java -jar maestro-cli.jar test flow.yaml"),
      ],
      REPO,
    );

    assert.equal(left.length, 2);
  });

  it("leaves a Gradle daemon alone, because an IDE the user has open owns one in this tree", () => {
    const left = leftoversIn([entry(REPO, "java GradleDaemon 9.0")], REPO);

    assert.deepEqual(left, []);
  });

  it("ignores other repos, sibling directories with the same prefix, and unrelated commands", () => {
    const left = leftoversIn(
      [
        entry("/home/user/other", "qemu-system-x86_64"),
        entry(`${REPO}-copy`, "qemu-system-x86_64"),
        entry(REPO, "zsh"),
      ],
      REPO,
    );

    assert.deepEqual(left, []);
  });
});

class FakeHost {
  readings: HostReadings = CALM;
  procs: ProcEntry[] = [];
  backlog = BACKLOG_WITH_NEXT;
  stop = false;
  readonly events: LoopEvent[] = [];

  deps(clock: FakeClock): PreflightDeps {
    return {
      repoRoot: REPO,
      readBacklog: async () => this.backlog,
      readHost: async () => this.readings,
      readProcs: async () => this.procs,
      stopRequested: async () => this.stop,
      clock,
      emit: async (event) => {
        this.events.push(event);
      },
      recheckMs: 60_000,
      repeatReportMs: 10 * 60_000,
    };
  }
}

describe("waitUntilReady", () => {
  it("is ready at once on a calm host with a next row", async () => {
    const host = new FakeHost();

    assert.equal(await waitUntilReady(host.deps(new FakeClock())), "ready");
    assert.deepEqual(host.events, []);
  });

  it("waits while the host is busy, says why once, and goes when it calms", async () => {
    const host = new FakeHost();
    host.readings = { ...CALM, load1: 18 };
    const clock = new FakeClock((now) => {
      if (now >= 180_000) host.readings = CALM;
    });

    const result = await waitUntilReady(host.deps(clock));

    assert.equal(result, "ready");
    assert.equal(host.events.length, 1);
    assertPartial(host.events[0], { type: "preflight_wait" });
  });

  it("waits for a leftover dev server to exit instead of killing it", async () => {
    const host = new FakeHost();
    host.procs = [
      { pid: 42, cwd: REPO, cmdline: "qemu-system-x86_64 -avd dev36" },
    ];
    const clock = new FakeClock((now) => {
      if (now >= 120_000) host.procs = [];
    });

    assert.equal(await waitUntilReady(host.deps(clock)), "ready");
    assertContains(String(host.events[0]?.["reason"]), "42 qemu-system-x86_64");
  });

  it("repeats its report every so often while still blocked", async () => {
    const host = new FakeHost();
    host.readings = { ...CALM, load1: 18 };
    const clock = new FakeClock((now) => {
      if (now >= 25 * 60_000) host.readings = CALM;
    });

    await waitUntilReady(host.deps(clock));

    assert.ok(
      host.events.length >= 3,
      `expected 3 or more events, saw ${host.events.length}`,
    );
  });

  it("reports an empty backlog instead of starting a session", async () => {
    const host = new FakeHost();
    host.backlog = "| Items | shipped |\n";

    assert.equal(
      await waitUntilReady(host.deps(new FakeClock())),
      "backlog_exhausted",
    );
  });

  it("stops when asked, even while blocked", async () => {
    const host = new FakeHost();
    host.readings = { ...CALM, load1: 18 };
    const clock = new FakeClock((now) => {
      if (now >= 120_000) host.stop = true;
    });

    assert.equal(await waitUntilReady(host.deps(clock)), "stop");
  });
});
