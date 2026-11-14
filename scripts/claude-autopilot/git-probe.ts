import { execFile } from "node:child_process";
import { promisify } from "node:util";
import type { GitProbe } from "./iteration.ts";

const run = promisify(execFile);

/** Reads the two git facts an iteration is judged by: the commit at HEAD and whether the tree is clean. */
export class GitCli implements GitProbe {
  constructor(private readonly repoRoot: string) {}

  async head(): Promise<string> {
    const { stdout } = await run("git", ["rev-parse", "HEAD"], {
      cwd: this.repoRoot,
    });
    return stdout.trim();
  }

  async isClean(): Promise<boolean> {
    const { stdout } = await run("git", ["status", "--porcelain"], {
      cwd: this.repoRoot,
    });
    return stdout.trim() === "";
  }
}
