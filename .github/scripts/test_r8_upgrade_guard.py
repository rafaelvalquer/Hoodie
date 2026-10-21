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
    def __init__(self, *, api='36', page='16384', avd='Hoodie_QA_API36', packages='', user='0'):
        self.values = {
            ('emu', 'avd', 'name'): (avd+'\nOK\n').encode(),
            ('shell', 'getprop', 'ro.build.version.sdk'): api.encode(),
            ('shell', 'getconf', 'PAGE_SIZE'): page.encode(),
            ('shell', 'am', 'get-current-user'): user.encode(),
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

    def test_secondary_android_user_is_rejected_before_installation(self):
        with self.assertRaisesRegex(AssertionError, 'Expected owner user 0'):
            self.verify(user='10')

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


class PackageMetadataTest(unittest.TestCase):
    def exercise(self, api=35, *, transform=lambda b: b, uid=None, digest='a'*64):
        raw = transform((Path(__file__).parent/'fixtures'/f'upgrade-api{api}-package.txt').read_bytes())
        if uid is None:
            uid = (Path(__file__).parent/'fixtures'/f'upgrade-api{api}-uid.txt').read_bytes()
        class PackageUi:
            def run(self, *command):
                values = {
                    ('shell','dumpsys','package',upgrade.PACKAGE): raw,
                    ('shell','pm','list','packages','--user','0','-U',upgrade.PACKAGE): uid,
                    ('shell','pm','path',upgrade.PACKAGE): b'package:/data/app/synthetic/base.apk\n',
                    ('shell','sha256sum','/data/app/synthetic/base.apk'): (digest+'  /data/app/synthetic/base.apk\n').encode(),
                }
                if command not in values: raise AssertionError('Unexpected command: '+repr(command))
                return values[command]
        base = upgrade.ROOT/'app/build/reports/r8-upgrade-guard-tests'
        base.mkdir(parents=True,exist_ok=True)
        output = Path(tempfile.mkdtemp(dir=base))
        try:
            return upgrade.package_details(PackageUi(),output,'baseline',dict(versionCode=6,apkSha256='a'*64))
        finally:
            self.assertEqual(raw,(output/'baseline-package.txt').read_bytes())

    def test_real_api35_appid_metadata_uses_the_explicit_package_uid(self):
        result = self.exercise()
        self.assertEqual(10207,result['uid'])
        self.assertEqual('2026-10-21 22:22:50',result['firstInstallTime'])

    def test_real_api36_appid_metadata_uses_the_explicit_package_uid(self):
        result = self.exercise(36)
        self.assertEqual(10213,result['uid'])
        self.assertEqual('2026-10-21 22:23:51',result['firstInstallTime'])

    def test_missing_uid_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'package UID'): self.exercise(uid=b'package:com.hoodie.app\n')

    def test_another_package_uid_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'package UID'): self.exercise(uid=b'package:com.hoodie.app.test uid:10207\n')

    def test_multiple_package_uids_are_rejected(self):
        with self.assertRaisesRegex(AssertionError,'package UID'):
            self.exercise(uid=b'package:com.hoodie.app uid:10207\npackage:com.hoodie.app.test uid:10208\n')

    def test_missing_first_install_time_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'first-install'):
            self.exercise(transform=lambda b:b.replace(b'firstInstallTime=',b'changedField='))

    def test_duplicate_version_metadata_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'ambiguous'):
            self.exercise(transform=lambda b:b+b'\nversionCode=6\n')

    def test_wrong_installed_apk_still_fails(self):
        with self.assertRaisesRegex(AssertionError,'APK identity mismatch'): self.exercise(digest='b'*64)


class LaunchComponentTest(unittest.TestCase):
    def exercise(self, api=35, transform=lambda b:b):
        raw = transform((Path(__file__).parent/'fixtures'/f'upgrade-api{api}-launch.txt').read_bytes())
        class LaunchUi:
            def run(self, *command):
                values = {
                    ('shell','am','force-stop',upgrade.PACKAGE): b'',
                    ('shell',f'pidof {upgrade.PACKAGE} || [ $? -eq 1 ]'): b'',
                    ('shell','am','start','-W','-n',upgrade.PACKAGE+'/com.hoodie.app.MainActivity'): raw,
                    ('shell','pidof',upgrade.PACKAGE): b'1234\n',
                }
                if command not in values: raise AssertionError('Unexpected command: '+repr(command))
                return values[command]
        base = upgrade.ROOT/'app/build/reports/r8-upgrade-guard-tests'
        base.mkdir(parents=True,exist_ok=True)
        output = Path(tempfile.mkdtemp(dir=base))
        try: return upgrade.launch(LaunchUi(),output,'baseline')
        finally: self.assertEqual(raw,(output/'baseline-launch.txt').read_bytes())

    def test_real_api35_relative_component_is_the_same_activity(self):
        self.assertEqual('1234',self.exercise())

    def test_real_api36_relative_component_is_the_same_activity(self):
        self.assertEqual('1234',self.exercise(36))

    def test_fully_qualified_component_is_accepted(self):
        self.assertEqual('1234',self.exercise(transform=lambda b:b.replace(b'Activity: com.hoodie.app/.MainActivity',b'Activity: com.hoodie.app/com.hoodie.app.MainActivity')))

    def test_another_activity_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'launched Activity'):
            self.exercise(transform=lambda b:b.replace(b'Activity: com.hoodie.app/.MainActivity',b'Activity: com.hoodie.app/.OtherMainActivity'))

    def test_another_package_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'launched Activity'):
            self.exercise(transform=lambda b:b.replace(b'Activity: com.hoodie.app/.MainActivity',b'Activity: com.other/.MainActivity'))

    def test_duplicate_activity_lines_are_rejected(self):
        with self.assertRaisesRegex(AssertionError,'ambiguous'):
            self.exercise(transform=lambda b:b+b'Activity: com.hoodie.app/.MainActivity\n')

    def test_missing_activity_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'launched Activity'):
            self.exercise(transform=lambda b:b.replace(b'Activity:',b'ChangedField:'))

    def test_hot_launch_is_still_rejected(self):
        with self.assertRaisesRegex(AssertionError,'launch state'):
            self.exercise(transform=lambda b:b.replace(b'LaunchState: COLD',b'LaunchState: HOT'))

    def test_missing_timing_is_still_rejected(self):
        with self.assertRaisesRegex(AssertionError,'launch timing'):
            self.exercise(transform=lambda b:b.replace(b'TotalTime:',b'ChangedField:'))


class KeyboardReadinessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        folder=Path(__file__).parent/'fixtures'
        cls.visible=(folder/'upgrade-api35-keyboard-visible.txt').read_bytes()
        cls.hidden=(folder/'upgrade-api36-keyboard-hidden.txt').read_bytes()

    def flow(self, windows):
        events=[]
        class Ui:
            def run(self, *command):
                if command != ('shell','input','keyevent','4'):
                    raise AssertionError('Unexpected command: '+repr(command))
                events.append('back')
            def tap_node(self, node):
                events.append('tap')
        class Flow(upgrade.Flow):
            def __init__(self):
                super().__init__(Ui(),Path('.'))
                self.windows=iter(windows)
            def snapshot(self, stage):
                self.window=next(self.windows)
                events.append('snapshot:'+stage)
                return ET.fromstring('<hierarchy><node class="android.widget.TextView" text="Marcar como favorito" bounds="[111,2014][631,2054]" enabled="true"/></hierarchy>')
        return Flow(),events

    def test_real_api35_visible_ime_is_detected(self):
        self.assertTrue(upgrade.ime_visible(self.visible))

    def test_real_api36_hidden_ime_is_detected(self):
        self.assertFalse(upgrade.ime_visible(self.hidden))

    def test_legacy_input_method_flags_do_not_substitute_for_window_evidence(self):
        with self.assertRaisesRegex(AssertionError,'insets evidence'):
            upgrade.ime_visible(b'mInputShown=false\n')

    def test_one_hidden_source_does_not_override_another_visible_source(self):
        import re
        raw=re.sub(rb'(type=ime[^\r\n]*\bvisible=)true',rb'\1false',self.visible,count=1)
        self.assertNotEqual(raw,self.visible)
        self.assertTrue(upgrade.ime_visible(raw))

    def test_malformed_ime_visibility_is_rejected(self):
        with self.assertRaisesRegex(AssertionError,'IME visibility'):
            upgrade.ime_visible(self.visible.replace(b'visible=true',b'visibility=true'))

    def test_hide_keyboard_sends_back_once_then_observes_hidden_state(self):
        flow,events=self.flow([self.visible,self.hidden])
        flow.hide_keyboard('typing')
        self.assertEqual(1,events.count('back'))
        self.assertFalse(upgrade.ime_visible(flow.window))

    def test_already_hidden_keyboard_does_not_receive_back(self):
        flow,events=self.flow([self.hidden])
        flow.hide_keyboard('typing')
        self.assertNotIn('back',events)

    def test_button_is_tapped_once_only_after_ime_hide_and_fresh_target_snapshot(self):
        flow,events=self.flow([self.visible,self.visible,self.hidden,self.hidden])
        flow.tap('Marcar como favorito','favorite')
        self.assertEqual(1,events.count('back'))
        self.assertEqual(1,events.count('tap'))
        self.assertLess(events.index('back'),events.index('tap'))
        self.assertEqual('snapshot:favorite-after-keyboard',events[-2])

    def test_reopened_keyboard_blocks_the_target_tap(self):
        flow,events=self.flow([self.visible,self.visible,self.hidden,self.visible])
        with self.assertRaisesRegex(AssertionError,'IME reopened'):
            flow.tap('Marcar como favorito','favorite')
        self.assertNotIn('tap',events)

    def test_unobstructed_button_does_not_receive_a_back_action(self):
        flow,events=self.flow([self.hidden])
        flow.tap('Marcar como favorito','favorite')
        self.assertEqual(1,events.count('tap'))
        self.assertNotIn('back',events)


if __name__ == '__main__': unittest.main()
