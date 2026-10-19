"""Small adb/UIAutomator inspection helper for isolated acceptance devices.

Always supply a serial. Input is restricted to synthetic ASCII test values;
do not enter personal passwords through a command-line test tool.
"""
import argparse
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


class AndroidUi:
    def __init__(self, serial, adb='adb'):
        self.serial, self.adb = serial, adb

    def run(self, *args, timeout=20):
        return subprocess.run([self.adb, '-s', self.serial, *args], check=True,
                              capture_output=True, timeout=timeout).stdout

    def tree(self, timeout=25):
        deadline = time.monotonic() + timeout
        last = ''
        while time.monotonic() < deadline:
            result = self.run('shell', 'uiautomator', 'dump', '/sdcard/hoodie-acceptance-window.xml').decode(errors='replace')
            last = result
            if 'dumped to:' in result:
                data = self.run('exec-out', 'cat', '/sdcard/hoodie-acceptance-window.xml')
                return ET.fromstring(data), data
            time.sleep(.2)
        raise RuntimeError(f'No fresh accessibility root: {last}')

    def tap_node(self, node):
        values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        if len(values) != 4:
            raise ValueError('Node has no usable bounds')
        left, top, right, bottom = values
        if right <= left or bottom <= top:
            raise ValueError('Target is clipped or not visible; inspect/scroll before interacting')
        self.run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    def input_ascii(self, value):
        if not re.fullmatch(r'[A-Za-z0-9 _.\-]+', value):
            raise ValueError('Only synthetic ASCII letters, digits, spaces, dot, underscore and hyphen are accepted')
        self.run('shell', 'input', 'text', value.replace(' ', '%s'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default=os.environ.get('ADB', 'adb'))
    parser.add_argument('--output', type=Path, default=Path('app/build/hoodie-homologacao-window.xml'))
    parser.add_argument('--tap-label')
    parser.add_argument('--tap-class')
    parser.add_argument('--input-ascii')
    args = parser.parse_args()
    ui = AndroidUi(args.serial, args.adb)
    root, xml = ui.tree()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(xml)
    nodes = list(root.iter('node'))
    if args.tap_label:
        candidates = [node for node in nodes if node.get('content-desc') == args.tap_label]
        candidates = candidates or [node for node in nodes if node.get('text') == args.tap_label]
        if len(candidates) != 1:
            raise RuntimeError(f'Expected one node for label {args.tap_label!r}, found {len(candidates)}')
        ui.tap_node(candidates[0])
    if args.tap_class:
        candidates = [node for node in nodes if node.get('class') == args.tap_class]
        if len(candidates) != 1:
            raise RuntimeError(f'Expected one editable target, found {len(candidates)}')
        ui.tap_node(candidates[0])
    if args.input_ascii:
        ui.input_ascii(args.input_ascii)
    print(json.dumps([dict(text=n.get('text'), description=n.get('content-desc'),
                           bounds=n.get('bounds'), widget=n.get('class'), resource=n.get('resource-id'))
                      for n in nodes if n.get('text') or n.get('content-desc') or n.get('class') == 'android.widget.EditText'],
                     ensure_ascii=True))
