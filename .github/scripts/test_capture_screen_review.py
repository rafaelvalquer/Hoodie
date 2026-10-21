"""Candidate recording cannot hide missing, skipped or failing test cases."""
import importlib.util
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('review',Path(__file__).with_name('capture-screen-review.py'))
review=importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)

def raw_results(status=0):
    cases=[n.removesuffix('.png') for n in review.expected_images() if not n.endswith(('_bottom.png','_map.png'))]
    lines=[]
    for name in cases:
        for code in (1,status):
            lines += [f'INSTRUMENTATION_STATUS: class={review.CLASS}',
                      f'INSTRUMENTATION_STATUS: test=screenMatchesReviewedBaseline[{name}]',
                      f'INSTRUMENTATION_STATUS_CODE: {code}']
    return '\n'.join(lines+['INSTRUMENTATION_CODE: -1',''])

class ScreenReviewEvidenceTest(unittest.TestCase):
    def folder(self):
        parent=Path(__file__).resolve().parents[2]/'app/build/reports/screen-review-parser-tests'
        parent.mkdir(parents=True,exist_ok=True)
        return Path(tempfile.mkdtemp(dir=parent))

    def finalize(self, report, owned, *, pull_fails=False, existing=False):
        output=self.folder();events=[];blob=b'partial diagnostic image bytes'
        if existing:
            (output/'partial-images').mkdir()
            (output/'partial-images/previous.png').write_bytes(b'preserve')
        def capture():
            events.append('capture')
            (output/'crashes.txt').write_bytes(b'')
            (output/'anr.txt').write_bytes(b'')
        def adb(*args,**kwargs):
            events.append(args[0])
            if args[0]=='pull':
                if pull_fails:raise subprocess.SubprocessError('pull failed')
                (Path(args[2])/'partial.png').write_bytes(blob)
        health=review.finish_capture(report,output,owned,adb,capture)
        return output,events,blob,health

    def test_failed_recording_keeps_partial_bytes_before_uninstall_and_writes_health(self):
        report=dict(completed=False,instrumentationStarted=True,error='PixelCopy failed')
        out,events,blob,health=self.finalize(report,list(review.PACKAGES))
        self.assertEqual(['capture','pull','uninstall','uninstall'],events)
        self.assertEqual(blob,(out/'partial-images/partial.png').read_bytes())
        self.assertEqual(hashlib.sha256(blob).hexdigest(),report['partialImages'][0]['sha256'])
        self.assertFalse(report['partialImages'][0]['metadataMatchesExpectedViewport'])
        self.assertEqual('PixelCopy failed',json.loads((out/'result.json').read_text())['error'])
        self.assertFalse(health['recordingCompleted'])
        self.assertFalse(json.loads((out/'health.json').read_text())['releaseApproved'])

    def test_failed_pull_is_retained_as_failure_and_still_cleans_owned_packages(self):
        report=dict(completed=False,instrumentationStarted=True)
        out,events,_,health=self.finalize(report,list(review.PACKAGES),pull_fails=True)
        self.assertEqual(['capture','pull','uninstall','uninstall'],events)
        self.assertTrue(health['captureErrors'])
        self.assertFalse(health['recordingCompleted'])
        self.assertTrue((out/'health.json').is_file())

    def test_unknown_package_ownership_never_pulls_or_uninstalls(self):
        report=dict(completed=False,instrumentationStarted=True)
        _,events,_,health=self.finalize(report,[])
        self.assertEqual(['capture'],events)
        self.assertFalse(health['recordingCompleted'])

    def test_existing_partial_evidence_is_not_overwritten(self):
        report=dict(completed=False,instrumentationStarted=True)
        out,events,_,health=self.finalize(report,list(review.PACKAGES),existing=True)
        self.assertNotIn('pull',events)
        self.assertEqual(b'preserve',(out/'partial-images/previous.png').read_bytes())
        self.assertTrue(health['captureErrors'])

    def test_complete_recording_is_not_pulled_again_during_cleanup(self):
        _,events,_,health=self.finalize(dict(completed=True,instrumentationStarted=True),list(review.PACKAGES))
        self.assertEqual(['capture','uninstall','uninstall'],events)
        self.assertTrue(health['recordingCompleted'])
        self.assertFalse(health['releaseApproved'])

    def test_effective_angle_renderer_is_recorded(self):
        line='GLES: Google, Android Emulator Translator (ANGLE (Google, Vulkan SwiftShader)), OpenGL ES 3.1'
        self.assertEqual(line,review.verify_renderer('Display\n'+line+'\n','swangle'))

    def test_requesting_angle_does_not_accept_the_legacy_renderer(self):
        line='GLES: Google, Android Emulator Translator (Google SwiftShader), OpenGL ES 3.0 SwiftShader 4.0.0.1'
        with self.assertRaisesRegex(ValueError,'did not expose an ANGLE'):review.verify_renderer(line,'swangle')
        self.assertEqual(line,review.verify_renderer(line,'swiftshader'))

    def test_missing_or_ambiguous_effective_renderer_is_rejected(self):
        for snapshot in ('', 'GLES: ANGLE\nGLES: legacy\n'):
            with self.subTest(snapshot=snapshot),self.assertRaisesRegex(ValueError,'ambiguous GLES'):
                review.verify_renderer(snapshot,'swangle')

    def test_complete_case_and_image_inventory(self):
        self.assertEqual(222,len(review.expected_images()))
        self.assertEqual(42,sum(n.startswith('diary_') for n in review.expected_images()))
        self.assertEqual(180,len(review.verify_runner(raw_results())))

    def test_failed_case_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Failed, skipped'):review.verify_runner(raw_results(-2))

    def test_assumption_or_ignore_is_rejected(self):
        for code in (-3,-4):
            with self.subTest(code=code),self.assertRaisesRegex(ValueError,'Failed, skipped'):review.verify_runner(raw_results(code))

    def test_duplicate_case_is_rejected(self):
        raw=raw_results().replace('home_normal_360x640_f130','home_normal_360x640_f100')
        with self.assertRaisesRegex(ValueError,'Missing or duplicated'):review.verify_runner(raw)

    def test_missing_completion_is_rejected(self):
        raw=raw_results().replace('INSTRUMENTATION_STATUS_CODE: 0','INSTRUMENTATION_STATUS_CODE: 2',1)
        with self.assertRaisesRegex(ValueError,'Missing or duplicated'):review.verify_runner(raw)

    def test_runner_crash_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'did not complete'):review.verify_runner(raw_results().replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_CODE: 0'))

if __name__=='__main__':unittest.main()
