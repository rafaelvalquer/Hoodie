"""UI/SAF backup diagnostic, restricted to the disposable CI 16KB AVD.

Uses only the permanent public synthetic fixture and its public test phrase.
Never copies Keystore/app data, modifies the fixture, grants permissions,
changes clocks/JIT, or approves physical/full-history release acceptance.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from android_ui import AndroidUi

PACKAGE = 'com.hoodie.app.homologacao'
FIXTURE_SHA = 'c0d7739652d6b5e1a8e1c6df1b7cf592342db6d334c6c1b6d16fa016b43e20a7'
INTEGRITY = 'Compatibilidade: compatível. Integridade verificada.'
BUSY = {'Preparando…', 'Lendo os dados salvos…', 'Protegendo seus dados…',
        'Salvando o arquivo…', 'Verificando o arquivo salvo…'}


class Flow:
    def __init__(self, ui, out):
        self.ui, self.out, self.observations = ui, out, []

    def snapshot(self, label):
        stem = f'{len(self.observations)+1:03d}-{label}'
        root, xml = self.ui.tree()
        with (self.out/(stem+'.xml')).open('xb') as f:
            f.write(xml)
        window = self.ui.run('shell', 'dumpsys', 'window', 'displays')
        with (self.out/(stem+'-window.txt')).open('xb') as f:
            f.write(window)
        owners = {n.get('package') for n in root.iter('node')}
        allowed = {PACKAGE, 'com.google.android.documentsui', 'com.android.documentsui'}
        assert owners and owners <= allowed, owners
        focus = re.findall(r'^\s*mCurrentFocus=(.*)$', window.decode(errors='replace'), re.M)
        assert focus and all(any(f' {p}/' in value for p in allowed) for value in focus), focus
        self.observations.append(dict(stage=label, xml=stem+'.xml', window=stem+'-window.txt',
                                      exportBusy=bool(set(self.text(root)) & BUSY)))
        (self.out/'observations.json').write_text(json.dumps(self.observations, indent=2)+'\n', encoding='utf-8')
        return root

    @staticmethod
    def text(root):
        return [n.get('text', '') for n in root.iter('node')]

    @staticmethod
    def find(root, label):
        nodes = list(root.iter('node'))
        return ([n for n in nodes if n.get('content-desc') == label] or
                [n for n in nodes if n.get('text') == label])

    def tap(self, label, stage):
        nodes = self.find(self.snapshot(stage), label)
        assert len(nodes) == 1 and nodes[0].get('enabled') == 'true', (label, len(nodes))
        self.ui.tap_node(nodes[0])

    def wait(self, predicate, stage, timeout=90):
        deadline = time.monotonic()+timeout
        while time.monotonic() < deadline:
            root = self.snapshot(stage)
            if predicate(root):
                return root
            time.sleep(.5)
        raise AssertionError('UI condition not reached: '+stage)

    def hide_keyboard(self):
        if 'mInputShown=true' in self.ui.run('shell', 'dumpsys', 'input_method').decode():
            self.ui.run('shell', 'input', 'keyevent', '4')

    def phrase(self, index, count):
        root = self.snapshot(f'phrase-{index}-before')
        fields = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
        assert len(fields) == count and not fields[index].get('text')
        self.ui.tap_node(fields[index])
        self.ui.input_ascii('hoodie recovery phrase')
        self.hide_keyboard()

    def validate(self, filename, original):
        self.tap('Restaurar backup', 'before-open-backup')
        root = self.snapshot('document-picker')
        if not self.find(root, filename):
            self.tap('Show roots', 'before-roots')
            self.tap('Downloads', 'before-downloads')
        self.tap(filename, 'before-select-file')
        self.phrase(0, 1)
        self.tap('Validar backup', 'before-validation')
        root = self.wait(lambda r: INTEGRITY in self.text(r), 'authenticated-preview')
        assert 'Lugares: 1' in self.text(root)
        if original:
            for label in ['Contextos da Jornada: 0', 'Memórias: 0', 'Histórico digital: não incluído']:
                assert label in self.text(root), label
        else:
            assert 'Histórico digital: incluído' in self.text(root)
        return root


def verify_disposable_emulator(ui):
    api = os.environ.get('R8_QA_API', '35')
    assert api in ('35', '36'), 'Unsupported QA API'
    assert ui.run('emu', 'avd', 'name').decode().splitlines()[0].strip() == f'Hoodie_QA_API{api}'
    assert ui.run('shell', 'getprop', 'ro.build.version.sdk').decode().strip() == api
    assert ui.run('shell', 'getconf', 'PAGE_SIZE').strip() == b'16384'
    return int(api)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert os.environ.get('GITHUB_ACTIONS') == 'true', 'Disposable GitHub CI only'
    assert re.fullmatch(r'emulator-\d+', args.serial)
    args.output.mkdir(parents=True, exist_ok=True)
    if any(args.output.iterdir()):
        raise ValueError('A new empty evidence directory is required')
    fixture = ROOT/'app/src/androidTest/assets/backup-v1-minimal.hoodiebackup'
    assert hashlib.sha256(fixture.read_bytes()).hexdigest() == FIXTURE_SHA
    ui = AndroidUi(args.serial)
    api = verify_disposable_emulator(ui)
    build = json.loads((ROOT/'app/build/reports/r8-qa/build.json').read_text(encoding='utf-8'))
    installed = ui.run('shell', 'pm', 'path', PACKAGE).decode().strip()
    assert installed.startswith('package:') and len(installed.splitlines()) == 1
    installed_hash = ui.run('shell', 'sha256sum', installed[len('package:'):]).decode().split()[0]
    assert installed_hash == build['apkSha256'], 'Exact APK derived from this AAB required'
    flow = Flow(ui, args.output)
    assert flow.find(flow.snapshot('initial-backup-screen'), 'Criar backup')
    source_name = 'fixture-v1-source.hoodiebackup'
    destination = '/sdcard/Download/'+source_name
    ui.run('shell', 'mkdir', '-p', '/sdcard/Download')
    ui.run('shell', 'test', '!', '-e', destination)
    ui.run('push', str(fixture), destination)
    assert ui.run('shell', 'sha256sum', destination).decode().split()[0] == FIXTURE_SHA
    before_pid = ui.run('shell', 'pidof', PACKAGE).decode().strip()
    root = flow.validate(source_name, original=True)
    button = flow.find(root, 'Restaurar e substituir')
    assert len(button) == 1
    bounds = list(map(int, re.findall(r'\d+', button[0].get('bounds', ''))))
    if len(bounds) != 4 or bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
        scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true']
        assert len(scrolls) == 1
        left, top, right, bottom = map(int, re.findall(r'\d+', scrolls[0].get('bounds', '')))
        ui.run('shell', 'input', 'swipe', str((left+right)//2), str(bottom-80), str((left+right)//2), str(top+80), '400')
    flow.tap('Restaurar e substituir', 'before-activation')
    flow.wait(lambda r: any(t.startswith('Fixture ') for t in flow.text(r)), 'restored-home')
    restored_pid = ui.run('shell', 'pidof', PACKAGE).decode().strip()
    assert restored_pid and restored_pid != before_pid
    ui.run('shell', 'am', 'force-stop', PACKAGE)
    ui.run('shell', 'am', 'start', '-W', '-n', PACKAGE+'/com.hoodie.app.MainActivity')
    flow.wait(lambda r: any(t.startswith('Fixture ') for t in flow.text(r)), 'reopened-home')
    reopened_pid = ui.run('shell', 'pidof', PACKAGE).decode().strip()
    assert reopened_pid and reopened_pid != restored_pid
    flow.tap('Ajustes', 'before-settings')
    assert 'Fixture' in flow.text(flow.snapshot('restored-settings'))
    flow.tap('Dados e segurança', 'before-data')
    flow.tap('Criar backup', 'before-export')
    flow.phrase(0, 2)
    flow.phrase(1, 2)
    root = flow.snapshot('export-options')
    assert any(n.get('checked') == 'true' for n in root.iter('node') if n.get('checkable') == 'true')
    filename = 'Hoodie_R8_fixture_reexport.hoodiebackup'
    ui.run('shell', 'test', '!', '-e', '/sdcard/Download/'+filename)
    flow.tap('Escolher onde salvar', 'before-save-picker')
    fields = [n for n in flow.snapshot('save-picker').iter('node') if n.get('class') == 'android.widget.EditText']
    assert len(fields) == 1
    ui.tap_node(fields[0])
    ui.run('shell', 'input', 'keycombination', '113', '29')
    ui.input_ascii(filename)
    flow.hide_keyboard()
    flow.tap('SAVE', 'before-save')
    start_export = len(flow.observations)
    flow.wait(lambda r: 'Backup criado e verificado com sucesso.' in flow.text(r), 'export-progress')
    busy = sum(o['exportBusy'] for o in flow.observations[start_export:])
    assert busy > 0, 'No accessibility snapshot observed during export; concurrency coverage unproven'
    assert ui.run('shell', 'pidof', PACKAGE).decode().strip() == reopened_pid
    output = args.output/filename
    ui.run('pull', '/sdcard/Download/'+filename, str(output))
    blob = output.read_bytes()
    digest = hashlib.sha256(blob).hexdigest()
    assert digest == ui.run('shell', 'sha256sum', '/sdcard/Download/'+filename).decode().split()[0]
    assert blob[:8] == b'HOODIEB1' and struct.unpack('>II', blob[8:16]) == (1, 600000)
    flow.validate(filename, original=False)  # Authenticate via UI without activating again.
    report = dict(passed=True,sourceFixtureSha256=FIXTURE_SHA,apkSha256=installed_hash,
        api=api,pageSize=16384,processIds=[before_pid,restored_pid,reopened_pid],
        fixtureRestored=True,reopenedAfterRestore=True,exportSuccessObserved=True,
        accessibilitySnapshotsDuringExport=busy,reexportSha256=digest,reexportBytes=len(blob),
        reexportAuthenticatedByAppUi=True,independentlyAuthenticated=False,
        snapshots=len(flow.observations),releaseApproved=False,
        scope='Permanent one-place fixture restored/reopened/exported via UI and SAF on R8. Minimal diagnostic, not full-history/cross-device/physical/interruption/provider acceptance or closure of HVR-NATIVE-API35-001.')
    (args.output/'result.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
