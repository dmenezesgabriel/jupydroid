// Tests for the commitizen wiring in package.json. They run the real adapter the way commitizen does
// (`require` of config.commitizen.path) with a fake `inquirer`, because what the prompt promises is the
// questions it asks and the message it builds, not that a path string is present.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { test } from "node:test";

import commitlintConfig from "./commitlint.config.mjs";

const COMMITLINT = "node_modules/.bin/commitlint";
const { config, scripts } = JSON.parse(readFileSync("package.json", "utf8"));
const FEATURE_ANSWERS = {
  type: "feat",
  scope: "x",
  subject: "add y",
  body: "",
  isBreaking: false,
  isIssueAffected: false,
};

// commitizen hands the adapter its own inquirer and a callback for the finished message.
function runAdapter(answers) {
  const { prompter } = createRequire(import.meta.url)(config.commitizen.path);
  return new Promise((resolve) => {
    let questions;
    const fakeInquirer = { prompt: async (asked) => ((questions = asked), answers) };
    prompter(fakeInquirer, (message) => resolve({ questions, message }));
  });
}

test("the adapter is the commitlint one and `npm run commit` starts commitizen", () => {
  assert.equal(config.commitizen.path, "@commitlint/cz-commitlint");
  assert.equal(scripts.commit, "cz");
});

test("the prompt offers exactly the types commitlint.config.mjs allows", async () => {
  const { questions } = await runAdapter(FEATURE_ANSWERS);
  const offered = questions.find((question) => question.name === "type").choices.map((choice) => choice.value);
  const allowed = commitlintConfig.rules["type-enum"][2];
  assert.deepEqual([...offered].sort(), [...allowed].sort());
});

test("the prompt does not offer revert, which AGENTS.md forbids", async () => {
  const { questions } = await runAdapter(FEATURE_ANSWERS);
  const offered = questions.find((question) => question.name === "type").choices.map((choice) => choice.value);
  assert.ok(!offered.includes("revert"), `revert offered in ${JSON.stringify(offered)}`);
});

test("a message built from the prompt's answers passes commitlint", async () => {
  const { message } = await runAdapter(FEATURE_ANSWERS);
  const result = spawnSync(COMMITLINT, [], { input: message, encoding: "utf8" });
  assert.equal(message, "feat(x): add y");
  assert.equal(result.status, 0, `commitlint rejected ${JSON.stringify(message)}:\n${result.stdout}`);
});
