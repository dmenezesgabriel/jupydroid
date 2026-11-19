import type { AgentSignal } from "../../agent-signal.ts";

type RateLimitInfo = {
  status?: string;
  resetsAt?: number;
  rateLimitType?: string;
};

type UpdateMeta = {
  "_claude/rateLimit"?: RateLimitInfo;
  claudeCode?: { toolResponse?: { backgroundTaskId?: string } };
};

type SessionUpdate = {
  sessionUpdate: string;
  toolCallId?: string;
  title?: string;
  status?: string;
  rawInput?: { command?: string };
  rawOutput?: unknown;
  content?: { type?: string; text?: string };
  currentModeId?: string;
  _meta?: UpdateMeta;
};

// Frames that carry no decision: catalogues, session titles, the model's
// task list and reasoning. `usage_update` is read for its rate-limit meta
// before it is dropped.
const QUIET_UPDATES = new Set([
  "available_commands_update",
  "config_option_update",
  "session_info_update",
  "agent_thought_chunk",
  "user_message_chunk",
  "plan",
]);
const QUIET_METHODS = new Set(["_auth/status_update"]);

/**
 * Turns one notification into zero or more signals. Anything under `_claude/`
 * or any session update that is not known to be quiet becomes a `vendor`
 * signal, so a payload nobody has read yet is surfaced, not dropped.
 *
 * @example
 * signalsFrom("session/update", { update: { sessionUpdate: "current_mode_update", currentModeId: "plan" } });
 * // => [{ type: "mode_changed", mode: "plan" }]
 */
export function signalsFrom(method: string, params: unknown): AgentSignal[] {
  if (method === "session/update") return fromSessionUpdate(params);
  if (QUIET_METHODS.has(method)) return [];
  if (method.startsWith("_claude/"))
    return [{ type: "vendor", method, params }];
  return [];
}

function fromSessionUpdate(params: unknown): AgentSignal[] {
  const update = (params as { update?: SessionUpdate }).update;
  if (!update || QUIET_UPDATES.has(update.sessionUpdate)) return [];
  switch (update.sessionUpdate) {
    case "usage_update":
      return fromRateLimit(update._meta?.["_claude/rateLimit"]);
    case "tool_call":
      return fromToolCall(update);
    case "tool_call_update":
      return fromToolCallUpdate(update);
    case "agent_message_chunk":
      return update.content?.text
        ? [{ type: "agent_message", text: update.content.text }]
        : [];
    case "current_mode_update":
      return update.currentModeId
        ? [{ type: "mode_changed", mode: update.currentModeId }]
        : [];
    default:
      return [
        {
          type: "vendor",
          method: `session/update:${update.sessionUpdate}`,
          params,
        },
      ];
  }
}

function fromRateLimit(info: RateLimitInfo | undefined): AgentSignal[] {
  if (!info?.status) return [];
  return [
    {
      type: "rate_limit",
      status: info.status,
      resetsAtMs: info.resetsAt === undefined ? null : info.resetsAt * 1000,
      limitType: info.rateLimitType ?? null,
    },
  ];
}

function fromToolCall(update: SessionUpdate): AgentSignal[] {
  return [
    {
      type: "tool_started",
      toolCallId: update.toolCallId ?? "",
      title: update.title ?? "",
    },
  ];
}

function fromToolCallUpdate(update: SessionUpdate): AgentSignal[] {
  const toolCallId = update.toolCallId ?? "";
  const signals: AgentSignal[] = [];
  const command = update.rawInput?.command;
  if (command) signals.push({ type: "tool_command", toolCallId, command });
  const taskId = update._meta?.claudeCode?.toolResponse?.backgroundTaskId;
  if (taskId) signals.push({ type: "background_task_id", toolCallId, taskId });
  if (typeof update.rawOutput === "string") {
    signals.push({ type: "tool_output", toolCallId, text: update.rawOutput });
  }
  if (update.status === "completed" || update.status === "failed") {
    signals.push({ type: "tool_finished", toolCallId });
  }
  return signals;
}
