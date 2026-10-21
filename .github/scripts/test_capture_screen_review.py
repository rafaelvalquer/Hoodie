"""Candidate recording cannot hide missing, skipped or failing test cases."""
import importlib.util
from pathlib import Path
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
