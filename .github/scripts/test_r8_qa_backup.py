"""Guard destructive restore against a wrong device or reused evidence folder."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('r8_backup', Path(__file__).with_name('r8-qa-backup.py'))
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)

class BackupDiagnosticTest(unittest.TestCase):
    def test_supported_api_requires_matching_owned_avd_sdk_and_16kb(self):
        for api in ('35', '36'):
            with self.subTest(api=api), patch.dict(os.environ, {'R8_QA_API': api}):
                ui = Mock()
                ui.run.side_effect = [f'Hoodie_QA_API{api}\nOK\n'.encode(), api.encode(), b'16384\n']
                self.assertEqual(int(api), qa.verify_disposable_emulator(ui))
                self.assertEqual([
                    ('emu', 'avd', 'name'),
                    ('shell', 'getprop', 'ro.build.version.sdk'),
                    ('shell', 'getconf', 'PAGE_SIZE'),
                ], [c.args for c in ui.run.call_args_list])

    def test_sdk_mismatch_or_4kb_page_is_rejected(self):
        for responses in ([b'Hoodie_QA_API36\n', b'35\n'], [b'Hoodie_QA_API36\n', b'36\n', b'4096\n']):
            with self.subTest(responses=responses), patch.dict(os.environ, {'R8_QA_API': '36'}):
                ui = Mock()
                ui.run.side_effect = responses
                with self.assertRaises(AssertionError): qa.verify_disposable_emulator(ui)

    def test_unsupported_api_never_reads_a_device(self):
        with patch.dict(os.environ, {'R8_QA_API': '37'}):
            ui = Mock()
            with self.assertRaisesRegex(AssertionError, 'Unsupported QA API'): qa.verify_disposable_emulator(ui)
            ui.run.assert_not_called()

    def folder(self):
        base=qa.ROOT/'app/build/reports/r8-backup-driver-tests'
        base.mkdir(parents=True, exist_ok=True)
        return Path(tempfile.mkdtemp(dir=base))

    def test_non_ci_invocation_never_reads_a_device(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'false'}), patch.object(sys, 'argv', ['qa','--serial','emulator-5554','--output',str(self.folder())]), patch.object(qa, 'AndroidUi') as ui:
            with self.assertRaisesRegex(AssertionError, 'Disposable GitHub CI only'):qa.main()
            ui.assert_not_called()

    def test_existing_evidence_never_reads_a_device(self):
        out=self.folder()
        (out/'previous.xml').write_bytes(b'preserved')
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true'}), patch.object(sys, 'argv', ['qa','--serial','emulator-5554','--output',str(out)]), patch.object(qa, 'AndroidUi') as ui:
            with self.assertRaisesRegex(ValueError, 'new empty evidence'):qa.main()
            ui.assert_not_called()
        self.assertEqual(b'preserved',(out/'previous.xml').read_bytes())

    def test_wrong_avd_is_rejected_before_any_mutation(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true'}), patch.object(sys, 'argv', ['qa','--serial','emulator-5554','--output',str(self.folder())]), patch.object(qa, 'AndroidUi') as ui:
            ui.return_value.run.return_value=b'Pixel_8\nOK\n'
            with self.assertRaises(AssertionError):qa.main()
            ui.return_value.run.assert_called_once_with('emu','avd','name')

    def test_repeated_busy_reads_are_retained_and_counted(self):
        out=self.folder()
        root=ET.fromstring(f'<hierarchy><node package="{qa.PACKAGE}" text="Protegendo seus dados…"/></hierarchy>')
        class Ui:
            def tree(self):return root,ET.tostring(root)
            def run(self,*args):return f'mCurrentFocus=Window{{1 u0 {qa.PACKAGE}/.MainActivity}}'.encode()
        flow=qa.Flow(Ui(),out)
        flow.snapshot('export-progress');flow.snapshot('export-progress')
        result=json.loads((out/'observations.json').read_text())
        self.assertEqual(2,len(list(out.glob('*.xml'))))
        self.assertEqual(2,len(set(o['xml'] for o in result)))
        self.assertTrue(all(o['exportBusy'] for o in result))

if __name__ == '__main__':unittest.main()
