import type { LineChannel } from "../json-rpc.ts";

/** An in-memory {@link LineChannel}: `sent` records what we wrote, `deliver` plays the agent. */
export class FakeLineChannel implements LineChannel {
  readonly sent: Array<Record<string, unknown>> = [];
  private lineListener: (line: string) => void = () => {};
  private closeListener: () => void = () => {};

  send(line: string): void {
    this.sent.push(JSON.parse(line) as Record<string, unknown>);
  }

  onLine(listener: (line: string) => void): void {
    this.lineListener = listener;
  }

  onClose(listener: () => void): void {
    this.closeListener = listener;
  }

  deliver(frame: Record<string, unknown>): void {
    this.lineListener(JSON.stringify({ jsonrpc: "2.0", ...frame }));
  }

  deliverRaw(line: string): void {
    this.lineListener(line);
  }

  close(): void {
    this.closeListener();
  }

  lastSent(): Record<string, unknown> {
    const last = this.sent[this.sent.length - 1];
    if (!last) throw new Error("FakeLineChannel: nothing was sent");
    return last;
  }
}
