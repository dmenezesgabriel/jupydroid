"""Prove the image's interpreter sees a package that pip installs after it started.

Run by the Dockerfile with `PYTHONUSERBASE` naming a directory that does not exist
yet, which is the state every Space starts in: `SpaceDirs.ensure` creates `pkgs`, and
the first `pip install` is what writes the user site under it. One interpreter, the
shape of a kernel:

- the user site is on `sys.path`, and before the image's own site-packages - the
  place `site.addusersitepackages` gives it when the directory already exists, so a
  package a person upgrades shadows the shipped one;
- an import that misses is still a miss, because nothing has installed the package;
- a package written into the user site afterwards imports in that same interpreter,
  which is the cell below `%pip install`. ADR-0164 records why the directory has to be
  created and not only added to `sys.path`.

Run by the Dockerfile's gate; also runnable on a host with the module on `PYTHONPATH`:

```
PYTHONPATH=tools/rootfs PYTHONUSERBASE=/tmp/probe-pkgs python3 tools/rootfs/user_site_probe.py
```
"""

from __future__ import annotations

import importlib
import os
import site
import sys

PACKAGE = "probe_later_module"


def main() -> None:
    """Assert the three readings above, then print `ok`."""
    assert site.ENABLE_USER_SITE, "the user site is disabled; expected it enabled for this interpreter"
    user_site = site.getusersitepackages()
    print(f"user_site: {user_site}")
    assert user_site in sys.path, f"'{user_site}' is not on sys.path; expected the user site there"
    first_shipped = next((i for i, entry in enumerate(sys.path) if entry in site.getsitepackages()), len(sys.path))
    assert sys.path.index(user_site) < first_shipped, (
        f"the user site is at {sys.path.index(user_site)}, after the shipped packages at {first_shipped}; "
        "expected it before them, as site.addusersitepackages orders it"
    )
    assert not is_importable(), f"'{PACKAGE}' was importable before anything installed it"
    os.makedirs(user_site, exist_ok=True)
    with open(os.path.join(user_site, f"{PACKAGE}.py"), "w", encoding="utf-8") as installed:
        installed.write("origin = 'user'\n")
    imported = importlib.import_module(PACKAGE)
    assert getattr(imported, "origin", None) == "user", f"'{PACKAGE}' did not resolve to the user site's copy"
    print("ok")


def is_importable() -> bool:
    """Whether [PACKAGE] resolves now, which a fresh Space's first `import` must answer no to."""
    try:
        importlib.import_module(PACKAGE)
    except ModuleNotFoundError:
        return False
    return True


if __name__ == "__main__":
    main()
