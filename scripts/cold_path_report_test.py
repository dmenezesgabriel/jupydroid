#!/usr/bin/env python3
"""Pins `cold_path_report.py`'s parsing on the real logcat shapes it will read."""

from __future__ import annotations

import unittest

import cold_path_report as report

# The real shapes, copied from a `device-logcat.txt` capture: maestro records the brief format
# `MM-DD HH:MM:SS.mmm L/Tag( pid): message` - and `Displayed` gains `for user 0` on this build.
LOGCAT = """\
09-27 13:31:32.566 I/ActivityManager( 3540): Start proc 3796:com.jupydroid.app/u0a5424 for next-top-activity {com.jupydroid.app/com.jupydroid.app.MainActivity}
09-27 13:31:34.214 I/ActivityTaskManager( 3540): Displayed com.jupydroid.app/.MainActivity for user 0: +1s777ms
09-27 13:31:46.247 I/JupydroidSpace( 3796): {"stage":"installing base Space"}
09-27 13:32:03.579 I/JupydroidSpace( 3796): {"stage":"starting jupyter_server"}
09-27 13:32:10.622 I/JupydroidSpace( 3796): {"stage":"jupyter_server listening on http://127.0.0.1:41333/"}
09-27 13:32:13.265 I/JupydroidSpace( 3796): {"stage":"kernel.session.opened","kernelId":"k1","sessionId":"s1"}
09-27 13:32:13.265 I/JupydroidSpace( 3796): {"stage":"kernel k1 ready for space base"}
09-27 13:32:13.268 I/JupydroidSpace( 3796): {"stage":"kernel.socket.opened","kernelId":"k1","sessionId":"s1"}
09-27 13:32:13.393 I/JupydroidSpace( 3796): {"stage":"lsp.start","space":"base","rootfs":"7"}
09-27 13:32:15.958 I/JupydroidSpace( 3796): {"stage":"kernel.socket.connected","subscribers":"1"}
09-27 13:32:15.965 I/JupydroidSpace( 3796): {"stage":"kernel.greet.sent","sessionId":"s1"}
09-27 13:32:17.710 I/JupydroidSpace( 3796): {"stage":"kernel.greet.answered","sessionId":"s1"}
09-27 13:32:18.000 I/JupydroidSpace( 3796): {"stage":"kernel.execute.request","cellId":"c1","msgId":"m1"}
09-27 13:32:18.150 I/JupydroidSpace( 3796): {"stage":"kernel.frame.received","subscribers":"2","msgType":"stream","bytes":"4"}
09-27 13:32:18.300 I/JupydroidSpace( 3796): {"stage":"kernel.execute.complete","cellId":"c1","msgId":"m1"}
09-27 13:33:00.100 I/JupydroidSpace( 3796): {"stage":"terminal.opened","terminal":"1","cwd":"<server root>"}
09-27 13:33:00.600 I/JupydroidSpace( 3796): {"stage":"terminal.socket.opened","terminal":"1"}
09-27 13:33:01.900 I/JupydroidSpace( 3796): {"stage":"terminal.ready","terminal":"1"}
"""


class ParseLogcatTest(unittest.TestCase):
    def test_marks_carry_threadtime_milliseconds_of_day(self) -> None:
        marks = report.marks_of(LOGCAT)
        self.assertEqual(marks["space.installing"], 48_706_247)  # 13:31:46.247
        self.assertEqual(marks["server.listening"], 48_730_622)

    def test_process_start_is_the_earliest_known_mark(self) -> None:
        marks = report.marks_of(LOGCAT)
        self.assertEqual(marks["process.started"], 48_692_566)  # 13:31:32.566

    def test_the_free_text_stages_normalize_to_dotted_names(self) -> None:
        marks = report.marks_of(LOGCAT)
        self.assertEqual(marks["kernel.process.ready"], 48_733_265)

    def test_activity_displayed_reports_the_systems_own_duration(self) -> None:
        self.assertEqual(report.displayed_ms_of(LOGCAT), 1_777)

    def test_only_the_first_occurrence_of_a_stage_counts(self) -> None:
        later_frame = (
            "09-27 13:32:19.999 I/JupydroidSpace( 3796): "
            '{"stage":"kernel.frame.received","subscribers":"2","msgType":"stream","bytes":"4"}\n'
        )
        marks = report.marks_of(LOGCAT + later_frame)
        self.assertEqual(marks["kernel.frame.received"], 48_738_150)

    def test_a_frame_before_the_execute_request_is_not_the_answer(self) -> None:
        # Greet/status chatter arrives before the cell is asked; the mark must follow the ask.
        early = (
            "09-27 13:32:15.990 I/JupydroidSpace( 3796): "
            '{"stage":"kernel.frame.received","subscribers":"2","msgType":"status","bytes":"600"}\n'
        )
        lines = LOGCAT.splitlines(True)
        marks = report.marks_of("".join(lines[:11]) + early + "".join(lines[11:]))
        self.assertEqual(marks["kernel.frame.received"], 48_738_150)

    def test_the_space_named_server_stages_normalize_to_dotted_names(self) -> None:
        # The shape SpaceServer logs once a Space has an id of its own - captured on SM-X216B.
        capture = (
            '10-08 16:01:37.383 I/JupydroidSpace(16266): {"stage":"installing Space base"}\n'
            "10-08 16:01:37.436 I/JupydroidSpace(16266): "
            '{"stage":"starting jupyter_server for Space base"}\n'
        )
        marks = report.marks_of(capture)
        self.assertEqual(marks["space.installing"], 57_697_383)  # 16:01:37.383
        self.assertEqual(marks["server.starting"], 57_697_436)

    def test_missing_stages_stay_absent(self) -> None:
        marks = report.marks_of('09-27 13:31:32.566 I/JupydroidSpace( 1): {"stage":"x"}\n')
        self.assertNotIn("terminal.ready", marks)

    def test_rows_pair_each_stage_with_its_delta_since_the_previous(self) -> None:
        rows = report.rows_of(LOGCAT)
        self.assertEqual(rows[0], ("process.started", 0, None))
        self.assertEqual(rows[1], ("space.installing", 13_681, 13_681))
        self.assertEqual(rows[2], ("server.starting", 31_013, 17_332))
        self.assertEqual(rows[-1], ("terminal.ready", 89_334, 1_300))

    def test_lsp_start_orders_between_socket_opened_and_connected(self) -> None:
        stages = [stage for stage, _, _ in report.rows_of(LOGCAT)]
        self.assertLess(stages.index("kernel.socket.opened"), stages.index("lsp.start"))
        self.assertLess(stages.index("lsp.start"), stages.index("kernel.socket.connected"))

    def test_the_session_request_is_the_row_kernel_spawn_is_timed_from(self) -> None:
        requested = (
            "09-27 13:32:12.000 I/JupydroidSpace( 3796): "
            '{"stage":"kernel.session.requested","path":"Cold Path.ipynb"}\n'
        )
        lines = LOGCAT.splitlines(True)
        rows = report.rows_of("".join(lines[:5]) + requested + "".join(lines[5:]))
        stages = [stage for stage, _, _ in rows]
        self.assertEqual(stages.index("server.listening") + 1, stages.index("kernel.session.requested"))
        self.assertEqual(rows[stages.index("kernel.session.opened")][2], 1_265)

    def test_displayed_is_reported_beside_the_rows_not_in_them(self) -> None:
        rows = report.rows_of(LOGCAT)
        self.assertNotIn("activity.displayed", [stage for stage, _, _ in rows])


if __name__ == "__main__":
    unittest.main()
