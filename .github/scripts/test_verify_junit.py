"""Reject missing evidence and failures hidden by inconsistent JUnit summary attributes."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

VERIFIER = Path(__file__).with_name('verify-junit.py').resolve()


class JunitEvidenceTest(unittest.TestCase):
    def verify(self, body=None, tests=1):
        with tempfile.TemporaryDirectory() as directory:
            if body is not None:
                Path(directory, 'TEST-evidence.xml').write_text(
                    f'<testsuite tests="{tests}" failures="0" errors="0" skipped="0">{body}</testsuite>',
                    encoding='utf-8')
            return subprocess.run([sys.executable, str(VERIFIER), directory],
                                  capture_output=True, text=True).returncode

    def test_real_passing_case_is_accepted(self):
        self.assertEqual(0, self.verify('<testcase classname="Example" name="passes"/>'))

    def test_skip_cannot_be_hidden_by_zero_summary(self):
        self.assertNotEqual(0, self.verify('<testcase name="skips"><skipped/></testcase>'))

    def test_failure_cannot_be_hidden_by_zero_summary(self):
        self.assertNotEqual(0, self.verify('<testcase name="fails"><failure>broken</failure></testcase>'))

    def test_claimed_test_count_requires_matching_cases(self):
        self.assertNotEqual(0, self.verify('<testcase name="only-one"/>', tests=99))

    def test_missing_reports_fail(self):
        self.assertNotEqual(0, self.verify())


if __name__ == '__main__':
    unittest.main()
