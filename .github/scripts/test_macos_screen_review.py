import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('macos_review', Path(__file__).with_name('macos-screen-review.py'))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


class MacosObservationGuardTest(unittest.TestCase):
    def test_unsupported_host_never_creates_a_device(self):
        for ci, system, arch, vendor in [('false', 'Darwin', 'x86_64', 'GenuineIntel'),
                                         ('true', 'Windows', 'x86_64', 'GenuineIntel'),
                                         ('true', 'Linux', 'x86_64', 'GenuineIntel'),
                                         ('true', 'Darwin', 'arm64', 'Apple'),
                                         ('true', 'Darwin', 'x86_64', 'AuthenticAMD')]:
            with self.subTest(ci=ci, system=system, arch=arch, vendor=vendor):
                with patch.dict(review.os.environ, {'GITHUB_ACTIONS': ci}, clear=True), \
                     patch.object(review.platform, 'system', return_value=system), \
                     patch.object(review.platform, 'machine', return_value=arch), \
                     patch.object(review.subprocess, 'check_output', return_value=vendor), \
                     patch.object(review.subprocess, 'Popen') as spawn, \
                     patch.object(review.subprocess, 'run') as run:
                    with self.assertRaises(ValueError): review.main()
                    spawn.assert_not_called()
                    run.assert_not_called()

    def test_recorded_windows_legacy_renderer_is_not_angle(self):
        snapshot = (Path(__file__).resolve().parents[2] /
                    'docs/validation/release-v1/local-intel-81a2-renderer.txt').read_text()
        with self.assertRaises(ValueError): review.require_renderer(snapshot)

    def test_hardware_angle_is_not_the_requested_software_control(self):
        with self.assertRaises(ValueError):
            review.require_renderer('GLES: Google, ANGLE (Metal GPU), OpenGL ES 3.2')

    def test_software_angle_description_is_retained_exactly(self):
        snapshot = 'GLES: Google, ANGLE (SwiftShader Device (Subzero)), OpenGL ES 3.2'
        self.assertEqual(snapshot, review.require_renderer(snapshot))

    def finish_streams(self, *, events='', exited=None):
        parent = Path(__file__).resolve().parents[2] / 'app/build/reports/macos-observation-guard-tests'
        parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as temporary:
            output = Path(temporary)
            (output / 'continuous-anr.txt').write_text(events)
            (output / 'continuous-crashes.txt').write_text('')
            streams = []
            for label in ['anr', 'crashes']:
                process = unittest.mock.Mock()
                process.poll.return_value = exited
                process.wait.return_value = -15 if exited is None else exited
                streams.append((label, process, io.BytesIO(), io.BytesIO()))
            report = {}
            review.stop_health_streams(streams, output, report, review.module('health_test', 'verify-emulator-boot.py'))
            self.assertTrue(all(stdout.closed and stderr.closed for _, _, stdout, stderr in streams))
            return report, streams

    def test_continuous_anr_survives_a_later_empty_snapshot(self):
        evidence = (Path(__file__).resolve().parents[2] /
                    'docs/validation/release-v1/local-intel-81a2-followup-anr-events.txt').read_text()
        report, _ = self.finish_streams(events=evidence)
        self.assertIn('ANR', report['continuousHealthError'])

    def test_unexpected_clean_stream_exit_is_still_missing_evidence(self):
        report, streams = self.finish_streams(exited=0)
        self.assertEqual(2, len(report['streamErrors']))
        for _, process, _, _ in streams: process.terminate.assert_not_called()

    def test_healthy_live_streams_are_stopped_by_their_own_handles(self):
        report, streams = self.finish_streams()
        self.assertEqual({}, report)
        for _, process, _, _ in streams: process.terminate.assert_called_once_with()


if __name__ == '__main__':
    unittest.main()
