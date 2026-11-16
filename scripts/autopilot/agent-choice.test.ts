import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  describeChoice,
  initialChoice,
  parseChoiceFile,
  refreshChoice,
  sameChoice,
  type AgentChoice,
} from "./agent-choice.ts";
import type { LoopEvent } from "./mailbox.ts";

const CLAUDE: AgentChoice = { agent: "claude", model: null, effort: null };
const DEVIN_HIGH: AgentChoice = {
  agent: "devin",
  model: "swe-2-high",
  effort: "high",
};

describe("sameChoice", () => {
  it("is true only when the agent, the model and the effort all agree", () => {
    assert.equal(sameChoice(DEVIN_HIGH, { ...DEVIN_HIGH }), true);
    assert.equal(sameChoice(DEVIN_HIGH, { ...DEVIN_HIGH, effort: null }), false);
    assert.equal(sameChoice(DEVIN_HIGH, { ...DEVIN_HIGH, model: "x" }), false);
    assert.equal(sameChoice(CLAUDE, DEVIN_HIGH), false);
  });
});

describe("describeChoice", () => {
  it("names the agent, then the model and effort, or default where none is named", () => {
    assert.equal(
      describeChoice(DEVIN_HIGH),
      "devin, model swe-2-high, effort high",
    );
    assert.equal(
      describeChoice(CLAUDE),
      "claude, model default, effort default",
    );
  });
});

describe("parseChoiceFile", () => {
  it("reads the three fields back as ctl wrote them", () => {
    const text = JSON.stringify(DEVIN_HIGH);

    assert.deepEqual(parseChoiceFile(text, "/run/choice.json"), DEVIN_HIGH);
  });

  it("reads an absent model or effort as the agent's own default", () => {
    assert.deepEqual(
      parseChoiceFile('{"agent":"claude"}', "/run/choice.json"),
      CLAUDE,
    );
  });

  it("refuses text that is not JSON, naming the file and what it should hold", () => {
    assert.throws(
      () => parseChoiceFile("{half a wri", "/run/choice.json"),
      (error: Error) =>
        error.message.includes("/run/choice.json") &&
        error.message.includes('{"agent": "<name>"'),
    );
  });

  it("refuses a file with no agent, or a model that is not text, quoting the offending value", () => {
    assert.throws(
      () => parseChoiceFile('{"model":"x"}', "/run/choice.json"),
      (error: Error) => error.message.includes('{"model":"x"}'),
    );
    assert.throws(
      () => parseChoiceFile('{"agent":"claude","model":7}', "/run/choice.json"),
      (error: Error) => error.message.includes('"model":7'),
    );
  });
});

describe("initialChoice", () => {
  const fromFlags: AgentChoice = DEVIN_HIGH;

  it("lets the flags win and says they must be written, when any flag chose", () => {
    const initial = initialChoice({ flagged: true, fromFlags, stored: CLAUDE });

    assert.deepEqual(initial, { choice: DEVIN_HIGH, writeFile: true });
  });

  it("reads the file the last run or ctl left, when no flag chose", () => {
    const initial = initialChoice({ flagged: false, fromFlags: CLAUDE, stored: DEVIN_HIGH });

    assert.deepEqual(initial, { choice: DEVIN_HIGH, writeFile: false });
  });

  it("runs the default agent, writing nothing, when no flag chose and no file exists", () => {
    const initial = initialChoice({ flagged: false, fromFlags: CLAUDE, stored: null });

    assert.deepEqual(initial, { choice: CLAUDE, writeFile: false });
  });
});

function refresher(read: () => Promise<AgentChoice | null>) {
  const events: LoopEvent[] = [];
  const deps = {
    read,
    emit: async (event: LoopEvent) => {
      events.push(event);
    },
    knowsAgent: (name: string) => name === "claude" || name === "devin",
  };
  return { deps, events };
}

describe("refreshChoice", () => {
  it("keeps the choice and says nothing when the file is absent or the same", async () => {
    const absent = refresher(async () => null);
    const same = refresher(async () => ({ ...CLAUDE }));

    assert.deepEqual(await refreshChoice(absent.deps, CLAUDE), CLAUDE);
    assert.deepEqual(await refreshChoice(same.deps, CLAUDE), CLAUDE);
    assert.deepEqual(absent.events, []);
    assert.deepEqual(same.events, []);
  });

  it("takes a changed choice and journals the one it leaves and the one it takes", async () => {
    const { deps, events } = refresher(async () => DEVIN_HIGH);

    const taken = await refreshChoice(deps, CLAUDE);

    assert.deepEqual(taken, DEVIN_HIGH);
    assert.deepEqual(events, [
      { type: "agent_choice_changed", from: CLAUDE, to: DEVIN_HIGH },
    ]);
  });

  it("keeps the current choice and journals why when the file names an agent the loop does not know", async () => {
    const { deps, events } = refresher(async () => ({
      agent: "gemini",
      model: null,
      effort: null,
    }));

    const kept = await refreshChoice(deps, DEVIN_HIGH);

    assert.deepEqual(kept, DEVIN_HIGH);
    assert.deepEqual(events, [
      {
        type: "agent_choice_invalid",
        error: 'unknown agent "gemini" in the choice file',
      },
    ]);
  });

  it("keeps the current choice and journals the reason when the file cannot be read", async () => {
    const { deps, events } = refresher(async () => {
      throw new Error("choice file /run/choice.json is not valid JSON");
    });

    const kept = await refreshChoice(deps, CLAUDE);

    assert.deepEqual(kept, CLAUDE);
    assert.deepEqual(events, [
      {
        type: "agent_choice_invalid",
        error: "choice file /run/choice.json is not valid JSON",
      },
    ]);
  });
});
