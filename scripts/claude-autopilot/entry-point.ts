import { fileURLToPath } from "node:url";

/**
 * True when the module at `moduleUrl` is the script node was started with,
 * so an entry file can export its functions for tests and still run as a CLI.
 * `import.meta.main` would say the same, but the installed Node typings lack it.
 *
 * @example
 * if (isEntryPoint(import.meta.url)) void main();
 */
export function isEntryPoint(moduleUrl: string): boolean {
  return process.argv[1] === fileURLToPath(moduleUrl);
}
