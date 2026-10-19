"""Preflight rejects focus blockers even when dumpsys exceeds the pipe buffer."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    'window_gate', Path(__file__).with_name('verify-emulator-window.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class EmulatorWindowTest(unittest.TestCase):
    def test_healthy_launcher_focus_is_accepted(self):
        gate.verify('  mCurrentFocus=Window{abc u0 com.android.launcher/.Launcher}\n')

    def test_large_snapshot_with_focused_anr_is_rejected(self):
        snapshot = ('mCurrentFocus=Window{abc u0 Application Not Responding: '
                    'com.google.android.apps.nexuslauncher}\n' + 'window details\n' * 20000)
        with self.assertRaisesRegex(ValueError, 'ANR dialog'):
            gate.verify(snapshot)

    def test_null_focus_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'no focused window'):
            gate.verify('  mCurrentFocus=null\n')

    def test_missing_focus_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'no focused window'):
            gate.verify('adb: device offline\n')

    def test_unfocused_window_description_is_not_a_current_anr(self):
        gate.verify('Window #1 Application Not Responding: old.process\n'
                    '  mCurrentFocus=Window{abc u0 com.hoodie.app/.MainActivity}\n')


if __name__ == '__main__':
    unittest.main()
