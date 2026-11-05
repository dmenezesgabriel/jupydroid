// Tests for .npmrc: the effective npm configuration of this repository, read the way npm reads it.
// The user's and the machine's npm files are ignored so a contributor's own settings cannot make a
// missing line look present, and the npm_config_* variables npm exports to a script are dropped for the
// same reason.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";

// npm refuses one file loaded as both the user and the global config, so each gets its own empty one.
const EMPTY_CONFIGS = mkdtempSync(join(tmpdir(), "npmrc-test-"));
writeFileSync(join(EMPTY_CONFIGS, "user"), "");
writeFileSync(join(EMPTY_CONFIGS, "global"), "");
after(() => rmSync(EMPTY_CONFIGS, { recursive: true }));

function effectiveNpmSetting(key) {
  const env = Object.fromEntries(Object.entries(process.env).filter(([name]) => !/^npm_config_/i.test(name)));
  const userconfig = `--userconfig=${join(EMPTY_CONFIGS, "user")}`;
  const globalconfig = `--globalconfig=${join(EMPTY_CONFIGS, "global")}`;
  const args = ["config", "get", key, userconfig, globalconfig];
  return spawnSync("npm", args, { env, encoding: "utf8" }).stdout.trim();
}

test("install scripts of dependencies never run", () => assert.equal(effectiveNpmSetting("ignore-scripts"), "true"));

test("a dependency cannot be fetched from a git reference", () => {
  assert.equal(effectiveNpmSetting("allow-git"), "none");
});

test("a release younger than the Dependabot cooldown is not resolved", () => {
  assert.equal(effectiveNpmSetting("min-release-age"), "7");
});

test("an unsupported Node fails the install instead of warning", () => {
  assert.equal(effectiveNpmSetting("engine-strict"), "true");
});

test("a dependency added by hand is pinned to the exact version", () => {
  assert.equal(effectiveNpmSetting("save-exact"), "true");
});
