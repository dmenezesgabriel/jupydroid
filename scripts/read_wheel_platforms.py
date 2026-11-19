#!/usr/bin/env python3
"""Which releases of a PyPI package ship a wheel a Space's pip can install.

**Why this exists.** A Space is Alpine on musl with Python 3.12 on aarch64, and its pip installs wheels
only (`tools/rootfs/Dockerfile`: no C toolchain, `--only-binary=:all:`), so it looks for a `cp312`
`musllinux_*_aarch64` wheel. A package that publishes none answers "No matching distribution found",
which reads as a typo. How many packages are in that case, and whether an older release escapes it,
is a count PyPI holds and this reads.

**A measurement, not a gate.** Nothing in the build fails on its output, and PyPI moves, so the
numbers are quoted with the date they were read.

**Installable means a wheel pip would pick**: the Python tag matches (or is an `abi3` wheel of an
older CPython, or `py3`), and the platform is `any` or a `musllinux_*` tag of the architecture.
A `manylinux` wheel is not: glibc's startup is killed by Android's seccomp filter (ADR-0003).

```sh
python3 scripts/read_wheel_platforms.py duckdb scikit-learn pandas
python3 scripts/read_wheel_platforms.py numpy --python cp312 --family musllinux --arch aarch64
```

Tests in read_wheel_platforms_test.py.
"""

from __future__ import annotations

import argparse
import dataclasses
import json
import sys
import urllib.request
from collections.abc import Sequence
from typing import Protocol

PYPI_URL = "https://pypi.org/pypi/{package}/json"

#: `{name}-{version}[-{build}]-{python}-{abi}-{platform}.whl`, PEP 427.
WHEEL_SHAPE = "{name}-{version}[-{build}]-{python}-{abi}-{platform}.whl"


@dataclasses.dataclass(frozen=True)
class Target:
    """The interpreter and platform a Space's pip installs for, e.g. `cp312`, `musllinux`, `aarch64`."""

    python: str
    family: str
    arch: str


@dataclasses.dataclass(frozen=True)
class Releases:
    """A package's latest version and, per version, the wheel filenames it published."""

    latest: str
    files: dict[str, tuple[str, ...]]


@dataclasses.dataclass(frozen=True)
class Measurement:
    """What one package offers the target: counts over every release, and the latest release's wheels."""

    package: str
    releases: int
    installable_releases: int
    latest: str
    latest_installable: tuple[str, ...]
    latest_other_linux: tuple[str, ...]


class WheelIndex(Protocol):
    """Where release files come from; PyPI in production, a fake in a test."""

    def releases_of(self, package: str) -> Releases: ...


class PypiWheelIndex:
    """[WheelIndex] over PyPI's JSON API."""

    def releases_of(self, package: str) -> Releases:
        with urllib.request.urlopen(PYPI_URL.format(package=package), timeout=30) as response:
            document = json.load(response)
        files = {
            version: tuple(entry["filename"] for entry in entries if entry["filename"].endswith(".whl"))
            for version, entries in document["releases"].items()
        }
        return Releases(latest=document["info"]["version"], files=files)


def installable(filename: str, target: Target) -> bool:
    """Whether pip, installing for [target], would pick the wheel [filename].

    ```
    installable("pandas-3.1.1-cp312-cp312-musllinux_1_2_aarch64.whl", Target("cp312", "musllinux", "aarch64"))
    # True
    ```
    """
    parts = filename.removesuffix(".whl").split("-")
    if not filename.endswith(".whl") or len(parts) not in (5, 6):
        raise ValueError(f"not a wheel filename: {filename!r}, expected {WHEEL_SHAPE}")
    python, abi, platform = parts[-3:]
    return _python_fits(python, abi, target.python) and _platform_fits(platform, target)


def _python_fits(python: str, abi: str, wanted: str) -> bool:
    """Whether the wheel's Python tags cover [wanted]: exact, `py3`, or an `abi3` wheel of an older CPython."""
    tags = python.split(".")
    if wanted in tags or "py3" in tags:
        return True
    return abi == "abi3" and any(_minor(tag) <= _minor(wanted) for tag in tags if tag.startswith("cp"))


def _minor(cpython_tag: str) -> int:
    """The minor version of a tag like `cp312` (12) or `cp39` (9)."""
    return int(cpython_tag.removeprefix("cp")[1:])


def _platform_fits(platform: str, target: Target) -> bool:
    """Whether any of the dot-joined platform tags is `any` or of [target]'s family and architecture."""
    return any(
        tag == "any" or (tag.startswith(f"{target.family}_") and tag.endswith(f"_{target.arch}"))
        for tag in platform.split(".")
    )


def measure(package: str, index: WheelIndex, target: Target) -> Measurement:
    """What [package] offers [target], read from [index].

    ```
    measure("duckdb", PypiWheelIndex(), Target("cp312", "musllinux", "aarch64"))
    ```
    """
    releases = index.releases_of(package)
    fitting = {
        version: tuple(name for name in names if installable(name, target)) for version, names in releases.files.items()
    }
    latest_files = releases.files[releases.latest]
    return Measurement(
        package=package,
        releases=len(releases.files),
        installable_releases=sum(1 for names in fitting.values() if names),
        latest=releases.latest,
        latest_installable=fitting[releases.latest],
        latest_other_linux=tuple(
            name for name in latest_files if "linux" in name and name not in fitting[releases.latest]
        ),
    )


def describe(found: Measurement, target: Target) -> list[str]:
    """The report lines for one [Measurement]: the count, then what the latest release has or ships instead."""
    wanted = f"{target.python} {target.family} {target.arch}"
    head = f"{found.package}: {found.installable_releases} of {found.releases} releases have a wheel for {wanted}"
    if found.latest_installable:
        return [head, f"  latest {found.latest} has: {', '.join(found.latest_installable)}"]
    return [head, f"  latest {found.latest} ships instead: {', '.join(found.latest_other_linux) or 'no linux wheel'}"]


def main(argv: Sequence[str], index: WheelIndex) -> None:
    """Print the measurement of each package named in [argv] against [index]."""
    parser = argparse.ArgumentParser(description="Count the releases of PyPI packages a Space's pip can install.")
    parser.add_argument("packages", nargs="+")
    parser.add_argument("--python", default="cp312")
    parser.add_argument("--family", default="musllinux")
    parser.add_argument("--arch", default="aarch64")
    options = parser.parse_args(argv)
    target = Target(options.python, options.family, options.arch)
    for package in options.packages:
        print("\n".join(describe(measure(package, index, target), target)))


if __name__ == "__main__":
    main(sys.argv[1:], PypiWheelIndex())
