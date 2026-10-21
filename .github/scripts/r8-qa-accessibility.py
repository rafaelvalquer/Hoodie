"""Bounded, read-heavy accessibility diagnostic on a disposable R8 QA AVD.

Does not export/restore backups, grant permissions, disable JIT or change app
semantics. Passing this diagnostic does not close an export-specific crash.
"""
import argparse
import json
from pathlib import Path
import re
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from android_ui import AndroidUi

PACKAGE = 'com.hoodie.app.homologacao'
UI_READY_TIMEOUT = 45


def wait_for_welcome(snapshot, label):
    """Focus can belong to SplashScreen before RootState reaches ONBOARDING."""
    deadline = time.monotonic()+UI_READY_TIMEOUT
    while True:
        root = snapshot('welcome')
        if any(n.get('content-desc') == label and n.get('enabled') == 'true' for n in root.iter('node')):
            return root
        visible = {value for n in root.iter('node')
                   for value in (n.get('text'), n.get('content-desc')) if value}
        # Wait only for the observed, intentional SplashScreen. A database error,
        # unexpected navigation or different owner must remain an immediate failure.
        assert visible == {'HOODIE', 'Seu companheiro de rotina.'}, ('Unexpected initial UI', visible)
        assert time.monotonic() < deadline, 'Onboarding did not become ready; SplashScreen remained visible'
        time.sleep(.5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert re.fullmatch(r'emulator-\d+', args.serial), 'Disposable emulator required'
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        raise ValueError('A new empty evidence directory is required')
    ui = AndroidUi(args.serial)
    observations = []
    snapshot_count = 0

    def snapshot(label):
        nonlocal snapshot_count
        snapshot_count += 1
        stem = f'{snapshot_count:03d}-{label}'
        root, xml = ui.tree()
        xml_name = f'{stem}.xml'
        window_name = f'{stem}-window.txt'
        with (out/xml_name).open('xb') as stream:
            stream.write(xml)
        packages = {n.get('package') for n in root.iter('node')}
        assert packages == {PACKAGE}, f'Unexpected window owner: {packages}'
        window = ui.run('shell','dumpsys','window','displays')
        with (out/window_name).open('xb') as stream:
            stream.write(window)
        focus = re.findall(r'^\s*mCurrentFocus=(.*)$',window.decode(errors='replace'),re.MULTILINE)
        assert focus and all(f' {PACKAGE}/' in value for value in focus), focus
        pid = ui.run('shell','pidof',PACKAGE).decode().strip()
        assert re.fullmatch(r'[1-9]\d*',pid), pid
        observations.append(dict(stage=label,pid=int(pid),nodes=len(list(root.iter('node'))),
                                 xml=xml_name,window=window_name))
        (out/'observations.json').write_text(json.dumps(observations,indent=2)+'\n',encoding='utf-8')
        return root

    def tap(label, stage):
        root = snapshot(stage)
        nodes = [n for n in root.iter('node') if n.get('content-desc') == label]
        nodes = nodes or [n for n in root.iter('node') if n.get('text') == label]
        assert len(nodes) == 1 and nodes[0].get('enabled') == 'true', (label,len(nodes))
        ui.tap_node(nodes[0])

    strings = {n.get('name'): (n.text or '').strip('"')
               for n in ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()}
    # Each CI installation is new: no existing data or permissions are touched.
    wait_for_welcome(snapshot, strings['ui_onboarding_screen_3'])
    tap(strings['ui_onboarding_screen_3'],'before-name')
    root = snapshot('name')
    fields = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
    assert len(fields) == 1 and fields[0].get('text') == 'Hoodie'
    tap(strings['ui_onboarding_screen_3'],'before-tracking')
    tap(strings['prog_enter_home'],'before-home')
    deadline = time.monotonic()+UI_READY_TIMEOUT
    while True:
        root = snapshot('home-ready')
        if any(n.get('content-desc') == 'Hoodie' for n in root.iter('node')):
            break
        assert time.monotonic() < deadline, 'Home scene not presented'
        time.sleep(.5)
    tap('Ajustes','before-settings')
    for index in range(30):
        snapshot(f'settings-{index:02d}')
    tap('Dados e segurança','before-backup')
    for index in range(30):
        root = snapshot(f'backup-{index:02d}')
        assert any(n.get('content-desc') == 'Criar backup' for n in root.iter('node'))
    assert len({row['pid'] for row in observations}) == 1, 'Unexpected process restart'
    (out/'result.json').write_text(json.dumps(dict(
        passed=True,package=PACKAGE,snapshots=len(observations),
        evidenceFormatVersion=2,retainedXmlFiles=len(observations),
        settingsSnapshots=30,backupSnapshots=30,
        scope='Accessibility node creation on unchanged screens; no export/restore or production acceptance'
    ),indent=2)+'\n',encoding='utf-8')


if __name__ == '__main__':
    main()
