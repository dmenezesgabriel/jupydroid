import type { AgentProfile } from "../../agent-profile.ts";
import { ClaudeDialect } from "./dialect.ts";
import { classifyClaudeError } from "./limit-text.ts";

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

/**
 * `claude-agent-acp`, the adapter that drives the user's Claude Code, as
 * measured on 0.91.0. Claude Code bounds a foreground command with its Bash
 * timeout and moves longer work to a background task, so an hour of total
 * silence is not a test suite, and three hours is not a working agent.
 */
export const CLAUDE_PROFILE: AgentProfile = {
  name: "claude",
  defaultCommand: "claude-agent-acp",
  launchArgs: [],
  bypassMode: "bypassPermissions",
  exitPlan: { bypassOptionId: "exit-plan-bypass", declineOptionId: "reject" },
  timing: {
    possiblyHungAfterMs: 45 * MINUTE,
    cancelAfterSilenceMs: 3 * HOUR,
    backgroundCeilingMs: 3 * HOUR,
    limitPollMs: 30 * MINUTE,
    limitMarginMs: MINUTE,
    watchEveryMs: MINUTE,
  },
  createDialect: () => new ClaudeDialect(),
  classifyError: (error) => classifyClaudeError(error),
};
