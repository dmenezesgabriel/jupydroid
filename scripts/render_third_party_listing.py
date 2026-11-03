#!/usr/bin/env python3
"""The third-party listing (`THIRD_PARTY.md`), rendered from a syft SBOM of the arm64 rootfs.

**Why this exists.** The APK ships a rootfs of Alpine and pip packages under their own licences. A
stranger who clones the repository should find what is in it without running anything, and a later
rootfs build should change the listing by a diff, not by someone's memory. So the file is produced,
never edited.

**What is listed.** Only the `apk` and `python` artifacts syft reports. Its `binary` artifacts are
Windows launcher executables inside setuptools and pydevd (`cli-arm64`, `attach_x86`, version
`UNKNOWN`): files of packages that are already rows, not packages.

**The licence cell** is what the package's own metadata declares, with the junk syft's cataloguers
add removed. A licence-file hash (`sha256:...`) is not a licence. `AND`/`OR`/`WITH` alone are the
operators of an expression syft split into one value per term. Several terms are listed once each,
in order. A value outside the SPDX list (`MIT License`) is shown as written: normalising it would
be a claim the metadata does not make. A package that declares nothing syft could read is
`NOASSERTION`, never blank.

The SBOM comes from `tools/rootfs/sbom.sh`, which pins the syft container by digest:

```sh
tools/rootfs/sbom.sh arm64-v8a      # writes THIRD_PARTY.md
python3 scripts/render_third_party_listing.py --sbom sbom.json --rootfs-sha256 <hex> --out THIRD_PARTY.md
```

Tests in render_third_party_listing_test.py.
"""

from __future__ import annotations

import argparse
import dataclasses
import json
import sys
from collections import Counter
from pathlib import Path
from typing import NotRequired, TypedDict, cast

ECOSYSTEMS = ("apk", "python")

#: Terms syft splits an SPDX expression into that are operators, not licences.
EXPRESSION_OPERATORS = frozenset({"AND", "OR", "WITH"})

NO_LICENCE = "NOASSERTION"
NO_SOURCE = "-"


class SyftLicence(TypedDict):
    value: str
    spdxExpression: NotRequired[str]


class SyftArtifact(TypedDict):
    name: str
    version: str
    type: str
    licenses: NotRequired[list[SyftLicence]]
    metadata: NotRequired[dict[str, object]]


class SyftDescriptor(TypedDict):
    version: str


class SyftSbom(TypedDict):
    artifacts: list[SyftArtifact]
    descriptor: SyftDescriptor


@dataclasses.dataclass(frozen=True)
class ListedPackage:
    """One row of the listing."""

    name: str
    version: str
    ecosystem: str
    licence: str
    source: str


def parse_sbom(text: str) -> SyftSbom:
    """Parses syft-json, refusing a document that lacks what the listing reads.

    ```
    parse_sbom('{"artifacts": [], "descriptor": {"version": "1.55.0"}}')["descriptor"]["version"]  # "1.55.0"
    ```

    Args:
        text: the contents of a file written by `syft -o syft-json`.

    Returns:
        The document, typed to the fields this module reads.

    Raises:
        ValueError: the text is not JSON, or has no `artifacts` list or no `descriptor.version`.
    """
    try:
        document: object = json.loads(text)
    except json.JSONDecodeError as error:
        raise ValueError(f"not JSON ({error}); expected syft-json, got {text[:40]!r}") from error
    if not isinstance(document, dict) or not isinstance(document.get("artifacts"), list):
        keys = sorted(document) if isinstance(document, dict) else type(document).__name__
        raise ValueError(f"syft-json needs an `artifacts` list; the document has {keys}")
    descriptor = document.get("descriptor")
    if not isinstance(descriptor, dict) or not isinstance(descriptor.get("version"), str):
        raise ValueError(f"syft-json needs a string descriptor.version; got descriptor={descriptor!r}")
    return cast(SyftSbom, document)


def is_licence_term(licence: SyftLicence) -> bool:
    """Whether a syft licence value names a licence, rather than a file hash or an operator."""
    value = licence["value"]
    return bool(value) and not value.startswith("sha256:") and value not in EXPRESSION_OPERATORS


def licence_cell(licences: list[SyftLicence]) -> str:
    """The licence column: each distinct declared term once, in order, or `NOASSERTION`.

    ```
    licence_cell([{"value": "0BSD"}, {"value": "AND"}, {"value": "MIT"}])   # "0BSD, MIT"
    ```

    Args:
        licences: the `licenses` of one syft artifact.

    Returns:
        The terms joined by commas.
    """
    terms = [licence["value"] for licence in licences if is_licence_term(licence)]
    return ", ".join(dict.fromkeys(terms)) or NO_LICENCE


def source_url(artifact: SyftArtifact) -> str:
    """The `url` an apk's database entry carries; a pip package's metadata gives syft none."""
    url = artifact.get("metadata", {}).get("url")
    return url if isinstance(url, str) and url else NO_SOURCE


def listed_package(artifact: SyftArtifact) -> ListedPackage:
    """One artifact as a row."""
    return ListedPackage(
        name=artifact["name"],
        version=artifact["version"],
        ecosystem=artifact["type"],
        licence=licence_cell(artifact.get("licenses", [])),
        source=source_url(artifact),
    )


def listed_packages(sbom: SyftSbom) -> list[ListedPackage]:
    """The rows, de-duplicated and sorted by ecosystem, then name ignoring case, then version."""
    rows = {listed_package(a) for a in sbom["artifacts"] if a["type"] in ECOSYSTEMS}
    return sorted(rows, key=lambda r: (r.ecosystem, r.name.casefold(), r.name, r.version))


def escape_cell(text: str) -> str:
    """Escapes the one character that would split a Markdown table row."""
    return text.replace("|", r"\|")


def table_row(row: ListedPackage) -> str:
    cells = (row.name, row.version, row.ecosystem, row.licence, row.source)
    return "| " + " | ".join(escape_cell(cell) for cell in cells) + " |"


def header(rows: list[ListedPackage], syft_version: str, rootfs_sha256: str) -> str:
    counts = Counter(row.ecosystem for row in rows)
    per_ecosystem = ", ".join(f"{eco} {counts[eco]}" for eco in ECOSYSTEMS)
    return (
        "# Third-party software in the rootfs\n\n"
        "Generated by `tools/rootfs/sbom.sh`; do not edit. A rebuilt rootfs changes this file by a diff.\n\n"
        f"Source: syft {syft_version} over `rootfs-arm64-v8a.tzst`, sha256 `{rootfs_sha256}`.\n\n"
        f"Packages: {len(rows)} ({per_ecosystem})\n\n"
        "Licences are what each package's own metadata declares, listed once per term. A value outside the\n"
        "SPDX list is shown as written, and `NOASSERTION` means the package declared none syft could read.\n\n"
        "Not covered by this table: the Alpine `busybox` row is the package syft found, but the shipped\n"
        "busybox is built from source in `tools/rootfs/Dockerfile`; `proot` is staged into the APK's native\n"
        "libraries, outside the rootfs; and the Gradle libraries are not read here.\n\n"
    )


def render_listing(sbom: SyftSbom, rootfs_sha256: str) -> str:
    """The whole `THIRD_PARTY.md`.

    ```
    render_listing(parse_sbom(path.read_text()), "ab" * 32)
    ```

    Args:
        sbom: a parsed syft-json document.
        rootfs_sha256: sha256 of the tarball syft read, so a changed rootfs shows in the diff.

    Returns:
        Markdown ending in one newline. The same SBOM and hash always give the same bytes.
    """
    rows = listed_packages(sbom)
    table = ["| Package | Version | Ecosystem | Licence | Source |", "|---|---|---|---|---|"]
    table += [table_row(row) for row in rows]
    return header(rows, sbom["descriptor"]["version"], rootfs_sha256) + "\n".join(table) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Render THIRD_PARTY.md from a syft-json SBOM.")
    parser.add_argument("--sbom", type=Path, required=True, help="syft-json file")
    parser.add_argument("--rootfs-sha256", required=True, help="sha256 of the rootfs tarball syft read")
    parser.add_argument("--out", type=Path, required=True, help="where to write the listing")
    args = parser.parse_args(argv)
    sbom = parse_sbom(args.sbom.read_text(encoding="utf-8"))
    args.out.write_text(render_listing(sbom, args.rootfs_sha256), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
