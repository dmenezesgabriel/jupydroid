import type { AgentProfile } from "../agent-profile.ts";
import { CLAUDE_PROFILE } from "./claude/profile.ts";
import { DEVIN_PROFILE } from "./devin/profile.ts";

/** Every agent the loop can drive, by the name `--agent` takes. */
const PROFILES: readonly AgentProfile[] = [CLAUDE_PROFILE, DEVIN_PROFILE];

/** The names `--agent` and `ctl use` take. */
export const AGENT_NAMES: readonly string[] = PROFILES.map(
  (profile) => profile.name,
);

export const DEFAULT_AGENT = CLAUDE_PROFILE.name;

/**
 * The profile of the agent `--agent` names; `undefined` is a flag given
 * without its value, refused rather than read as the default.
 *
 * @example
 * profileFor("claude").defaultCommand; // => "claude-agent-acp"
 */
export function profileFor(name: string | undefined): AgentProfile {
  const found = PROFILES.find((profile) => profile.name === name);
  if (found) return found;
  const known = PROFILES.map((profile) => profile.name).join(", ");
  const got = name === undefined ? "none" : JSON.stringify(name);
  throw new Error(`--agent needs one of ${known}, got ${got}`);
}
