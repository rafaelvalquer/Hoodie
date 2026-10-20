"""Reject misleading raw runner results in the cross-host diagnostic."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('exact_pair', Path(__file__).with_name('diagnose-exact-apk-pair.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def fixture(failure=None, skipped=None):
    lines = []
    for n in range(180):
        for status in (1, -2 if n == failure else -4 if n == skipped else 0):
            lines += [f'INSTRUMENTATION_STATUS: class={module.GOLDEN_CLASS}',
                      f'INSTRUMENTATION_STATUS: test=screenMatchesReviewedBaseline[case_{n}]',
                      f'INSTRUMENTATION_STATUS_CODE: {status}']
    return '\n'.join(lines+['INSTRUMENTATION_CODE: -1', ''])


class RawEvidenceTest(unittest.TestCase):
    def test_complete_success(self):
        result = module.summarize(fixture())
        self.assertEqual((180, 0, []), (result['passed'], result['skipped'], result['failures']))
        self.assertEqual(180, len(result['caseOrder']))

    def test_failure_is_not_process_success(self):
        result = module.summarize(fixture(failure=4))
        self.assertEqual(179, result['passed'])
        self.assertEqual(-2, result['failures'][0]['status'])

    def test_assumption_is_not_approval(self):
        result = module.summarize(fixture(skipped=4))
        self.assertEqual(179, result['passed'])
        self.assertEqual(1, result['skipped'])

    def test_missing_completion_rejected(self):
        with self.assertRaises(ValueError):
            module.summarize(fixture().replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: 2', 1))

    def test_duplicate_case_rejected(self):
        with self.assertRaises(ValueError):
            module.summarize(fixture().replace('[case_1]', '[case_0]'))

    def test_crashed_runner_rejected(self):
        with self.assertRaises(ValueError):
            module.summarize(fixture().replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0'))


if __name__ == '__main__':
    unittest.main()
