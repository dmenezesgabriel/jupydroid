#!/bin/sh
# jupydroid-env - make a Python environment for a project folder and register its kernel.
#
#   jupydroid-env create <project>
#
# <project> is a top-level folder of the workspace - the folders DiskSpaceEnvironments scans
# (app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceEnvironments.kt). The environment made
# is <project>/.venv, vscode-python's own create convention, by the gnu payload's interpreter -
# the only one whose venv can carry the manylinux wheels a kernel exists for (ADR-0166); a musl
# env stays the terminal's `python3 -m venv` plus Register.
#
# The kernelspec written is the one SpaceKernelSpec.installEnvironment writes for the same env
# (app/src/main/java/com/jupydroid/app/data/space/SpaceKernelSpec.kt) - one shape, two writers,
# pinned by EnvironmentCreateDeviceTest - so create and Register give the same kernel.
#
# Installed at /usr/local/bin by JupydroidEnvHelper.install
# (app/src/main/java/com/jupydroid/app/data/space/JupydroidEnvHelper.kt): the Space's terminal
# runs it by name and SpaceShell runs these same bytes for the app.
set -eu

GNU_PYTHON=/opt/gnu/python/bin/python3.12
KERNELS_DIR=/usr/local/share/jupyter/kernels

usage() {
  echo "usage: jupydroid-env create <project>" >&2
  exit 2
}

# Create-only: registering an environment made by hand stays the app's disk act
# (app/src/main/java/com/jupydroid/app/domain/port/SpaceEnvironments.kt), which needs no proot -
# ADR-0078's explicit Register, never an implicit discovery.
[ "${1:-}" = "create" ] || usage
[ "$#" -eq 2 ] || usage
project=$2

# A project is one top-level folder: a single segment, not hidden, not itself an env container -
# DiskSpaceEnvironments' scan rule, so what it offers is what this accepts.
case "$project" in
  ""|.|..|*/*|.*|envs|Envs)
    echo "jupydroid-env: '$project' is no project folder; expected a top-level directory name" >&2
    exit 2
    ;;
esac
[ -d "$HOME/$project" ] || {
  echo "jupydroid-env: no project folder '$project' in $HOME" >&2
  exit 1
}

env_path="$project/.venv"
env_dir="$HOME/$env_path"
[ ! -e "$env_dir" ] || {
  echo "jupydroid-env: '$env_dir' already exists" >&2
  exit 1
}

# Every gnu-python call below runs under the two entries of the kernel env that decide whether a
# glibc process survives proot: no musl preload may enter it, and the patched tracer answers its
# seccomp traps with ENOSYS rather than a SIGSYS glibc's threads cannot survive (ADR-0166) - the
# arm KernelModelProbeDeviceTest's pip measured.
echo "== $GNU_PYTHON -m venv $env_dir"
env LD_PRELOAD= PROOT_SIGSYS_ENOSYS=1 "$GNU_PYTHON" -m venv "$env_dir"

echo "== $env_dir/bin/python -m pip install ipykernel"
env LD_PRELOAD= PROOT_SIGSYS_ENOSYS=1 "$env_dir/bin/python" -m pip install --no-input ipykernel

# The spec's name is PythonEnvironment.kernelName's rule on the env's workspace-relative path
# (app/src/main/java/com/jupydroid/app/domain/model/PythonEnvironment.kt): a project is never an
# env container, so no leading segment is stripped; lowercased, every [^a-z0-9._-] folded to
# '-'. python3 does the fold and the JSON write, so a folder named with a quote or a backslash
# cannot break the spec.
name=$(python3 -c 'import re, sys; print(re.sub(r"[^a-z0-9._-]", "-", sys.argv[1].lower()))' "$env_path")
python3 - "$KERNELS_DIR/$name/kernel.json" "$env_dir/bin/python" "Python ($env_path)" <<'PY'
import json, os, sys

path, interpreter, display = sys.argv[1:4]
spec = {
    "argv": [
        interpreter,
        "-c",
        "import runpy; runpy.run_path('/usr/lib/python3.12/site-packages/jupydroid_kernel.py', run_name='__main__')",
        "-f",
        "{connection_file}",
        "--log-level=DEBUG",
    ],
    "display_name": display,
    "language": "python",
    "env": {
        "PYTHONFAULTHANDLER": "1",
        "LD_PRELOAD": "",
        "PYTHONUSERBASE": "/pkgs-gnu",
        "PROOT_SIGSYS_ENOSYS": "1",
        "PATH": "/pkgs-gnu/bin:/pkgs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    },
    "metadata": {"debugger": True},
}
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "w") as f:
    json.dump(spec, f)
PY

# The script's own verdict: pip's exit code cannot be trusted through proot - a tracee killed by
# SIGSYS still reports 0 (KernelModelProbeDeviceTest) - so ipykernel is checked by import under
# the kernel's env, and the spec by jupyter's own catalogue, the search path the server reads.
env LD_PRELOAD= PROOT_SIGSYS_ENOSYS=1 "$env_dir/bin/python" -c "import ipykernel"
jupyter kernelspec list | awk '{print $1}' | grep -qxF "$name"
echo "== created $env_path; kernel '$name' registered"
