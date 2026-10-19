"""Prevent startup smoke reports from silently approving reused or warm launches."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('runtime_summary', Path(__file__).with_name('summarize-runtime-launches.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class RuntimeLaunchTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name)
        for opening in (1, 2):
            (self.output / f'stopped-pid-{opening}.txt').write_text('')
            (self.output / f'launch-{opening}.txt').write_text('Status: ok\nLaunchState: COLD\n')
            (self.output / f'pid-{opening}.txt').write_text(str(opening + 100))

    def test_two_new_cold_processes_are_reported(self):
        result = gate.summarize(self.output)
        self.assertEqual(result['processIds'], [101, 102])
        self.assertTrue(result['derivedApkColdLaunch'])

    def test_android_warm_launch_is_never_reported_as_cold(self):
        (self.output / 'launch-2.txt').write_text('Status: ok\r\nLaunchState: WARM\r\n')
        result = gate.summarize(self.output)
        self.assertTrue(result['derivedApkProcessRestart'])
        self.assertFalse(result['derivedApkColdLaunch'])
        self.assertEqual(result['androidLaunchStates'], ['COLD', 'WARM'])

    def test_surviving_process_is_rejected(self):
        (self.output / 'stopped-pid-1.txt').write_text('101\n')
        with self.assertRaisesRegex(ValueError, 'survived force-stop'):
            gate.summarize(self.output)

    def test_reused_process_is_rejected(self):
        (self.output / 'pid-2.txt').write_text('101')
        with self.assertRaisesRegex(ValueError, 'reused'):
            gate.summarize(self.output)

    def test_missing_stop_evidence_is_rejected(self):
        (self.output / 'stopped-pid-2.txt').unlink()
        with self.assertRaises(OSError):
            gate.summarize(self.output)

    def test_failed_activity_start_is_rejected(self):
        (self.output / 'launch-1.txt').write_text('Status: timeout\nLaunchState: COLD\n')
        with self.assertRaisesRegex(ValueError, 'did not succeed'):
            gate.summarize(self.output)

    def test_hot_or_missing_launch_state_is_rejected(self):
        for text in ('Status: ok\nLaunchState: HOT\n', 'Status: ok\n'):
            with self.subTest(text=text):
                (self.output / 'launch-1.txt').write_text(text)
                with self.assertRaisesRegex(ValueError, 'unexpected launch state'):
                    gate.summarize(self.output)

    def test_missing_or_multiple_pids_are_rejected(self):
        for text in ('', '101 102', '0', 'adb: device offline'):
            with self.subTest(text=text):
                (self.output / 'pid-1.txt').write_text(text)
                with self.assertRaisesRegex(ValueError, 'one app PID'):
                    gate.summarize(self.output)


if __name__ == '__main__':
    unittest.main()
