"""Never install upgrade packages over an existing app or on an unrelated device."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('upgrade', Path(__file__).with_name('r8-upgrade-ui.py'))
upgrade = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)


class ReadOnlyUi:
    def __init__(self, *, api='36', page='16384', avd='Hoodie_QA_API36', packages=''):
        self.values = {
            ('emu', 'avd', 'name'): (avd+'\nOK\n').encode(),
            ('shell', 'getprop', 'ro.build.version.sdk'): api.encode(),
            ('shell', 'getconf', 'PAGE_SIZE'): page.encode(),
            ('shell', 'pm', 'list', 'packages'): packages.encode(),
        }

    def run(self, *command):
        if command not in self.values:
            raise AssertionError('Unexpected device mutation or read: '+repr(command))
        return self.values[command]


class UpgradeOwnershipTest(unittest.TestCase):
    def verify(self, **kwargs):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true', 'R8_UPGRADE_API': '36'}):
            return upgrade.verify_environment(ReadOnlyUi(**kwargs))

    def test_owned_empty_16kb_avd_passes(self):
        self.assertEqual(36, self.verify())

    def test_owned_empty_api35_avd_passes(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true', 'R8_UPGRADE_API': '35'}):
            self.assertEqual(35, upgrade.verify_environment(ReadOnlyUi(api='35', avd='Hoodie_QA_API35')))

    def test_unrelated_avd_is_rejected(self):
        with self.assertRaises(AssertionError): self.verify(avd='Pixel_8')

    def test_wrong_sdk_is_rejected(self):
        with self.assertRaises(AssertionError): self.verify(api='35')

    def test_4kb_device_is_rejected(self):
        with self.assertRaises(AssertionError): self.verify(page='4096')

    def test_existing_application_data_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, 'Existing app data'):
            self.verify(packages='package:com.hoodie.app\n')

    def test_existing_test_package_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, 'Existing test'):
            self.verify(packages='package:com.hoodie.app.test\n')

    def test_local_execution_is_rejected(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'false', 'R8_UPGRADE_API': '36'}):
            with self.assertRaisesRegex(AssertionError, 'Disposable CI'):
                upgrade.verify_environment(ReadOnlyUi())

    def test_search_text_does_not_mask_a_duplicate_place(self):
        root = ET.fromstring('<hierarchy><node class="android.widget.EditText" text="Casa"/><node class="android.widget.TextView" text="Casa"/><node class="android.widget.TextView" text="Casa"/></hierarchy>')
        self.assertEqual(2, upgrade.Flow.place_count(root, 'Casa'))

    def test_unknown_launch_state_is_rejected_and_retained(self):
        raw = b'Status: ok\nLaunchState: UNKNOWN (0)\nActivity: com.hoodie.app/com.hoodie.app.MainActivity\nWaitTime: 1388\nComplete\n'
        class UnknownLaunchUi:
            def run(self, *command):
                if command == ('shell', 'am', 'force-stop', upgrade.PACKAGE): return b''
                if command == ('shell', f'pidof {upgrade.PACKAGE} || [ $? -eq 1 ]'): return b''
                if command == ('shell', 'am', 'start', '-W', '-n', upgrade.PACKAGE+'/com.hoodie.app.MainActivity'): return raw
                raise AssertionError('Unexpected command: '+repr(command))
        base = upgrade.ROOT/'app/build/reports/r8-upgrade-guard-tests'
        base.mkdir(parents=True, exist_ok=True)
        output = Path(tempfile.mkdtemp(dir=base))
        with self.assertRaisesRegex(AssertionError, 'unexpected launch state'):
            upgrade.launch(UnknownLaunchUi(), output, 'unknown')
        self.assertEqual(raw, (output/'unknown-launch.txt').read_bytes())


if __name__ == '__main__': unittest.main()
