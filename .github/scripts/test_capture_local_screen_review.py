import copy
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location('local_review', Path(__file__).with_name('capture-local-screen-review.py'))
local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local)


class LocalScreenReviewSafetyTest(unittest.TestCase):
    def setUp(self):
        # Keep synthetic files in the workspace on Windows, too. No ADB/emulator is invoked.
        parent = Path(__file__).resolve().parents[2] / 'app/build/reports/local-review-guard-tests'
        parent.mkdir(parents=True, exist_ok=True)
        self.tmp = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name).resolve()
        self.nonce = 'ab' * 16
        name = 'Hoodie_Angle_Intel_' + self.nonce[:12]
        self.avd = self.repo / 'tools/runtime/avd' / (name + '.avd')
        self.avd.mkdir(parents=True)
        self.owner = dict(kind='hoodie-local-screen-review-v1', nonce=self.nonce, avd=name,
                          serial='emulator-5572', port=5572, avdPath=str(self.avd))
        self.path = self.repo / 'app/build/reports/local-screen-review' / self.nonce / 'owner.json'
        self.path.parent.mkdir(parents=True)
        self.write()
        (self.avd / '.hoodie-local-review-owner').write_text(self.nonce)
        self.avd.with_suffix('.ini').write_text('path=' + str(self.avd) + '\n')
        self.prepared = dict(passed=True, avd=name, serial='emulator-5572', nonce=self.nonce, apkInstalled=False)
        self.preparation().write_text(json.dumps(self.prepared))

    def preparation(self):
        return self.path.parent / 'preparation-result.json'

    def write(self):
        self.path.write_text(json.dumps(self.owner))

    def test_fresh_explicit_owner_is_accepted(self):
        self.assertEqual(self.owner, local.validate_owner(self.path, self.repo))

    def test_foreign_owner_location_is_rejected(self):
        outside = self.repo / 'owner.json'
        outside.write_text(json.dumps(self.owner))
        with self.assertRaises(ValueError): local.validate_owner(outside, self.repo)

    def test_foreign_names_ports_and_path_traversal_are_rejected(self):
        original = copy.deepcopy(self.owner)
        for key, value in [('kind', 'user-avd'), ('avd', 'Pixel_8'), ('nonce', '../user'),
                           ('port', 5037), ('port', 5573), ('serial', 'emulator-5554'),
                           ('avdPath', str(self.repo / 'user-device.avd'))]:
            with self.subTest(key=key, value=value):
                self.owner = dict(original, **{key: value})
                self.write()
                with self.assertRaises(ValueError): local.validate_owner(self.path, self.repo)

    def test_marker_and_registration_must_match(self):
        (self.avd / '.hoodie-local-review-owner').write_text('another owner')
        with self.assertRaises(ValueError): local.validate_owner(self.path, self.repo)
        (self.avd / '.hoodie-local-review-owner').write_text(self.nonce)
        self.avd.with_suffix('.ini').write_text('path=' + str(self.repo / 'foreign.avd') + '\n')
        with self.assertRaises(ValueError): local.validate_owner(self.path, self.repo)

    def test_failed_or_unrelated_preparation_is_rejected(self):
        for key, value in [('passed', False), ('avd', 'Pixel_8'), ('serial', 'emulator-5554'),
                           ('nonce', 'cd' * 16), ('apkInstalled', True)]:
            with self.subTest(key=key):
                self.preparation().write_text(json.dumps(dict(self.prepared, **{key: value})))
                with self.assertRaises(ValueError): local.validate_owner(self.path, self.repo)

    def device(self, overrides=None):
        replies = {('get-state',): b'device\n', ('emu', 'avd', 'name'): self.owner['avd'].encode() + b'\nOK\n',
                   ('shell', 'getprop', 'debug.hoodie.local_review_nonce'): self.nonce.encode() + b'\n',
                   ('shell', 'getprop', 'ro.build.version.sdk'): b'34\n',
                   ('shell', 'getconf', 'PAGE_SIZE'): b'4096\n',
                   ('shell', 'pm', 'list', 'packages'): b'package:com.android.settings\n'}
        replies.update(overrides or {})
        calls = []
        def adb(*command):
            calls.append(command)
            return SimpleNamespace(stdout=replies[command])
        return adb, calls

    def test_device_identity_gate_only_reads(self):
        adb, calls = self.device()
        local.verify_device(self.owner, adb)
        self.assertEqual(6, len(calls))
        self.assertFalse(any(c[0] in ('install', 'uninstall', 'push') for c in calls))

    def test_device_rejects_foreign_avd_nonce_api_and_page_size(self):
        mutations = [(('get-state',), b'offline'), (('emu', 'avd', 'name'), b'Pixel_8\nOK'),
                     (('shell', 'getprop', 'debug.hoodie.local_review_nonce'), b'other'),
                     (('shell', 'getprop', 'ro.build.version.sdk'), b'35'),
                     (('shell', 'getconf', 'PAGE_SIZE'), b'16384')]
        for command, value in mutations:
            with self.subTest(command=command):
                adb, calls = self.device({command: value})
                with self.assertRaises(ValueError): local.verify_device(self.owner, adb)
                self.assertFalse(any(c[0] in ('install', 'uninstall', 'push') for c in calls))

    def test_existing_app_or_test_data_blocks_installation(self):
        for package in local.review.PACKAGES:
            with self.subTest(package=package):
                adb, calls = self.device({('shell', 'pm', 'list', 'packages'): ('package:' + package).encode()})
                with self.assertRaises(ValueError): local.verify_device(self.owner, adb)
                self.assertFalse(any(c[0] in ('install', 'uninstall', 'push') for c in calls))


if __name__ == '__main__':
    unittest.main()
