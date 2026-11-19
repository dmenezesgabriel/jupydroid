/**
 * The value of an optional flag. A flag given without its value is refused: reading it as absent
 * would silently run the agent's own default model, effort or plan handling.
 *
 * @example
 * requiredValue(["--model", "haiku"], "--model", "a model id the agent offers"); // => "haiku"
 * requiredValue([], "--model", "a model id the agent offers"); // => null
 */
export function requiredValue(
  argv: string[],
  name: string,
  needs: string,
): string | null {
  const at = argv.indexOf(name);
  if (at === -1) return null;
  const value = argv[at + 1];
  if (value !== undefined && !value.startsWith("--")) return value;
  const got = value === undefined ? "none" : JSON.stringify(value);
  throw new Error(`${name} needs ${needs}, got ${got}`);
}
