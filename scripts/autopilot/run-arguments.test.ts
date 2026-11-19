import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseArguments } from "./run.ts";

describe("parseArguments", () => {
  it("defaults to the cwd, its run directory and prompt-execution.md", () => {
    const parsed = parseArguments([], "/work/repo");

    assert.deepEqual({ ...parsed, agent: parsed.agent.name }, {
      repoRoot: "/work/repo",
      runDir: "/work/repo/.autopilot-run",
      promptFile: "/work/repo/prompt-execution.md",
      agent: "claude",
      agentCommand: "claude-agent-acp",
      model: null,
      effort: null,
      choiceFlagged: false,
      planFollowUp: null,
    });
  });

  it("reads each flag by name, not by position", () => {
    const parsed = parseArguments(
      ["--prompt", "other.md", "--repo", "/srv/other"],
      "/work/repo",
    );

    assert.deepEqual({ ...parsed, agent: parsed.agent.name }, {
      repoRoot: "/srv/other",
      runDir: "/srv/other/.autopilot-run",
      promptFile: "/srv/other/other.md",
      agent: "claude",
      agentCommand: "claude-agent-acp",
      model: null,
      effort: null,
      choiceFlagged: false,
      planFollowUp: null,
    });
  });

  it("reads the plan follow-up command from its flag", () => {
    const parsed = parseArguments(
      ["--plan-follow-up", "/engineering-mode"],
      "/work/repo",
    );

    assert.equal(parsed.planFollowUp, "/engineering-mode");
  });

  it("refuses a plan follow-up flag with no command, rather than silently running without it", () => {
    assert.throws(() => parseArguments(["--plan-follow-up"], "/work/repo"), (error: Error) => error.message.includes('--plan-follow-up needs a command such as "/engineering-mode", got none'));
    assert.throws(() =>
      parseArguments(["--plan-follow-up", "--repo", "/x"], "/work/repo"), (error: Error) => error.message.includes('got "--repo"'));
  });

  it("takes the agent command from --agent-command, for a shell whose PATH has no nvm", () => {
    const parsed = parseArguments(
      ["--agent-command", "/opt/node/bin/claude-agent-acp"],
      "/work/repo",
    );

    assert.equal(parsed.agentCommand, "/opt/node/bin/claude-agent-acp");
  });

  it("selects the agent by name and starts its own executable by default", () => {
    const parsed = parseArguments(["--agent", "claude"], "/work/repo");

    assert.equal(parsed.agent.name, "claude");
    assert.equal(parsed.agentCommand, parsed.agent.defaultCommand);
  });

  it("starts devin with its own executable and its acp subcommand", () => {
    const parsed = parseArguments(["--agent", "devin"], "/work/repo");

    assert.equal(parsed.agent.name, "devin");
    assert.equal(parsed.agentCommand, "devin");
    assert.deepEqual(parsed.agent.launchArgs, ["acp"]);
  });

  it("refuses an unknown agent and a flag with no agent, naming the valid ones", () => {
    assert.throws(() => parseArguments(["--agent", "gpt"], "/work/repo"), (error: Error) => error.message.includes('--agent needs one of claude, devin, got "gpt"'));
    assert.throws(() => parseArguments(["--agent"], "/work/repo"), (error: Error) => error.message.includes("--agent needs one of claude, devin, got none"));
  });

  it("takes the model from --model, and runs the agent's own default without it", () => {
    const chosen = parseArguments(["--model", "haiku"], "/work/repo");
    const unchosen = parseArguments([], "/work/repo");

    assert.equal(chosen.model, "haiku");
    assert.equal(unchosen.model, null);
  });

  it("takes the effort from --effort, and leaves the agent's own effort without it", () => {
    const chosen = parseArguments(["--effort", "high"], "/work/repo");
    const unchosen = parseArguments([], "/work/repo");

    assert.equal(chosen.effort, "high");
    assert.equal(unchosen.effort, null);
  });

  it("says whether any of --agent, --model or --effort chose, because a choice on the command line outranks the choice file", () => {
    assert.equal(parseArguments(["--agent", "devin"], "/work/repo").choiceFlagged, true);
    assert.equal(parseArguments(["--model", "haiku"], "/work/repo").choiceFlagged, true);
    assert.equal(parseArguments(["--effort", "high"], "/work/repo").choiceFlagged, true);
    assert.equal(parseArguments(["--repo", "/x"], "/work/repo").choiceFlagged, false);
  });

  it("refuses an --effort with no value, rather than silently running the agent's own effort", () => {
    assert.throws(
      () => parseArguments(["--effort"], "/work/repo"),
      (error: Error) =>
        error.message.includes("--effort needs an effort the agent offers, got none"),
    );
    assert.throws(
      () => parseArguments(["--effort", "--model", "x"], "/work/repo"),
      (error: Error) => error.message.includes('got "--model"'),
    );
  });

  it("refuses a --model with no id, rather than silently running the default model", () => {
    assert.throws(() => parseArguments(["--model"], "/work/repo"), (error: Error) => error.message.includes("--model needs a model id the agent offers, got none"));
    assert.throws(() =>
      parseArguments(["--model", "--repo", "/x"], "/work/repo"), (error: Error) => error.message.includes('got "--repo"'));
  });

  it("does not mistake the first argument for a missing flag's value", () => {
    const parsed = parseArguments(["--prompt", "other.md"], "/work/repo");

    assert.equal(parsed.repoRoot, "/work/repo");
  });
});
