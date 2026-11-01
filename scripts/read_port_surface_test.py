#!/usr/bin/env python3
"""Tests for read_port_surface.py."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from read_port_surface import (
    FAKE_DIR,
    PORT_DIR,
    Operation,
    named_in,
    operations_of,
    ports_under,
    unused_operations,
)

REPO = Path(__file__).resolve().parent.parent


def _tree(files: dict[str, str]) -> Path:
    root = Path(tempfile.mkdtemp())
    for name, body in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class OperationsOfTest(unittest.TestCase):
    PORT = """
package com.jupydroid.app.domain.port

/** Reads one notebook. */
interface NotebookRepository {
  /** fun notACommentOperation(): Unit */
  suspend fun readNotebook(id: String): Notebook

  suspend fun saveNotebook(notebook: Notebook)

  val changes: Flow<WorkspaceChange>

  companion object {
    const val MAX = 10
  }
}
"""

    def names(self, source: str) -> list[str]:
        return [operation.name for operation in operations_of(source)]

    def test_a_suspend_function_is_an_operation(self) -> None:
        self.assertIn("readNotebook", self.names(self.PORT))

    def test_a_flow_property_is_an_operation(self) -> None:
        """`WorkspaceRepository.changes` and `LanguageServerPort.diagnostics` are surface too."""
        self.assertIn("changes", self.names(self.PORT))

    def test_a_function_named_only_in_a_comment_is_not_an_operation(self) -> None:
        """The defect `kotlin_source.without_comments` exists to prevent, from the other side."""
        self.assertNotIn("notACommentOperation", self.names(self.PORT))

    def test_a_companion_constant_is_not_an_operation(self) -> None:
        self.assertNotIn("MAX", self.names(self.PORT))

    def test_the_operations_carry_the_port_they_belong_to(self) -> None:
        found = operations_of(self.PORT)
        self.assertEqual({"NotebookRepository"}, {operation.port for operation in found})

    def test_a_fun_interface_declares_operations_too(self) -> None:
        self.assertEqual(["open"], self.names("fun interface SocketOpener {\n  fun open(): Unit\n}\n"))

    def test_a_file_declaring_no_interface_has_no_operations(self) -> None:
        """An exception file like `FileNameTakenException.kt` sits in `domain/port/` and is not one."""
        self.assertEqual([], self.names("class FileNameTakenException : Exception()\n"))


class PortsUnderTest(unittest.TestCase):
    def test_each_interface_file_becomes_one_port(self) -> None:
        root = _tree(
            {
                "KernelGateway.kt": "interface KernelGateway {\n  suspend fun connect()\n}\n",
                "FileNameTakenException.kt": "class FileNameTakenException : Exception()\n",
            }
        )
        self.assertEqual(
            {"KernelGateway": ["connect"]}, {port: [op.name for op in ops] for port, ops in ports_under(root).items()}
        )


class NamedInTest(unittest.TestCase):
    """The measurement is deliberately *mentions*, not calls - see the module docstring."""

    def test_a_call_is_a_mention(self) -> None:
        tree = _tree({"ui/Screen.kt": "val n = repository.readNotebook(id)\n"})
        self.assertEqual([tree / "ui/Screen.kt"], named_in("readNotebook", tree))

    def test_a_mention_in_a_comment_is_not_one(self) -> None:
        tree = _tree({"ui/Screen.kt": "// readNotebook is what this would call\n"})
        self.assertEqual([], named_in("readNotebook", tree))

    def test_a_longer_name_that_contains_it_is_not_one(self) -> None:
        """`readNotebook` must not be found inside `readNotebookOutputs`."""
        tree = _tree({"ui/Screen.kt": "val n = repository.readNotebookOutputs(id)\n"})
        self.assertEqual([], named_in("readNotebook", tree))


class OverrideIsNotUseTest(unittest.TestCase):
    """An adapter implements an operation; implementing is not using.

    Every port has a real adapter as well as a `Fake…`, and each overrides every operation. Counting
    an override as use made the first run of this tool report zero unused operations across all 19
    ports - the measurement could not fail.
    """

    def uses(self, body: str) -> list[Path]:
        return named_in("openDocument", _tree({"data/space/Stdio.kt": body}))

    def test_an_override_declaring_it_is_not_a_use(self) -> None:
        self.assertEqual([], self.uses("  override suspend fun openDocument(path: String) = Unit\n"))

    def test_an_overridden_property_is_not_a_use(self) -> None:
        self.assertEqual([], self.uses("  override val openDocument: Flow<Unit> = empty()\n"))

    def test_an_abstract_redeclaration_is_not_a_use(self) -> None:
        self.assertEqual([], self.uses("  abstract suspend fun openDocument(path: String)\n"))

    def test_an_override_that_delegates_on_the_same_line_is_a_use(self) -> None:
        """`BaseSpaceKernelGateway` is written this way, so a line check would lose the real call."""
        tree = _tree({"data/space/Base.kt": "  override suspend fun openDocument(p: String) = inner.openDocument(p)\n"})
        self.assertEqual([tree / "data/space/Base.kt"], named_in("openDocument", tree))

    def test_a_plain_call_is_still_a_use(self) -> None:
        self.assertEqual(1, len(self.uses("  server.openDocument(path)\n")))

    def test_an_unrelated_declaration_of_the_same_name_is_not_a_use(self) -> None:
        """`StdioLanguageServer` declares `val capabilities: LanguageCapabilities` of its own, which
        is a different thing from the port operation `capabilities(space)` and masked it."""
        self.assertEqual([], self.uses("  val openDocument: Flow<Unit>,\n"))

    def test_a_call_on_a_receiver_of_the_same_name_is_still_a_use(self) -> None:
        self.assertEqual(1, len(self.uses("  val caps = pool.serverFor(space).openDocument(p)\n")))


class UnusedOperationsTest(unittest.TestCase):
    def test_an_operation_nothing_names_is_reported(self) -> None:
        ports = _tree({"NotebookRepository.kt": "interface NotebookRepository {\n  suspend fun deleteNotebook()\n}\n"})
        production = _tree({"ui/Screen.kt": "val x = 1\n"})
        self.assertEqual([Operation("NotebookRepository", "deleteNotebook", 2)], unused_operations(ports, production))

    def test_an_operation_production_code_names_is_not_reported(self) -> None:
        ports = _tree({"NotebookRepository.kt": "interface NotebookRepository {\n  suspend fun readNotebook()\n}\n"})
        production = _tree({"ui/Screen.kt": "repository.readNotebook()\n"})
        self.assertEqual([], unused_operations(ports, production))

    def test_the_fake_that_implements_it_is_not_production_use(self) -> None:
        """Every port has a `Fake…` by rule, so a fake's override would make nothing ever unused."""
        ports = _tree({"NotebookRepository.kt": "interface NotebookRepository {\n  suspend fun deleteNotebook()\n}\n"})
        production = _tree({"data/fake/FakeNotebookRepository.kt": "override suspend fun deleteNotebook() = Unit\n"})
        self.assertEqual(["deleteNotebook"], [op.name for op in unused_operations(ports, production)])

    def test_the_ports_own_declaration_is_not_production_use(self) -> None:
        ports = _tree({"NotebookRepository.kt": "interface NotebookRepository {\n  suspend fun deleteNotebook()\n}\n"})
        self.assertEqual(["deleteNotebook"], [op.name for op in unused_operations(ports, ports)])


class ThisRepositorysPortsTest(unittest.TestCase):
    """Read off the tree, so what can go stale is the tree, not a number stored here."""

    def setUp(self) -> None:
        self.ports = ports_under(REPO / PORT_DIR)

    def test_every_port_has_the_fake_the_agreement_requires(self) -> None:
        """`building-blocks.md` §5.3 checks this with a shell one-liner; this is the same claim."""
        fakes = {path.stem.removeprefix("Fake") for path in (REPO / FAKE_DIR).glob("Fake*.kt")}
        self.assertEqual(set(), set(self.ports) ^ fakes)

    def test_the_ports_named_in_building_blocks_are_all_found(self) -> None:
        for port in (
            "NotebookRepository",
            "KernelGateway",
            "KernelDebugger",
            "ServerLauncher",
            "WorkspaceSearcher",
            "SpaceRepository",
            "LanguageServerPort",
        ):
            with self.subTest(port=port):
                self.assertIn(port, self.ports)

    def test_every_port_declares_at_least_one_operation(self) -> None:
        for port, operations in self.ports.items():
            with self.subTest(port=port):
                self.assertNotEqual([], operations)


if __name__ == "__main__":
    unittest.main()
