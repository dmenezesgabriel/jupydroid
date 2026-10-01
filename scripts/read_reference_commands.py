#!/usr/bin/env python3
"""The user-facing surface each reference offers, enumerated from its own source.

**Why this exists rather than a list in a document.** `docs/architecture.md` §1 states that the
references' command lists *are* the specification for this app. A parity ledger that compares our
surface against theirs needs that surface enumerated, not summarised, and needs it reproducible at
the SHAs `docs/README.md` pins - so that bumping a pin re-measures rather than quietly falsifying
prose, the way a stored test count drifted before `count-tests.sh` (ADR-0001).

**A measurement, not a gate**, on the same footing as `resolve_vscode_theme.py` and
`read_pane_widths.py`: nothing in the build fails on its output. It reads `reference/`, which is
gitignored and travels with no clone, so an absent clone is reported with the command that makes it
rather than treated as an error in the tree.

**The pattern travels with the count, because two reasonable patterns disagree.** Narrowing the
constant's *name* to `[A-Za-z]+` reports 405 ids where [COMMAND_ID] reports 412, and the seven it
loses are real: `markdown1` through `markdown6`
(`notebook:change-cell-to-heading-1`..`-6`) and `displayH1Numbering`
(`toc:display-h1-numbering`), whose names end in a digit. A TypeScript identifier may contain
digits, so dropping them undercounts the specification. A count is therefore only meaningful
together with the pattern that produced it, which is why the patterns are module constants with
their reasoning attached rather than inline literals.

```sh
python3 scripts/read_reference_commands.py --all
python3 scripts/read_reference_commands.py --jupyterlab-package notebook-extension
```

Tests in read_reference_commands_test.py.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from collections.abc import Iterator
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

#: Where the clones live. Gitignored, so every function that reads it says what to do when it is absent.
REFERENCE_ROOT = REPO / "reference/research_repos"

#: The table in `docs/README.md` that pins each clone. One home for the SHAs; this reads it.
PINNED_ROW = re.compile(r"^\| `([a-z0-9-]+)` \| `([0-9a-f]{40})` \| `([^`]+)`", re.M)

#: A JupyterLab command id, as the `CommandIDs` namespaces declare them:
#: `export const runAll = 'notebook:run-all-cells';`. The value must carry a namespace colon -
#: `export const FACTORY = 'Notebook'` is a widget factory name in the same namespace and is not a
#: command. Requiring the colon is what makes this 405 ids rather than 412.
#:
#: **The value may sit on the next line**, because the reference is prettier-formatted and a
#: declaration too long for one line is wrapped after the `=`. Requiring it on the `export const`
#: line missed 9 ids across 5 packages, three of them in `notebook-extension` itself:
#: `notebook:move-cursor-heading-above-or-collapse`, `-below-or-expand` and
#: `notebook:toggle-render-side-by-side-current` - two of which are the only command-mode way to
#: walk headings, so the gap they belong to was invisible to the measurement that should have found
#: it. `\s*` rather than `[ ]*` is the whole fix, and the colon rule is unchanged by it.
COMMAND_ID = re.compile(r"^\s+export const [A-Za-z]\w* *=\s*['\"]([a-z0-9-]+:[A-Za-z0-9:_.-]+)['\"]", re.M)

#: A member of `export namespace NotebookActions`, which is indented by exactly two spaces.
ACTION_FUNCTION = re.compile(r"^  export (?:async )?function (\w+)", re.M)

#: What `notebook_action_names` looks for. ADR-0027 makes this namespace the behaviour specification.
ACTIONS_NAMESPACE = "export namespace NotebookActions {"

#: `lib/` is compiled `src/`, so reading it would count every id twice; `node_modules/` is not the
#: reference at all.
SKIPPED_DIRS = frozenset({"node_modules", "lib"})

#: What `lsp_method_names` looks for. `packages/lsp/src/tokens.ts` sorts every LSP method the
#: connection layer routes into `Method`'s four direction enums, which is the whole of the ask
#: surface: a method absent there cannot cross this client's connection.
METHOD_NAMESPACE = "export namespace Method {"

#: One direction enum inside `Method`, whose body ends at the `}` in column 2 - the file's own
#: nesting, the same bound `_namespace_end` takes for the namespace itself.
LSP_DIRECTION = re.compile(r"export enum (\w+) \{(.*?)\n  \}", re.S)

#: A member's wire string: `INITIALIZE = 'initialize'` counts `initialize`, which is what crosses
#: the wire, not the identifier that names it.
LSP_MEMBER = re.compile(r"'([^']+)'")


def pinned_clones(readme: Path) -> dict[str, tuple[str, str]]:
    """Each reference project's pinned SHA and clone command, read off the table in [readme].

    ```
    pinned_clones(Path("docs/README.md"))["jupyterlab"][0]   # '7f0e690680a763d3c3db5dc0231803b0ba0e1e12'
    ```
    """
    rows = PINNED_ROW.findall(readme.read_text(encoding="utf-8"))
    return {project: (sha, clone) for project, sha, clone in rows}


def reference_dir(project: str, root: Path = REFERENCE_ROOT,
                  readme: Path = REPO / "docs/README.md") -> Path:
    """The clone of [project], or a refusal naming the command that would create it.

    ```
    reference_dir("jupyterlab")   # .../reference/research_repos/jupyterlab
    ```
    """
    clones = pinned_clones(readme)
    if project not in clones:
        raise KeyError(f"{project!r} is pinned nowhere in {readme}; pinned: {sorted(clones)}")
    path = root / project
    if not path.is_dir():
        sha, clone = clones[project]
        raise FileNotFoundError(
            f"{path} is absent - reference/ is gitignored and travels with no clone. "
            f"Make it with: {clone} {path} - then check out {sha}")
    return path


def clone_head(clone: Path) -> str | None:
    """The revision [clone] is actually at, or None where it is not a git working tree.

    A count measured against a different revision is a different count, so the report prints this
    beside every number instead of trusting the pin.
    """
    found = subprocess.run(["git", "-C", str(clone), "rev-parse", "HEAD"],
                           capture_output=True, text=True, check=False)
    return found.stdout.strip() or None


def jupyterlab_command_ids(packages: Path) -> dict[str, list[str]]:
    """Every command id declared under [packages], grouped by the package that declares it.

    Grouped because a package is the unit one slice of the audit takes - `notebook-extension` is
    one slice's worth of surface and `filebrowser-extension` another.

    ```
    jupyterlab_command_ids(reference_dir("jupyterlab") / "packages")["toc-extension"]
    ```
    """
    found: dict[str, set[str]] = {}
    for source in _typescript_under(packages):
        ids = COMMAND_ID.findall(source.read_text(encoding="utf-8", errors="replace"))
        if ids:
            found.setdefault(source.relative_to(packages).parts[0], set()).update(ids)
    return {package: sorted(ids) for package, ids in sorted(found.items())}


def _typescript_under(packages: Path) -> Iterator[Path]:
    """Every `.ts`/`.tsx` file under [packages] that is source rather than output or dependency."""
    for pattern in ("*.ts", "*.tsx"):
        for source in sorted(packages.rglob(pattern)):
            if SKIPPED_DIRS.isdisjoint(source.parts):
                yield source


def notebook_action_names(actions: Path) -> list[str]:
    """The members of `export namespace NotebookActions` in [actions], which is `actions.tsx`.

    Bounded to the namespace rather than the file: a `namespace Private` follows it with members of
    the same shape, and `export function setCellExecutor` sits between them at top level. Counting
    to the end of the file reports 83 where the actual surface is 69.

    ```
    notebook_action_names(reference_dir("jupyterlab") / "packages/notebook/src/actions.tsx")
    ```
    """
    text = actions.read_text(encoding="utf-8")
    start = text.find(ACTIONS_NAMESPACE)
    if start < 0:
        raise ValueError(f"{actions} declares no `{ACTIONS_NAMESPACE}`, so it is not the file "
                         f"holding the action surface")
    return sorted(set(ACTION_FUNCTION.findall(text[start:_namespace_end(text, start)])))


def _namespace_end(text: str, start: int) -> int:
    """Where the namespace opened at [start] closes - a `}` in column 0, the file's own nesting."""
    end = text.find("\n}", start)
    return len(text) if end < 0 else end


def vscode_jupyter_command_ids(manifest: Path) -> list[str]:
    """Every command the extension contributes, from its `package.json`.

    This is the whole of what a VS Code extension can be asked to do: a command absent here is
    unreachable from the palette, a menu or a keybinding.

    ```
    vscode_jupyter_command_ids(reference_dir("vscode-jupyter") / "package.json")
    ```
    """
    contributes = json.loads(manifest.read_text(encoding="utf-8")).get("contributes", {})
    commands = contributes.get("commands")
    if not commands:
        raise ValueError(f"{manifest} declares no `contributes.commands`; it contributes "
                         f"{sorted(contributes)}")
    return sorted({entry["command"] for entry in commands})


def lsp_method_names(tokens_ts: Path) -> dict[str, list[str]]:
    """The LSP methods `Method` enumerates in [tokens_ts], grouped by its direction enum.

    Bounded to the namespace rather than the file: `tokens.ts` keeps declaring exported types
    after it, and an enum outside `Method` is not a direction the connection routes.

    ```
    lsp_method_names(reference_dir("jupyterlab") / "packages/lsp/src/tokens.ts")
    ```
    """
    text = tokens_ts.read_text(encoding="utf-8")
    start = text.find(METHOD_NAMESPACE)
    if start < 0:
        raise ValueError(f"{tokens_ts} declares no `{METHOD_NAMESPACE}`, so it is not the file "
                         f"holding the LSP method surface")
    body = text[start:_namespace_end(text, start)]
    return {direction: LSP_MEMBER.findall(members)
            for direction, members in LSP_DIRECTION.findall(body)}


def rootfs_kernel_message_types(kernelbase: Path) -> dict[str, list[str]]:
    """The message types the shipped kernel answers, from its own dispatch lists.

    The shipped rootfs is what `docs/architecture/cross-cutting.md` §8.3 makes authoritative for
    what the kernel on this device does - not the published spec, which may describe a version this
    image does not carry. Extract the file first:

    ```sh
    T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
    zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/ipykernel/kernelbase.py > /tmp/kb.py
    python3 scripts/read_reference_commands.py --kernelbase /tmp/kb.py
    ```
    """
    text = kernelbase.read_text(encoding="utf-8")
    return {"shell": _declared_strings(text, "msg_types", kernelbase),
            "control_only": _declared_strings(text, "control_msg_types", kernelbase)}


def _declared_strings(text: str, name: str, source: Path) -> list[str]:
    """The quoted strings in the list literal `name = [...]`, in declaration order.

    `control_msg_types` opens with `*msg_types`, which is a splat and not a string, so what this
    returns for it is the control channel's own types rather than all of them twice.
    """
    opener = re.compile(rf"^\s*{name} = \[", re.M).search(text)
    if not opener:
        raise ValueError(f"{source} declares no `{name} = [`, so it is not the file holding the "
                         f"kernel's message dispatch")
    return re.findall(r'"([a-z_]+)"', text[opener.end():text.index("]", opener.end())])


def _report_jupyterlab(root: Path, readme: Path) -> None:
    """Every package that declares a command id, largest surface first."""
    clone = reference_dir("jupyterlab", root, readme)
    print(f"jupyterlab @ {clone_head(clone)}")
    by_package = jupyterlab_command_ids(clone / "packages")
    unique = {found for ids in by_package.values() for found in ids}
    print(f"  command ids           {len(unique)} unique across {len(by_package)} packages")
    for package, ids in sorted(by_package.items(), key=lambda row: (-len(row[1]), row[0])):
        print(f"    {len(ids):4d}  {package}")
    actions = notebook_action_names(clone / "packages/notebook/src/actions.tsx")
    print(f"  NotebookActions       {len(actions)} functions")


def _report_vscode_jupyter(root: Path, readme: Path) -> None:
    clone = reference_dir("vscode-jupyter", root, readme)
    ids = vscode_jupyter_command_ids(clone / "package.json")
    print(f"vscode-jupyter @ {clone_head(clone)}")
    print(f"  contributes.commands  {len(ids)}")


def _report_lsp_methods(clone: Path) -> None:
    tokens = clone / "packages/lsp/src/tokens.ts"
    directions = lsp_method_names(tokens)
    print(f"jupyterlab @ {clone_head(clone)} {tokens.relative_to(clone)}")
    for direction, methods in directions.items():
        print(f"  {direction:<20} {len(methods)}: {', '.join(methods)}")


def _report_kernelbase(kernelbase: Path) -> None:
    types = rootfs_kernel_message_types(kernelbase)
    print(f"shipped kernel @ {kernelbase}")
    print(f"  shell                 {len(types['shell'])}: {', '.join(types['shell'])}")
    print(f"  control only          {len(types['control_only'])}: {', '.join(types['control_only'])}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Enumerate what each reference offers.")
    parser.add_argument("--all", action="store_true", help="report every reference (the default)")
    parser.add_argument("--jupyterlab-package", help="print one package's command ids, one per line")
    parser.add_argument("--notebook-actions", action="store_true",
                        help="print NotebookActions' members, one per line")
    parser.add_argument("--vscode-jupyter-commands", action="store_true",
                        help="print the contributed command ids, one per line")
    parser.add_argument("--kernelbase", type=Path,
                        help="an ipykernel kernelbase.py extracted from the shipped rootfs")
    parser.add_argument("--lsp-methods", action="store_true",
                        help="print Method's direction enums and their members")
    parser.add_argument("--reference-root", type=Path, default=REFERENCE_ROOT,
                        help="where the clones live")
    args = parser.parse_args()
    readme = REPO / "docs/README.md"
    if args.jupyterlab_package:
        clone = reference_dir("jupyterlab", args.reference_root, readme)
        print(*jupyterlab_command_ids(clone / "packages").get(args.jupyterlab_package, []), sep="\n")
    elif args.notebook_actions:
        clone = reference_dir("jupyterlab", args.reference_root, readme)
        print(*notebook_action_names(clone / "packages/notebook/src/actions.tsx"), sep="\n")
    elif args.vscode_jupyter_commands:
        clone = reference_dir("vscode-jupyter", args.reference_root, readme)
        print(*vscode_jupyter_command_ids(clone / "package.json"), sep="\n")
    elif args.kernelbase:
        _report_kernelbase(args.kernelbase)
    elif args.lsp_methods:
        _report_lsp_methods(reference_dir("jupyterlab", args.reference_root, readme))
    else:
        _report_jupyterlab(args.reference_root, readme)
        _report_vscode_jupyter(args.reference_root, readme)


if __name__ == "__main__":
    main()
