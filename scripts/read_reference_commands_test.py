#!/usr/bin/env python3
"""Tests for read_reference_commands.py."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from read_reference_commands import (
    REFERENCE_ROOT,
    clone_head,
    jupyterlab_command_ids,
    notebook_action_names,
    pinned_clones,
    reference_dir,
    rootfs_kernel_message_types,
    vscode_jupyter_command_ids,
)

REPO = Path(__file__).resolve().parent.parent


def _tree(files: dict[str, str]) -> Path:
    """A throwaway directory holding [files], keyed by path relative to its root."""
    root = Path(tempfile.mkdtemp())
    for name, body in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class JupyterLabCommandIdsTest(unittest.TestCase):
    """The ids are grouped by package because that is the unit a slice of the audit takes."""

    def test_an_id_is_found_and_keyed_by_its_package(self) -> None:
        packages = _tree({"notebook-extension/src/index.ts": """
export namespace CommandIDs {
  export const runAll = 'notebook:run-all-cells';
}
"""})
        self.assertEqual({"notebook-extension": ["notebook:run-all-cells"]},
                         jupyterlab_command_ids(packages))

    def test_a_constant_whose_value_has_no_namespace_colon_is_not_an_id(self) -> None:
        """`export const FACTORY = 'Notebook'` is a widget factory name, not a command."""
        packages = _tree({"notebook-extension/src/index.ts": """
  export const FACTORY = 'Notebook';
  export const runAll = 'notebook:run-all-cells';
"""})
        self.assertEqual({"notebook-extension": ["notebook:run-all-cells"]},
                         jupyterlab_command_ids(packages))

    def test_the_same_id_declared_in_two_files_of_one_package_counts_once(self) -> None:
        packages = _tree({
            "notebook-extension/src/index.ts": "  export const a = 'notebook:x';\n",
            "notebook-extension/src/other.tsx": "  export const b = 'notebook:x';\n",
        })
        self.assertEqual({"notebook-extension": ["notebook:x"]}, jupyterlab_command_ids(packages))

    def test_a_package_declaring_none_is_absent_rather_than_empty(self) -> None:
        packages = _tree({"ui-components/src/index.ts": "export const x = 1;\n"})
        self.assertEqual({}, jupyterlab_command_ids(packages))

    def test_built_output_and_dependencies_are_not_read(self) -> None:
        """`lib/` is compiled `src/`, so reading it would count every id twice."""
        packages = _tree({
            "notebook-extension/lib/index.js": "  export const a = 'notebook:built';\n",
            "notebook-extension/node_modules/dep/src/i.ts": "  export const b = 'dep:x';\n",
            "notebook-extension/src/index.ts": "  export const c = 'notebook:real';\n",
        })
        self.assertEqual({"notebook-extension": ["notebook:real"]}, jupyterlab_command_ids(packages))


class NotebookActionNamesTest(unittest.TestCase):
    """ADR-0027 makes `NotebookActions` the behaviour specification, so its members are the list."""

    ACTIONS = """
export class NotebookActions {
  static get executed(): ISignal<any, any> { return Private.executed; }
}

export namespace NotebookActions {
  export function splitCell(notebook: Notebook): void {}

  export async function runCells(notebook: Notebook): Promise<boolean> { return true; }
}

export function setCellExecutor(executor: INotebookCellExecutor): void {}

namespace Private {
  export function handleState(notebook: Notebook): void {}
}
"""

    def names(self, source: str) -> list[str]:
        return notebook_action_names(_tree({"actions.tsx": source}) / "actions.tsx")

    def test_the_namespaces_functions_are_found(self) -> None:
        self.assertIn("splitCell", self.names(self.ACTIONS))

    def test_an_async_function_is_found(self) -> None:
        self.assertIn("runCells", self.names(self.ACTIONS))

    def test_the_private_namespace_after_it_is_not_read(self) -> None:
        """`Private.handleState` is an implementation detail; counting it overstates the surface."""
        self.assertNotIn("handleState", self.names(self.ACTIONS))

    def test_a_function_outside_any_namespace_is_not_read(self) -> None:
        self.assertNotIn("setCellExecutor", self.names(self.ACTIONS))

    def test_the_class_body_above_it_contributes_no_action(self) -> None:
        """The class holds four signal getters; the actions are all in the namespace."""
        self.assertEqual(["runCells", "splitCell"], self.names(self.ACTIONS))

    def test_a_file_without_the_namespace_is_an_error_naming_what_was_wanted(self) -> None:
        with self.assertRaises(ValueError) as raised:
            self.names("export function splitCell(): void {}\n")
        self.assertIn("export namespace NotebookActions", str(raised.exception))


class VsCodeJupyterCommandIdsTest(unittest.TestCase):
    def ids(self, contributes: object) -> list[str]:
        root = _tree({"package.json": json.dumps({"contributes": contributes})})
        return vscode_jupyter_command_ids(root / "package.json")

    def test_every_contributed_command_is_listed(self) -> None:
        self.assertEqual(["jupyter.restartkernel", "jupyter.runallcells"],
                         self.ids({"commands": [{"command": "jupyter.runallcells"},
                                                {"command": "jupyter.restartkernel"}]}))

    def test_a_manifest_contributing_no_command_is_an_error_naming_the_key(self) -> None:
        with self.assertRaises(ValueError) as raised:
            self.ids({"keybindings": []})
        self.assertIn("contributes.commands", str(raised.exception))


class RootfsKernelMessageTypesTest(unittest.TestCase):
    """The shipped kernel's own list, which is what `cross-cutting.md` §8.3 makes authoritative."""

    KERNELBASE = """
    msg_types = [
        "execute_request",
        "complete_request",
    ]

    # control channel accepts all shell messages
    # and some of its own
    control_msg_types = [
        *msg_types,
        "debug_request",
    ]
"""

    def types(self, source: str) -> dict[str, list[str]]:
        return rootfs_kernel_message_types(_tree({"kernelbase.py": source}) / "kernelbase.py")

    def test_the_shell_types_are_read_in_declaration_order(self) -> None:
        self.assertEqual(["execute_request", "complete_request"], self.types(self.KERNELBASE)["shell"])

    def test_the_control_list_reports_only_what_is_control_only(self) -> None:
        """`*msg_types` is a splat, not a string, so what is left is the control channel's own."""
        self.assertEqual(["debug_request"], self.types(self.KERNELBASE)["control_only"])

    def test_a_file_without_the_list_is_an_error_naming_what_was_wanted(self) -> None:
        with self.assertRaises(ValueError) as raised:
            self.types("class Kernel:\n    pass\n")
        self.assertIn("msg_types = [", str(raised.exception))


class PinnedClonesTest(unittest.TestCase):
    """The SHAs have one home - `docs/README.md` - and this reads it rather than storing them again."""

    def test_a_row_yields_its_sha_and_clone_command(self) -> None:
        readme = _tree({"README.md": (
            "| Project | Pinned SHA | Clone |\n"
            "|---|---|---|\n"
            "| `jupyterlab` | `7f0e690680a763d3c3db5dc0231803b0ba0e1e12` | "
            "`git clone --depth 1 https://github.com/jupyterlab/jupyterlab` |\n"
        )}) / "README.md"
        self.assertEqual(
            {"jupyterlab": ("7f0e690680a763d3c3db5dc0231803b0ba0e1e12",
                            "git clone --depth 1 https://github.com/jupyterlab/jupyterlab")},
            pinned_clones(readme))

    def test_the_repositorys_own_readme_pins_every_project_this_tool_reads(self) -> None:
        clones = pinned_clones(REPO / "docs/README.md")
        for project in ("jupyterlab", "vscode-jupyter"):
            with self.subTest(project=project):
                self.assertIn(project, clones)
                self.assertEqual(40, len(clones[project][0]))


class CloneHeadTest(unittest.TestCase):
    """A count is only meaningful with the revision it was counted at, so the report states it."""

    def test_a_directory_that_is_not_a_clone_reads_as_unknown(self) -> None:
        self.assertIsNone(clone_head(Path(tempfile.mkdtemp())))

    def test_this_repository_reads_its_own_head(self) -> None:
        self.assertEqual(40, len(clone_head(REPO) or ""))


class ReferenceDirTest(unittest.TestCase):
    def test_an_absent_clone_names_the_path_and_the_command_that_makes_it(self) -> None:
        """`reference/` is gitignored, so this is the normal state of a fresh clone."""
        with self.assertRaises(FileNotFoundError) as raised:
            reference_dir("jupyterlab", root=Path(tempfile.mkdtemp()))
        message = str(raised.exception)
        self.assertIn("jupyterlab", message)
        self.assertIn("git clone --depth 1 https://github.com/jupyterlab/jupyterlab", message)

    def test_an_unknown_project_names_the_ones_that_are_pinned(self) -> None:
        with self.assertRaises(KeyError) as raised:
            reference_dir("sora-editor", root=Path(tempfile.mkdtemp()))
        self.assertIn("jupyterlab", str(raised.exception))

    def test_the_default_root_is_the_gitignored_reference_tree(self) -> None:
        self.assertEqual(REPO / "reference/research_repos", REFERENCE_ROOT)


class PinnedSurfaceTest(unittest.TestCase):
    """The counts `docs/architecture.md` §1 calls the specification, asserted against the clone.

    §1 states them as prose - "99 command ids, 96 of them `notebook:*`; `filebrowser-extension` 36,
    `console-extension` 21, `terminal-extension` 11, `documentsearch-extension` 5, `toc-extension`
    5". Every one is confirmed here at the SHA `docs/README.md` pins, so the prose is checked rather
    than trusted: bumping a pin fails this test instead of leaving the specification silently wrong.

    Skipped where `reference/` is absent, which is every fresh clone.
    """

    @classmethod
    def setUpClass(cls) -> None:
        try:
            cls.clone = reference_dir("jupyterlab")
            cls.vscode_jupyter = reference_dir("vscode-jupyter")
        except FileNotFoundError as absent:
            raise unittest.SkipTest(str(absent)) from absent
        cls.by_package = jupyterlab_command_ids(cls.clone / "packages")

    def test_each_clone_is_at_the_revision_the_readme_pins(self) -> None:
        pinned = pinned_clones(REPO / "docs/README.md")
        for project, clone in (("jupyterlab", self.clone), ("vscode-jupyter", self.vscode_jupyter)):
            with self.subTest(project=project):
                self.assertEqual(pinned[project][0], clone_head(clone))

    def test_the_five_package_counts_section_1_names(self) -> None:
        for package, expected in (("filebrowser-extension", 36), ("console-extension", 21),
                                  ("terminal-extension", 11), ("documentsearch-extension", 5),
                                  ("toc-extension", 5)):
            with self.subTest(package=package):
                self.assertEqual(expected, len(self.by_package[package]))

    def test_the_notebook_extension_declares_99_ids_of_which_96_are_notebook(self) -> None:
        ids = self.by_package["notebook-extension"]
        self.assertEqual(99, len(ids))
        self.assertEqual(96, sum(1 for found in ids if found.startswith("notebook:")))

    def test_the_three_ids_the_notebook_extension_declares_for_other_namespaces(self) -> None:
        """The remainder of the 99, named so that a change in it is read rather than counted."""
        ids = self.by_package["notebook-extension"]
        self.assertEqual(["completer:invoke-notebook", "completer:select-notebook", "toc:run-cells"],
                         [found for found in ids if not found.startswith("notebook:")])

    def test_the_whole_command_surface_is_403_ids_across_33_packages(self) -> None:
        unique = {found for ids in self.by_package.values() for found in ids}
        self.assertEqual((403, 33), (len(unique), len(self.by_package)))

    def test_notebook_actions_declares_69_functions(self) -> None:
        """ADR-0027's specification, bounded to the namespace - the file holds 83 to its end."""
        actions = notebook_action_names(self.clone / "packages/notebook/src/actions.tsx")
        self.assertEqual(69, len(actions))
        self.assertIn("splitCell", actions)
        self.assertNotIn("handleState", actions)

    def test_vscode_jupyter_contributes_83_commands(self) -> None:
        self.assertEqual(83, len(vscode_jupyter_command_ids(self.vscode_jupyter / "package.json")))


if __name__ == "__main__":
    unittest.main()
