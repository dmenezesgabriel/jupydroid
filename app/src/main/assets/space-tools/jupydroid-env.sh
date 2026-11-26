#!/bin/sh
# jupydroid-env - a Space's environments and kernels, made and registered.
#
#   jupydroid-env create <project>
#   jupydroid-env register <env-or-interpreter>
#
# create: <project> is a top-level folder of the workspace - the folders DiskSpaceEnvironments
# scans (app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceEnvironments.kt). The environment
# made is <project>/.venv, vscode-python's own create convention, by the gnu payload's interpreter -
# the only one whose venv can carry the manylinux wheels a kernel exists for (ADR-0166); a musl
# env stays the terminal's `python3 -m venv` plus register.
#
# register: an existing interpreter - one of the Space's pythons, a hand-made venv's bin/python -
# or an environment directory becomes a kernelspec every notebook's picker offers, with no env
# created: ADR-0078's registration is the person's act, never an implicit discovery. A path that
# is an environment's own interpreter - `…/bin/python` beside a pyvenv.cfg - registers the
# environment, so both spellings land on one kernel name.
#
# The kernelspec written is the one SpaceKernelSpec.installEnvironment writes for the same
# interpreter (app/src/main/java/com/jupydroid/app/data/space/SpaceKernelSpec.kt) - one shape, two
# writers, pinned field-for-field by KernelRegisterDeviceTest and EnvironmentCreateDeviceTest.
#
# Installed at /usr/local/bin by JupydroidEnvHelper.install
# (app/src/main/java/com/jupydroid/app/data/space/JupydroidEnvHelper.kt): the Space's terminal
# runs it by name and SpaceShell runs these same bytes for the app.
set -eu

GNU_PYTHON=/opt/gnu/python/bin/python3.12
KERNELS_DIR=/usr/local/share/jupyter/kernels

usage() {
  echo "usage: jupydroid-env create <project>" >&2
  echo "       jupydroid-env register <env-or-interpreter>" >&2
  exit 2
}

refuse() {
  echo "jupydroid-env: $1" >&2
  exit "${2:-1}"
}

# kernel_env_run <gnu|musl> <cmd...>: under the kernel env that decides whether a glibc process
# survives proot - no musl preload may enter it, and the patched tracer answers its seccomp traps
# with ENOSYS rather than a SIGSYS glibc's threads cannot survive (ADR-0166) - while a musl
# process inherits the Space's own environment.
kernel_env_run() {
  libc=$1
  shift
  if [ "$libc" = gnu ]; then
    env LD_PRELOAD= PROOT_SIGSYS_ENOSYS=1 "$@"
  else
    "$@"
  fi
}

# The spec's name is PythonEnvironment.kernelName's rule
# (app/src/main/java/com/jupydroid/app/domain/model/PythonEnvironment.kt): lowercased, every
# [^a-z0-9._-] folded to '-'. python3 does the fold, so a path named with a quote or a backslash
# cannot break the command.
kernel_name() {
  python3 -c 'import re, sys; print(re.sub(r"[^a-z0-9._-]", "-", sys.argv[1].lower()))' "$1"
}

# specPath's one leading envs-directory strip - ENVIRONMENT_CONTAINER_DIRS in PythonEnvironment.kt,
# the same list the scan finds its roots by: a container's children are environments, so
# `envs/stats` is `stats` while `proj-a/envs/stats` keeps every later segment.
env_spec_path() {
  rel=$1
  for container in envs Envs .direnv .venvs .virtualenvs .local/share/virtualenvs; do
    case "$rel" in
      "$container/"*) rel=${rel#"$container"/}; break ;;
    esac
  done
  printf %s "$rel"
}

# One spec shape, two writers - this script and SpaceKernelSpec.installEnvironment
# (app/src/main/java/com/jupydroid/app/data/space/SpaceKernelSpec.kt): argv[0] the interpreter, the
# image's jupydroid_kernel run by path because a venv's sys.path lacks the system site-packages
# (`tools/rootfs/jupydroid_kernel.py`), and the libc's half of the env split (ADR-0166).
write_spec() {
  python3 - "$KERNELS_DIR/$1/kernel.json" "$2" "$3" "$4" <<'PY'
import json, os, sys

path, interpreter, display, libc = sys.argv[1:5]
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
    }
    if libc == "gnu"
    else {"PYTHONFAULTHANDLER": "1"},
    "metadata": {"debugger": True},
}
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "w") as f:
    json.dump(spec, f)
PY
}

# NAME/INTERPRETER/LIBC/SPEC_PATH for an environment inside the workspace: pyvenv.cfg is the env's
# own signal - what the scan finds and what a venv always writes - and its `home` is the directory
# of the interpreter that made it, the libc question `isGnuVenv` answers the same way
# (app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceEnvironments.kt).
resolve_environment() {
  env_dir=$1
  [ -f "$env_dir/pyvenv.cfg" ] ||
    refuse "'$env_dir' is no Python environment; expected a directory holding pyvenv.cfg"
  SPEC_PATH=$(env_spec_path "${env_dir#"$HOME"/}")
  NAME=$(kernel_name "$SPEC_PATH")
  INTERPRETER=$env_dir/bin/python
  [ -x "$INTERPRETER" ] ||
    refuse "'$INTERPRETER' is no interpreter a kernel can start; expected an executable"
  home=$(sed -n 's/^home *= *\([^ \t]*\).*/\1/p' "$env_dir/pyvenv.cfg" | head -1)
  case "$home" in
    /opt/gnu/*) LIBC=gnu ;;
    *) LIBC=musl ;;
  esac
}

# The name must be one no kernel of the Space has - jupyter's own catalogue is the oracle, the
# same list `registrableEnvironment` refuses by.
refuse_if_held() {
  held=$(jupyter kernelspec list 2>/dev/null | awk -v n="$1" '$1 == n { print $2; exit }')
  [ -z "$held" ] ||
    refuse "a kernel named '$1' is already held by '$held'; expected a name no kernel here has"
}

# `import ipykernel` under the kernel's own env, not a file probe: the script runs inside the
# Space, where "a kernel can start in it" is whether the interpreter imports it - an env whose
# include-system-site-packages reaches the base's ipykernel is invisible to a file scan.
has_ipykernel() {
  kernel_env_run "$1" "$2" -c "import ipykernel" >/dev/null 2>&1
}

# The spec the server offers is jupyter's own search path's verdict: pip's exit code cannot be
# trusted through proot - a tracee killed by SIGSYS still reports 0 (KernelModelProbeDeviceTest).
require_listed() {
  jupyter kernelspec list | awk '{print $1}' | grep -qxF "$1" ||
    refuse "kernel '$1' does not appear in 'jupyter kernelspec list' though its spec was written"
}

# jupydroid-env create <project>: make <project>/.venv with the gnu interpreter, pip ipykernel
# into it, and write its kernelspec - registration folded into creation because creation's purpose
# is a kernel.
create() {
  project=$1
  # A project is one top-level folder: a single segment, not hidden, not itself an env container -
  # DiskSpaceEnvironments' scan rule, so what it offers is what this accepts.
  case "$project" in
    "" | . | .. | */* | .* | envs | Envs)
      refuse "'$project' is no project folder; expected a top-level directory name" 2
      ;;
  esac
  [ -d "$HOME/$project" ] || refuse "no project folder '$project' in $HOME"

  env_path="$project/.venv"
  env_dir="$HOME/$env_path"
  [ ! -e "$env_dir" ] || refuse "'$env_dir' already exists"

  echo "== $GNU_PYTHON -m venv $env_dir"
  kernel_env_run gnu "$GNU_PYTHON" -m venv "$env_dir"

  echo "== $env_dir/bin/python -m pip install ipykernel"
  kernel_env_run gnu "$env_dir/bin/python" -m pip install --no-input ipykernel

  resolve_environment "$env_dir"
  write_spec "$NAME" "$INTERPRETER" "Python ($SPEC_PATH)" "$LIBC"
  # Create's own verdict, after the write: the kernel's interpreter must import ipykernel, and
  # jupyter's catalogue must list the spec.
  kernel_env_run "$LIBC" "$INTERPRETER" -c "import ipykernel"
  require_listed "$NAME"
  echo "== created $env_path; kernel '$NAME' registered"
}

# jupydroid-env register <env-or-interpreter>: an env directory holding pyvenv.cfg, or an
# interpreter - a bare command name resolves through PATH. `…/bin/python` inside an environment is
# the environment: one kernel name per env however the path is spelled.
register() {
  arg=$1
  if [ ! -e "$arg" ]; then
    case "$arg" in
      */*) refuse "'$arg' is no environment directory and no interpreter; expected a path that exists" ;;
      *)
        arg=$(command -v "$arg" || true)
        [ -n "$arg" ] ||
          refuse "'$1' is no path and no command on PATH; expected an env directory or an interpreter"
        ;;
    esac
  fi
  case "$arg" in
    /*) ;;
    *) arg=$PWD/$arg ;;
  esac

  env_dir=
  if [ -d "$arg" ]; then
    env_dir=$arg
  elif [ -f "$arg" ]; then
    env_root=$(dirname "$(dirname "$arg")")
    if [ -f "$env_root/pyvenv.cfg" ]; then
      env_dir=$env_root
    fi
  fi

  if [ -n "$env_dir" ]; then
    case "$env_dir" in
      "$HOME/"*) ;;
      *) refuse "'$env_dir' is outside the workspace; expected an environment inside $HOME" ;;
    esac
    resolve_environment "$env_dir"
    refuse_if_held "$NAME"
    has_ipykernel "$LIBC" "$INTERPRETER" ||
      refuse "'${env_dir#"$HOME"/}' has no ipykernel, so no kernel can start in it; add it with: ~/${env_dir#"$HOME"/}/bin/pip install ipykernel"
    write_spec "$NAME" "$INTERPRETER" "Python ($SPEC_PATH)" "$LIBC"
  else
    INTERPRETER=$arg
    [ -x "$INTERPRETER" ] ||
      refuse "'$INTERPRETER' is no interpreter a kernel can start; expected an executable"
    case "$INTERPRETER" in
      /opt/gnu/*) LIBC=gnu ;;
      *) LIBC=musl ;;
    esac
    NAME=$(kernel_name "${INTERPRETER#/}")
    refuse_if_held "$NAME"
    has_ipykernel "$LIBC" "$INTERPRETER" ||
      refuse "'$INTERPRETER' has no ipykernel, so no kernel can start in it; add it with: $INTERPRETER -m pip install ipykernel"
    write_spec "$NAME" "$INTERPRETER" "Python ($INTERPRETER)" "$LIBC"
  fi

  require_listed "$NAME"
  echo "== registered ${env_dir:-$INTERPRETER}; kernel '$NAME'"
}

case "${1:-}" in
  create)
    [ "$#" -eq 2 ] || usage
    create "$2"
    ;;
  register)
    [ "$#" -eq 2 ] || usage
    register "$2"
    ;;
  *) usage ;;
esac
