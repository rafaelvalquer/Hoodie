"""Preflight rejects focus blockers even when dumpsys exceeds the pipe buffer."""
import importlib.util
import subprocess
import tempfile
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

    def test_release_requires_its_exact_application_id(self):
        gate.verify('mCurrentFocus=Window{abc u0 com.hoodie.app/.MainActivity}\n', 'com.hoodie.app')
        with self.assertRaisesRegex(ValueError, 'Expected focused package'):
            gate.verify('mCurrentFocus=Window{abc u0 com.hoodie.app.homologacao/.MainActivity}\n',
                        'com.hoodie.app')

    def test_release_does_not_accept_a_package_in_unfocused_history(self):
        with self.assertRaisesRegex(ValueError, 'Expected focused package'):
            gate.verify('Window #1 com.hoodie.app/.MainActivity\n'
                        'mCurrentFocus=Window{abc u0 com.android.launcher/.Launcher}\n',
                        'com.hoodie.app')

    def test_anr_on_one_display_is_rejected_even_if_another_has_no_focus(self):
        with self.assertRaisesRegex(ValueError, 'ANR dialog'):
            gate.verify('mCurrentFocus=null\nmCurrentFocus=Window{abc u0 Application Not Responding: launcher}\n')

    def test_waits_for_boot_focus_and_preserves_each_snapshot(self):
        snapshots=iter(['mCurrentFocus=null\n', 'mCurrentFocus=Window{abc u0 launcher/.Main}\n'])
        now=[0]
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory)
            count=gate.wait_for_focus(lambda remaining: next(snapshots),output,
                clock=lambda: now[0], sleep=lambda seconds: now.__setitem__(0,now[0]+seconds))
            self.assertEqual(count,2)
            self.assertEqual((output/'preflight-window-001.txt').read_text(),'mCurrentFocus=null\n')
            self.assertEqual((output/'preflight-window.txt').read_text(),(output/'preflight-window-002.txt').read_text())

    def test_null_focus_has_a_real_deadline(self):
        now=[0]
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(gate.WindowNotReady,'after 2 seconds'):
                gate.wait_for_focus(lambda remaining: 'mCurrentFocus=null\n',Path(directory),timeout=2,
                    clock=lambda: now[0], sleep=lambda seconds: now.__setitem__(0,now[0]+seconds))
            self.assertEqual(now[0],2)
            self.assertEqual(len(list(Path(directory).glob('preflight-window-*.txt'))),2)

    def test_anr_is_not_retried_or_dismissed(self):
        def cannot_sleep(seconds):
            self.fail('ANR must fail immediately')
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError,'ANR dialog'):
                gate.wait_for_focus(lambda remaining: 'mCurrentFocus=Window{abc u0 Application Not Responding: launcher}\n',
                    Path(directory),sleep=cannot_sleep)
            self.assertTrue((Path(directory)/'preflight-window-001.txt').exists())

    def test_adb_failure_is_not_retried(self):
        def failed_read(remaining):
            raise subprocess.CalledProcessError(1,'adb')
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(subprocess.CalledProcessError):
                gate.wait_for_focus(failed_read,Path(directory))

    def test_slow_observation_cannot_approve_after_deadline(self):
        now=[0]
        def slow_read(remaining):
            now[0]=3
            return 'mCurrentFocus=Window{abc u0 launcher/.Main}\n'
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(gate.WindowNotReady,'exceeded 2 seconds'):
                gate.wait_for_focus(slow_read,Path(directory),timeout=2,clock=lambda:now[0])
            self.assertTrue((Path(directory)/'preflight-window-001.txt').exists())


if __name__ == '__main__':
    unittest.main()
