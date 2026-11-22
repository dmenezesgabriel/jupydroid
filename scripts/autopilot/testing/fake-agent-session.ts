import type { StopReason } from "../decide.ts";
import type { PromptBlock } from "../acp-agent.ts";
import type {
  AgentSession,
  GitProbe,
} from "../iteration.ts";

type ScriptedTurn = () => Promise<StopReason> | StopReason;

/** An {@link AgentSession} that plays one scripted turn per prompt, in order. */
export class FakeAgentSession implements AgentSession {
  readonly prompts: PromptBlock[][] = [];
  readonly modes: string[] = [];
  cancelCount = 0;
  /** Makes switching to `mode` fail, as an agent that refuses the mode would. */
  failOn: { mode: string; error: Error } | null = null;

  constructor(private readonly turns: ScriptedTurn[]) {}

  async setMode(mode: string): Promise<void> {
    if (this.failOn?.mode === mode) throw this.failOn.error;
    this.modes.push(mode);
  }

  async prompt(blocks: PromptBlock[]): Promise<StopReason> {
    this.prompts.push(blocks);
    const turn = this.turns.shift();
    if (!turn)
      throw new Error(
        `FakeAgentSession: no scripted turn left for ${JSON.stringify(blocks)}`,
      );
    return turn();
  }

  cancel(): void {
    this.cancelCount += 1;
  }

  promptTexts(): string[] {
    return this.prompts.map((blocks) =>
      blocks
        .map((block) =>
          block.type === "text" ? block.text : block.resource.uri,
        )
        .join("\n"),
    );
  }
}

export class FakeGit implements GitProbe {
  constructor(
    public headSha = "aaa111",
    public clean = true,
  ) {}

  async head(): Promise<string> {
    return this.headSha;
  }

  async isClean(): Promise<boolean> {
    return this.clean;
  }
}
