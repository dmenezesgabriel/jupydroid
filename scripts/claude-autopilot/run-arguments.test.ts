import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseArguments } from "./run.ts";

describe("parseArguments", () => {
  it("defaults to the cwd, its run directory and prompt-execution.md", () => {
    assert.deepEqual(parseArguments([], "/work/jupydroid"), {
      repoRoot: "/work/jupydroid",
      runDir: "/work/jupydroid/.autopilot-run",
      promptFile: "/work/jupydroid/prompt-execution.md",
      agentCommand: "claude-agent-acp",
    });
  });

  it("reads each flag by name, not by position", () => {
    const parsed = parseArguments(
      ["--prompt", "other.md", "--repo", "/srv/other"],
      "/work/jupydroid",
    );

    assert.deepEqual(parsed, {
      repoRoot: "/srv/other",
      runDir: "/srv/other/.autopilot-run",
      promptFile: "/srv/other/other.md",
      agentCommand: "claude-agent-acp",
    });
  });

  it("takes the agent command from --agent-command, for a shell whose PATH has no nvm", () => {
    const parsed = parseArguments(
      ["--agent-command", "/opt/node/bin/claude-agent-acp"],
      "/work/jupydroid",
    );

    assert.equal(parsed.agentCommand, "/opt/node/bin/claude-agent-acp");
  });

  it("does not mistake the first argument for a missing flag's value", () => {
    const parsed = parseArguments(["--prompt", "other.md"], "/work/jupydroid");

    assert.equal(parsed.repoRoot, "/work/jupydroid");
  });
});
