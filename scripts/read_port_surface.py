#!/usr/bin/env python3
"""This app's own port surface: every operation `domain/port/` declares, and whether anything uses it.

**Why this exists.** `docs/architecture/building-blocks.md` §5.3 keeps a uniform port surface on
purpose - a single-implementation port stays a port, because that consistency is the map a newcomer
navigates by. The cost of that rule is that an operation nobody calls looks exactly like an
operation kept for discoverability, and the Working Agreement's "no speculative features" cannot be
checked by reading one file. This reads them all.

**A measurement, not a gate.** Nothing in the build fails on its output. Whether dead port surface
should block a commit is a decision this tool exists to inform, not one it takes.

**What it counts is *mentions*, and the zero is the finding.** A name reached through a variable
(`gateway.execute(...)`) cannot be resolved to its declaration without a Kotlin front end, so a
non-zero count is an upper bound on real use: another type's identically named member counts too. A
**zero** is the strong claim - no production file names that identifier at all, under any receiver -
and that is what [unused_operations] reports.

**Comments are not uses**, which is why this shares `kotlin_source.without_comments` with the gates.
Two gates in this repository have already reported a member as used because a KDoc named it, and the
first measurement taken with a plain grep here made the same mistake in reverse: `SpaceConfig.kt`'s
KDoc mentions `SpaceRepository`, which is not a call.

**A `Fake…` is not production use.** Every port has one by rule, and it overrides every operation, so
counting fakes would make nothing ever unused. Neither is the port's own declaration.

```sh
python3 scripts/read_port_surface.py            # every port, every operation, with mention counts
python3 scripts/read_port_surface.py --unused   # only what nothing names
```

Tests in read_port_surface_test.py.
"""
from __future__ import annotations

import argparse
import dataclasses
import re
from pathlib import Path

from kotlin_source import without_comments

REPO = Path(__file__).resolve().parent.parent

#: Repo-relative, so the module can be pointed at a fixture tree in a test and at the real one here.
PORT_DIR = "app/src/main/java/com/jupydroid/app/domain/port"
MAIN_DIR = "app/src/main/java/com/jupydroid/app"
FAKE_DIR = "app/src/main/java/com/jupydroid/app/data/fake"

#: `fun interface` is one too - `OkHttpKernelSocketOpener`'s port is declared that way.
INTERFACE = re.compile(r"^(?:fun )?interface (\w+)", re.M)

#: A member of an interface body, which this codebase indents by two. A `companion object`'s own
#: members are indented by four and are constants rather than surface, so they fall out.
MEMBER = re.compile(r"^  (?:suspend )?(?:fun|val|var) (\w+)", re.M)

#: A path part whose files are not production use of a port.
NOT_PRODUCTION = frozenset({"fake"})

#: What sits immediately before a name that is being *declared* rather than used. Two things made
#: this necessary, both measured here: every port has a real adapter as well as a `Fake…` and each
#: overrides every operation, so counting an override left the first run reporting zero unused
#: operations across all 19 ports; and an unrelated declaration of the same name masks a dead one -
#: `StdioLanguageServer` holds a `val capabilities: LanguageCapabilities` of its own, which is not
#: the port's `capabilities(space)`. A declaration of a name is never a use of the operation.
DECLARES = re.compile(r"(?:^|[^.\w])(?:suspend\s+)?(?:fun|val|var)\s+$")


@dataclasses.dataclass(frozen=True)
class Operation:
    """One declared operation: which port declares it, its name, and the line it is declared on."""

    port: str
    name: str
    line: int


def operations_of(source: str) -> list[Operation]:
    """Every operation the interfaces in [source] declare, in declaration order.

    ```
    operations_of("interface Sharer {\\n  suspend fun share(path: String)\\n}\\n")
    # [Operation(port='Sharer', name='share', line=2)]
    ```
    """
    code = without_comments(source)
    found: list[Operation] = []
    for declaration in INTERFACE.finditer(code):
        opened, body = _body_after(code, declaration.end())
        found += [Operation(declaration.group(1), member.group(1),
                            code.count("\n", 0, opened + member.start()) + 1)
                  for member in MEMBER.finditer(body)]
    return found


def _body_after(code: str, start: int) -> tuple[int, str]:
    """The braced block opening at or after [start], with the offset it opens at.

    Brace-matched rather than read to the next `}` in column 0: a port may declare a nested
    `companion object`, and stopping at its closer would hide every operation below it.
    """
    opened = code.find("{", start)
    if opened < 0:
        return start, ""
    depth = 0
    for at in range(opened, len(code)):
        depth += (code[at] == "{") - (code[at] == "}")
        if depth == 0:
            return opened, code[opened:at]
    return opened, code[opened:]


def ports_under(port_dir: Path) -> dict[str, list[Operation]]:
    """Every port declared under [port_dir], keyed by its interface name.

    A file declaring no interface contributes nothing: `FileNameTakenException.kt` and
    `KernelChoice.kt` live in `domain/port/` and are an exception and a sealed choice, not ports.
    """
    found: dict[str, list[Operation]] = {}
    for source in sorted(port_dir.rglob("*.kt")):
        for operation in operations_of(source.read_text(encoding="utf-8")):
            found.setdefault(operation.port, []).append(operation)
    return dict(sorted(found.items()))


def production_code(tree: Path) -> dict[Path, str]:
    """Every production Kotlin file under [tree], read once and stripped of its prose.

    **Read once on purpose.** Stripping comments per operation made this tool itself
    O(operations x files) in file reads - 150 operations against 426 files, which did not finish
    inside two minutes. The names are searched against this map instead.
    """
    sources = (path for path in sorted(tree.rglob("*.kt")) if NOT_PRODUCTION.isdisjoint(path.parts))
    return {path: without_comments(path.read_text(encoding="utf-8")) for path in sources}


def naming(name: str, code: dict[Path, str]) -> list[Path]:
    """Every file in [code] that *uses* the identifier [name], whole-identifier only.

    `readNotebook` is not found inside `readNotebookOutputs`, and an `override` of the operation is
    not a use of it - see [_declares_here].
    """
    word = re.compile(rf"(?<![A-Za-z0-9_]){re.escape(name)}(?![A-Za-z0-9_])")
    return [path for path, text in code.items()
            if any(not _declares_here(text, found.start()) for found in word.finditer(text))]


def _declares_here(text: str, at: int) -> bool:
    """Whether the occurrence at [at] is a declaration of the name rather than a use of it.

    Per *occurrence*, not per line, because a delegating adapter writes both on one:
    `override suspend fun interrupt(s: KernelSession) = issued.gatewayKnowing(s).interrupt(s)`
    declares the operation and calls it, and a line-level check would lose the call.
    """
    line_start = text.rfind("\n", 0, at) + 1
    return bool(DECLARES.search(text[line_start:at]))


def named_in(name: str, tree: Path) -> list[Path]:
    """Every Kotlin file under [tree] whose code - not its prose - names the identifier [name]."""
    return naming(name, production_code(tree))


def unused_operations(port_dir: Path, production: Path) -> list[Operation]:
    """Every declared operation that no production file under [production] names.

    ```
    unused_operations(REPO / PORT_DIR, REPO / MAIN_DIR)
    ```
    """
    return _unused(ports_under(port_dir), production_code(production), port_dir.resolve())


def _unused(ports: dict[str, list[Operation]], code: dict[Path, str],
            port_dir: Path) -> list[Operation]:
    """The declared operations of [ports] that nothing in [code] but their own declaration names."""
    declared = [operation for operations in ports.values() for operation in operations]
    return [operation for operation in declared
            if not _named_outside(operation.name, code, port_dir)]


def _named_outside(name: str, code: dict[Path, str], port_dir: Path) -> bool:
    """Whether [name] is named anywhere in [code] but its own declaration."""
    users = (path.resolve() for path in naming(name, code))
    return any(port_dir not in path.parents for path in users)


def _report(port_dir: Path, production: Path) -> None:
    """Every port with its operations, each marked by how many production files name it."""
    code = production_code(production)
    ports = ports_under(port_dir)
    unused = {(operation.port, operation.name) for operation in _unused(ports, code, port_dir.resolve())}
    print(f"{len(ports)} ports, {sum(len(ops) for ops in ports.values())} operations, "
          f"{len(unused)} named nowhere in production code")
    for port, operations in ports.items():
        print(f"\n{port}  ({len(operations)})")
        _print_operations(port, operations, code, unused)


def _print_operations(port: str, operations: list[Operation], code: dict[Path, str],
                      unused: set[tuple[str, str]]) -> None:
    """One line per operation: where it is declared, and how many production files name it."""
    for operation in operations:
        mark = ("NAMED NOWHERE" if (port, operation.name) in unused
                else f"{len(naming(operation.name, code))} files")
        print(f"  {operation.name:28s} :{operation.line:<4d} {mark}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Read this app's port surface and its use.")
    parser.add_argument("--unused", action="store_true",
                        help="print only the operations nothing names, one per line")
    parser.add_argument("--port-dir", type=Path, default=REPO / PORT_DIR)
    parser.add_argument("--production", type=Path, default=REPO / MAIN_DIR)
    args = parser.parse_args()
    if not args.unused:
        _report(args.port_dir, args.production)
        return
    for operation in unused_operations(args.port_dir, args.production):
        print(f"{operation.port}.{operation.name}  {PORT_DIR}/{operation.port}.kt:{operation.line}")


if __name__ == "__main__":
    main()
