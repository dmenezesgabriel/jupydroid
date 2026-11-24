// The conventional-commit preset, with the overrides this repository's own history forces. Each is
// measured, not preferred: the history holds subjects up to 313 characters, capitalised subjects such
// as "refactor(tests): Kotlin fixtures ...", and no `revert` because AGENTS.md forbids reverts. About a
// tenth of the bodies close with a "Gates: ..." or "Reproduce: ..." line, which commitlint parses as a
// footer and warns about without a blank line before it. A cap or a case rule here would fail the
// commits the repository already makes, and a warning on every tenth commit trains people to ignore it.
const OFF = [0];

// feat/fix/perf subjects become the published changelog (python-semantic-release renders them, and a
// bumping commit can never be excluded), so they must read as release notes: a short description of
// the change, never an ephemeral backlog id. Ids are still allowed on the types the changelog drops.
const noEphemeralIdsInReleaseSubjects = ({ type, subject }) => {
  const bumpsRelease = ["feat", "fix", "perf"].includes(type ?? "");
  const carriesId = /\b(?:JD|OPS|AUD|GAP)-\d+\b/.test(subject ?? "");
  return [
    !(bumpsRelease && carriesId),
    "feat/fix/perf subjects are release notes — describe the change, never a JD-/OPS-/AUD-/GAP- id",
  ];
};

export default {
  extends: ["@commitlint/config-conventional"],
  plugins: [{ rules: { "subject-no-ephemeral-ids": noEphemeralIdsInReleaseSubjects } }],
  rules: {
    "subject-no-ephemeral-ids": [2, "always"],
    "type-enum": [2, "always", ["feat", "fix", "docs", "test", "refactor", "build", "ci", "chore", "perf", "style"]],
    "header-max-length": OFF,
    "body-max-line-length": OFF,
    "footer-max-line-length": OFF,
    "subject-case": OFF,
    "footer-leading-blank": OFF,
  },
};
