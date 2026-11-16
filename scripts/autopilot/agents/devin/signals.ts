import type { AgentSignal } from "../../agent-signal.ts";

type UpdateMeta = {
  "cognition.ai/background"?: boolean;
  "cognition.ai/backgroundShellId"?: string;
  "cognition.ai/backgroundCommand"?: string;
  terminal_exit?: { terminal_id: string; exit_code: number | null };
};

type SessionUpdate = {
  sessionUpdate: string;
  toolCallId?: string;
  title?: string;
  kind?: string;
  status?: string;
  rawInput?: { plan?: string };
  content?: { type?: string; text?: string };
  currentModeId?: string;
  _meta?: UpdateMeta;
};

// Frames that carry no decision: telemetry, catalogues and model reasoning.
const QUIET_UPDATES = new Set([
  "usage_update",
  "available_commands_update",
  "config_option_update",
  "session_info_update",
  "agent_thought_chunk",
  "user_message_chunk",
]);
const QUIET_VENDOR = [
  "_cognition.ai/mcp/",
  "_cognition.ai/output",
  "_cognition.ai/thinking_complete",
  "_cognition.ai/turn_stats",
];

/**
 * Turns one `devin acp` notification into zero or more signals. Anything
 * under `_cognition.ai/` that is not known to be quiet becomes a `vendor`
 * signal, so a payload nobody has read yet is surfaced, not dropped.
 *
 * @example
 * signalsFrom("_cognition.ai/agent_stopped", { cause: "complete" });
 * // => [{ type: "agent_stopped", cause: "complete" }]
 */
export function signalsFrom(method: string, params: unknown): AgentSignal[] {
  if (method === "session/update") return fromSessionUpdate(params);
  if (method === "_cognition.ai/agent_stopped") {
    const cause = (params as { cause?: string }).cause ?? "unknown";
    return [{ type: "agent_stopped", cause }];
  }
  if (QUIET_VENDOR.some((prefix) => method.startsWith(prefix))) return [];
  if (method.includes("cognition.ai/"))
    return [{ type: "vendor", method, params }];
  return [];
}

function fromSessionUpdate(params: unknown): AgentSignal[] {
  const update = (params as { update?: SessionUpdate }).update;
  if (!update || QUIET_UPDATES.has(update.sessionUpdate)) return [];
  switch (update.sessionUpdate) {
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

function fromToolCall(update: SessionUpdate): AgentSignal[] {
  const toolCallId = update.toolCallId ?? "";
  const signals: AgentSignal[] = [
    { type: "tool_started", toolCallId, title: update.title ?? "" },
  ];
  if (update.kind === "switch_mode" && update.rawInput?.plan !== undefined) {
    signals.push({ type: "plan_proposed", plan: update.rawInput.plan });
  }
  return signals;
}

function fromToolCallUpdate(update: SessionUpdate): AgentSignal[] {
  const signals: AgentSignal[] = [];
  const meta = update._meta ?? {};
  const shellId = meta["cognition.ai/backgroundShellId"];
  if (meta["cognition.ai/background"] && shellId) {
    signals.push({
      type: "background_started",
      shellId,
      command: meta["cognition.ai/backgroundCommand"] ?? "",
      outputPath: null,
    });
  }
  if (meta.terminal_exit) {
    signals.push({
      type: "background_exited",
      shellId: meta.terminal_exit.terminal_id,
      exitCode: meta.terminal_exit.exit_code,
    });
  }
  if (update.status === "completed" || update.status === "failed") {
    signals.push({
      type: "tool_finished",
      toolCallId: update.toolCallId ?? "",
    });
  }
  return signals;
}
