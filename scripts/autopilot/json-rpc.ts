/** One newline-delimited JSON frame per line, the framing `claude-agent-acp` speaks on stdio. */
export interface LineChannel {
  send(line: string): void;
  onLine(listener: (line: string) => void): void;
  onClose(listener: () => void): void;
}

export class RpcError extends Error {
  readonly code: number;
  readonly data: unknown;

  constructor(code: number, message: string, data?: unknown) {
    super(message);
    this.name = "RpcError";
    this.code = code;
    this.data = data;
  }
}

/** The connection was lost before the request was answered. */
export class ChannelClosedError extends Error {
  constructor(method: string) {
    super(`channel closed before "${method}" was answered`);
    this.name = "ChannelClosedError";
  }
}

export interface RpcHandlers {
  /** Answers a request the agent sends us; a thrown error becomes a JSON-RPC error reply. */
  onRequest(method: string, params: unknown): Promise<unknown>;
  onNotification(method: string, params: unknown): void;
  /** Called for every inbound frame, before dispatch: proof the agent is alive. */
  onFrame(): void;
}

type Frame = {
  id?: string | number;
  method?: string;
  params?: unknown;
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
};

type Pending = {
  method: string;
  resolve: (value: unknown) => void;
  reject: (reason: Error) => void;
};

const METHOD_NOT_FOUND = -32601;

/**
 * JSON-RPC 2.0 over a {@link LineChannel}, both directions: requests we send
 * are matched to replies by id, requests the agent sends go to the handlers.
 *
 * @example
 * const rpc = new JsonRpcConnection(channel, handlers);
 * const { sessionId } = (await rpc.request("session/new", { cwd, mcpServers: [] })) as { sessionId: string };
 */
export class JsonRpcConnection {
  private nextId = 1;
  private readonly pending = new Map<number, Pending>();

  constructor(
    private readonly channel: LineChannel,
    private readonly handlers: RpcHandlers,
  ) {
    channel.onLine((line) => this.receive(line));
    channel.onClose(() => this.failPending());
  }

  request(method: string, params: unknown): Promise<unknown> {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { method, resolve, reject });
      this.channel.send(JSON.stringify({ jsonrpc: "2.0", id, method, params }));
    });
  }

  notify(method: string, params: unknown): void {
    this.channel.send(JSON.stringify({ jsonrpc: "2.0", method, params }));
  }

  private receive(line: string): void {
    const frame = parseFrame(line);
    if (!frame) return;
    this.handlers.onFrame();
    if (frame.method !== undefined && frame.id !== undefined) {
      void this.answerRequest(frame.id, frame.method, frame.params);
    } else if (frame.method !== undefined) {
      this.handlers.onNotification(frame.method, frame.params);
    } else if (typeof frame.id === "number") {
      this.settle(frame.id, frame);
    }
  }

  private async answerRequest(
    id: string | number,
    method: string,
    params: unknown,
  ): Promise<void> {
    try {
      const result = await this.handlers.onRequest(method, params);
      this.channel.send(JSON.stringify({ jsonrpc: "2.0", id, result }));
    } catch (error) {
      const code = error instanceof RpcError ? error.code : METHOD_NOT_FOUND;
      const message = error instanceof Error ? error.message : String(error);
      this.channel.send(
        JSON.stringify({ jsonrpc: "2.0", id, error: { code, message } }),
      );
    }
  }

  private settle(id: number, frame: Frame): void {
    const entry = this.pending.get(id);
    if (!entry) return;
    this.pending.delete(id);
    if (frame.error) {
      const { code, message, data } = frame.error;
      entry.reject(new RpcError(code, message, data));
    } else {
      entry.resolve(frame.result);
    }
  }

  private failPending(): void {
    for (const entry of this.pending.values()) {
      entry.reject(new ChannelClosedError(entry.method));
    }
    this.pending.clear();
  }
}

function parseFrame(line: string): Frame | null {
  try {
    const parsed: unknown = JSON.parse(line);
    return typeof parsed === "object" && parsed !== null
      ? (parsed as Frame)
      : null;
  } catch {
    return null;
  }
}
