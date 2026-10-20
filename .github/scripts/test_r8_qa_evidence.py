"""Ensure repeated UI polling retains every hierarchy, without running Android."""
import importlib.util
from pathlib import Path
import json
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('r8_accessibility', Path(__file__).with_name('r8-qa-accessibility.py'))
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class FakeUi:
    def __init__(self, serial):
        self.state = 'welcome'
        self.home_reads = 0
        self.strings = {n.get('name'): (n.text or '').strip('"')
                        for n in ET.parse(qa.ROOT/'app/src/main/res/values/strings.xml').getroot()}

    def tree(self):
        root = ET.Element('hierarchy')
        def node(**attrs):
            ET.SubElement(root, 'node', {'package': qa.PACKAGE, **attrs})
        if self.state == 'welcome':
            node(**{'content-desc': self.strings['ui_onboarding_screen_3'], 'enabled': 'true'})
        elif self.state == 'name':
            node(**{'class': 'android.widget.EditText', 'text': 'Hoodie'})
            node(**{'content-desc': self.strings['ui_onboarding_screen_3'], 'enabled': 'true'})
        elif self.state == 'tracking':
            node(**{'content-desc': self.strings['prog_enter_home'], 'enabled': 'true'})
        elif self.state == 'home':
            self.home_reads += 1
            node(**{'content-desc': 'Ajustes', 'enabled': 'true'})
            if self.home_reads > 1:
                node(**{'content-desc': 'Hoodie'})
        elif self.state == 'settings':
            node(**{'content-desc': 'Dados e segurança', 'enabled': 'true'})
        elif self.state == 'backup':
            node(**{'content-desc': 'Criar backup', 'enabled': 'true'})
        return root, ET.tostring(root, encoding='utf-8')

    def tap_node(self, node):
        self.state = {'welcome': 'name', 'name': 'tracking', 'tracking': 'home',
                      'home': 'settings', 'settings': 'backup'}[self.state]

    def run(self, *command):
        if command == ('shell', 'dumpsys', 'window', 'displays'):
            return f'  mCurrentFocus=Window{{healthy u0 {qa.PACKAGE}/.MainActivity}}'.encode()
        if command == ('shell', 'pidof', qa.PACKAGE):
            return b'4321'
        raise AssertionError(command)


class EvidenceRetentionTest(unittest.TestCase):
    def test_every_poll_keeps_its_own_xml_and_window(self):
        base = (qa.ROOT/'app/build/reports/r8-evidence-tests').resolve()
        base.mkdir(parents=True, exist_ok=True)
        # Retain this diagnostic's synthetic files, including failed negative controls.
        output = Path(tempfile.mkdtemp(prefix='evidence-', dir=base)).resolve()
        self.assertTrue(output.is_relative_to(base))
        argv = ['qa', '--serial', 'emulator-5554', '--output', str(output)]
        with patch.object(sys, 'argv', argv), patch.object(qa, 'AndroidUi', FakeUi), patch.object(qa.time, 'sleep'):
            qa.main()
        observations = json.loads((output/'observations.json').read_text())
        result = json.loads((output/'result.json').read_text())
        self.assertEqual(69, len(observations))
        self.assertEqual(69, result['snapshots'])
        self.assertEqual(len(observations), len(list(output.glob('*.xml'))))
        self.assertEqual(len(observations), len(list(output.glob('*-window.txt'))))
        home = [row for row in observations if row['stage'] == 'home-ready']
        self.assertEqual(2, len(home))
        self.assertNotEqual(home[0]['xml'], home[1]['xml'])
        trees = [ET.parse(output/row['xml']).getroot() for row in home]
        self.assertFalse(any(n.get('content-desc') == 'Hoodie' for n in trees[0].iter('node')))
        self.assertTrue(any(n.get('content-desc') == 'Hoodie' for n in trees[1].iter('node')))
        for row in observations:
            self.assertTrue((output/row['xml']).is_file())
            self.assertTrue((output/row['window']).is_file())

    def test_existing_evidence_is_rejected_before_any_device_read(self):
        base = (qa.ROOT/'app/build/reports/r8-evidence-tests').resolve()
        base.mkdir(parents=True, exist_ok=True)
        output = Path(tempfile.mkdtemp(prefix='existing-', dir=base)).resolve()
        self.assertTrue(output.is_relative_to(base))
        sentinel = output/'previous.xml'
        sentinel.write_bytes(b'<previous-evidence/>')
        argv = ['qa', '--serial', 'emulator-5554', '--output', str(output)]
        with patch.object(sys, 'argv', argv), patch.object(qa, 'AndroidUi') as device:
            with self.assertRaisesRegex(ValueError, 'new empty evidence directory'):
                qa.main()
            device.assert_not_called()
        self.assertEqual(b'<previous-evidence/>', sentinel.read_bytes())


if __name__ == '__main__':
    unittest.main()
