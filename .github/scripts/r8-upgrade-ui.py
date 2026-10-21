"""Real prior-schema APK upgrade on a new, owned CI AVD; synthetic UI data only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from android_ui import AndroidUi

PACKAGE = 'com.hoodie.app'
BASELINE = 'e44c17889a1430e77a78d1854e3924f28afbe85a'
PAIR = ROOT/'app/build/reports/r8-upgrade-qa'
PROFILE = 'Migracao'
PLACE = 'Casa Migracao'
NEW_PLACE = 'Trabalho Migracao'


def verify_environment(ui):
    assert os.environ.get('GITHUB_ACTIONS') == 'true', 'Disposable CI only'
    api = os.environ.get('R8_UPGRADE_API')
    assert api in ('35', '36'), 'Unsupported API'
    assert ui.run('emu', 'avd', 'name').decode().splitlines()[0].strip() == f'Hoodie_QA_API{api}'
    assert ui.run('shell', 'getprop', 'ro.build.version.sdk').decode().strip() == api
    assert ui.run('shell', 'getconf', 'PAGE_SIZE').strip() == b'16384'
    assert ui.run('shell', 'am', 'get-current-user').strip() == b'0', 'Expected owner user 0'
    packages = set(ui.run('shell', 'pm', 'list', 'packages').decode().splitlines())
    assert 'package:'+PACKAGE not in packages, 'Existing app data must not be replaced'
    assert 'package:'+PACKAGE+'.test' not in packages, 'Existing test installation'
    return int(api)


def verify_pair():
    pair = json.loads((PAIR/'pair.json').read_text())
    assert pair['baseline']['commit'] == BASELINE
    assert pair['current']['commit'] == os.environ['GITHUB_SHA']
    assert pair['baseline']['certificateSha256'] == pair['current']['certificateSha256']
    for label, code, schema in [('baseline', 6, 11), ('current', 7, 12)]:
        item = pair[label]
        assert item['package'] == PACKAGE and item['versionCode'] == code
        assert item['schemaVersionFromSource'] == schema and item['developmentSigningOnly']
        for name, key in [('derived.apk', 'apkSha256'), ('bundle.aab', 'aabSha256'),
                          ('mapping.txt', 'mappingSha256'), ('strings.xml', 'stringsSha256'),
                          ('development-certificate.der', 'certificateSha256')]:
            assert hashlib.sha256((PAIR/label/name).read_bytes()).hexdigest() == item[key], (label, name)
    return pair


def bounds(node):
    values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
    return values if len(values) == 4 and values[2] > values[0] and values[3] > values[1] else None


class Flow:
    def __init__(self, ui, out):
        self.ui, self.out, self.observations = ui, out, []

    def snapshot(self, stage):
        stem = f'{len(self.observations)+1:03d}-{stage}'
        root, xml = self.ui.tree()
        window = self.ui.run('shell', 'dumpsys', 'window', 'displays')
        (self.out/(stem+'.xml')).write_bytes(xml)
        (self.out/(stem+'-window.txt')).write_bytes(window)
        self.observations.append(dict(stage=stage, xml=stem+'.xml', window=stem+'-window.txt'))
        (self.out/'observations.json').write_text(json.dumps(self.observations, indent=2)+'\n')
        assert {n.get('package') for n in root.iter('node')} == {PACKAGE}, 'Unexpected UI owner'
        focus = re.findall(r'^\s*mCurrentFocus=(.*)$', window.decode(errors='replace'), re.M)
        assert focus and all(f' {PACKAGE}/' in value for value in focus), 'Unexpected focused window'
        return root

    @staticmethod
    def text(root):
        return [n.get('text', '') for n in root.iter('node')]

    @staticmethod
    def place_count(root, name):
        # Exclude the search EditText, which contains the same name as its result.
        return sum(n.get('class') == 'android.widget.TextView' and n.get('text') == name for n in root.iter('node'))

    @staticmethod
    def find(root, label):
        return ([n for n in root.iter('node') if n.get('content-desc') == label] or
                [n for n in root.iter('node') if n.get('text') == label or n.get('hint') == label])

    def wait(self, stage, predicate):
        deadline = time.monotonic()+45
        while True:
            root = self.snapshot(stage)
            if predicate(root):
                return root
            assert time.monotonic() < deadline, 'UI did not reach '+stage
            time.sleep(.5)

    def visible(self, stage, select, direction='down'):
        for _ in range(8):
            root = self.snapshot(stage)
            nodes = select(root)
            assert len(nodes) <= 1, ('Ambiguous target', stage)
            if nodes and bounds(nodes[0]):
                assert nodes[0].get('enabled') == 'true', ('Disabled target', stage)
                return nodes[0]
            scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true' and bounds(n)]
            assert len(scrolls) == 1, ('No unique visible scroll container', stage)
            left, top, right, bottom = bounds(scrolls[0])
            start, end = (bottom-80, top+80) if direction == 'down' else (top+80, bottom-80)
            self.ui.run('shell', 'input', 'swipe', str((left+right)//2), str(start), str((left+right)//2), str(end), '400')
        raise AssertionError('Target not visible: '+stage)

    def tap(self, label, stage):
        self.ui.tap_node(self.visible(stage, lambda root: self.find(root, label)))

    def hide_keyboard(self):
        if 'mInputShown=true' in self.ui.run('shell', 'dumpsys', 'input_method').decode():
            self.ui.run('shell', 'input', 'keyevent', '4')

    def enter(self, label, value, stage):
        assert value in (PROFILE, PLACE, NEW_PLACE, '-23.0,-46.0', '-23.1,-46.1')
        def fields(root):
            return [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText' and
                    (label is None or any(label in (x.get('text'), x.get('hint'), x.get('content-desc')) for x in n.iter('node')))]
        self.ui.tap_node(self.visible(stage, fields, direction='up' if label == 'Buscar por nome' else 'down'))
        self.ui.run('shell', 'input', 'keycombination', '113', '29')
        # Fixed public coordinates only; commas are data, never shell syntax.
        if ',' in value:
            self.ui.run('shell', 'input', 'text', value)
        else:
            self.ui.input_ascii(value)
        self.hide_keyboard()

    def add_place(self, name, coordinates, stage, work=False):
        self.tap('Adicionar lugar', stage+'-add')
        if work:
            self.tap('Trabalho', stage+'-type')
        self.enter('Nome (opcional)', name, stage+'-name')
        self.tap('Ou digitar coordenadas', stage+'-manual')
        self.enter('lat, lng', coordinates, stage+'-coordinates')
        self.tap('Salvar', stage+'-save')
        return self.wait(stage+'-saved', lambda r: name in self.text(r))


def package_details(ui, out, stage, expected):
    raw = ui.run('shell', 'dumpsys', 'package', PACKAGE)
    (out/(stage+'-package.txt')).write_bytes(raw)
    text = raw.decode()
    assert len(re.findall(r'^\s*Package \['+re.escape(PACKAGE)+r'\]', text, re.M)) == 1, 'Expected one package metadata section'
    versions = re.findall(r'^\s*versionCode=(\d+)\b', text, re.M)
    dates = re.findall(r'^\s*firstInstallTime=([^\r\n]+)', text, re.M)
    assert len(versions) == 1 and len(dates) == 1, 'Missing or ambiguous version/first-install metadata'
    version, first_install = int(versions[0]), dates[0].strip()
    assert first_install, 'Missing first-install time'
    # Modern dumpsys calls the package-level identifier appId, not userId.
    # Read the actual per-user UID instead of treating an appId as a UID.
    uid_raw = ui.run('shell', 'pm', 'list', 'packages', '--user', '0', '-U', PACKAGE)
    (out/(stage+'-uid.txt')).write_bytes(uid_raw)
    uid_match = re.fullmatch(r'package:'+re.escape(PACKAGE)+r' uid:([1-9]\d*)', uid_raw.decode().strip())
    assert uid_match, 'Missing or ambiguous installed package UID'
    uid = int(uid_match[1])
    assert version == expected['versionCode']
    installed_raw = ui.run('shell', 'pm', 'path', PACKAGE)
    (out/(stage+'-installed-path.txt')).write_bytes(installed_raw)
    installed = installed_raw.decode().strip()
    assert installed.startswith('package:') and len(installed.splitlines()) == 1
    digest_raw = ui.run('shell', 'sha256sum', installed.removeprefix('package:'))
    (out/(stage+'-installed-sha256.txt')).write_bytes(digest_raw)
    digest = digest_raw.decode().split()[0]
    assert digest == expected['apkSha256'], 'Installed APK identity mismatch'
    return dict(uid=uid, versionCode=version, firstInstallTime=first_install, apkSha256=digest)


def launch(ui, out, stage):
    ui.run('shell', 'am', 'force-stop', PACKAGE)
    stopped = ui.run('shell', f'pidof {PACKAGE} || [ $? -eq 1 ]')
    (out/(stage+'-stopped-pid.txt')).write_bytes(stopped)
    assert not stopped.strip()
    raw = ui.run('shell', 'am', 'start', '-W', '-n', PACKAGE+'/com.hoodie.app.MainActivity')
    (out/(stage+'-launch.txt')).write_bytes(raw)
    assert len(re.findall(rb'^Status: ok\s*$', raw, re.M)) == 1
    assert len(re.findall(rb'^LaunchState: (?:COLD|WARM)\s*$', raw, re.M)) == 1, 'Missing or unexpected launch state'
    components = re.findall(rb'^Activity: ([^\r\n]+)', raw, re.M)
    expected_components = {(PACKAGE+'/com.hoodie.app.MainActivity').encode(), (PACKAGE+'/.MainActivity').encode()}
    assert len(components) == 1 and components[0].strip() in expected_components, 'Unexpected or ambiguous launched Activity'
    assert re.search(rb'^TotalTime: \d+\s*$', raw, re.M), 'Missing launch timing'
    pid = ui.run('shell', 'pidof', PACKAGE).decode().strip()
    (out/(stage+'-pid.txt')).write_text(pid+'\n')
    assert re.fullmatch(r'[1-9]\d*', pid), 'Expected one app process'
    return pid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert os.environ.get('GITHUB_ACTIONS') == 'true'
    assert re.fullmatch(r'emulator-\d+', args.serial)
    assert not args.output.exists(), 'New evidence directory required'
    pair = verify_pair()
    ui = AndroidUi(args.serial)
    api = verify_environment(ui)
    args.output.mkdir(parents=True)
    flow = Flow(ui, args.output)
    report = dict(passed=False, api=api, pageSize=16384, pair=pair, releaseApproved=False,
        scope='UI-observed profile/place/favorite preservation across real derived R8 APK upgrade, then new place write. Not all-row/relationship, full-history, backup, physical-device, upload-key or Play acceptance.')
    try:
        ui.run('install', str(PAIR/'baseline/derived.apk'), timeout=120)
        before = package_details(ui, args.output, 'baseline', pair['baseline'])
        old_pid = launch(ui, args.output, 'baseline')
        strings = {n.get('name'): (n.text or '').strip('"') for n in ET.parse(PAIR/'baseline/strings.xml').getroot()}
        start = strings['ui_onboarding_screen_3']
        flow.wait('baseline-welcome', lambda r: any(n.get('content-desc') == start and n.get('enabled') == 'true' for n in r.iter('node')))
        flow.tap(start, 'baseline-before-name')
        flow.enter(None, PROFILE, 'baseline-name')
        for key in ('ui_onboarding_screen_6', 'ui_onboarding_screen_11', 'ui_onboarding_screen_16', 'ui_onboarding_screen_28', 'ui_onboarding_screen_38'):
            flow.tap(strings[key], 'baseline-'+key)
        flow.wait('baseline-home', lambda r: any(PROFILE in t for t in flow.text(r)) and bool(flow.find(r, 'Ajustes')))
        flow.tap(strings['nav_places'], 'baseline-places')
        flow.add_place(PLACE, '-23.0,-46.0', 'baseline-place')
        flow.tap('Marcar como favorito', 'baseline-favorite')
        root = flow.wait('baseline-preserved-data', lambda r: 'Remover dos favoritos' in flow.text(r))
        assert flow.place_count(root, PLACE) == 1 and '150 m' in flow.text(root)
        baseline_reopened = launch(ui, args.output, 'baseline-reopen')
        assert baseline_reopened != old_pid
        flow.wait('baseline-reopened-home', lambda r: any(PROFILE in t for t in flow.text(r)))
        ui.run('shell', 'am', 'force-stop', PACKAGE)
        ui.run('install', '-r', str(PAIR/'current/derived.apk'), timeout=120)
        after = package_details(ui, args.output, 'current', pair['current'])
        assert before['uid'] == after['uid'] and before['firstInstallTime'] == after['firstInstallTime']
        current_pid = launch(ui, args.output, 'current')
        assert current_pid != baseline_reopened
        flow.wait('current-home', lambda r: any(PROFILE in t for t in flow.text(r)) and bool(flow.find(r, 'Ajustes')))
        current_strings = {n.get('name'): (n.text or '').strip('"') for n in ET.parse(PAIR/'current/strings.xml').getroot()}
        flow.tap(current_strings['nav_places'], 'current-places')
        root = flow.wait('current-preserved-data', lambda r: PLACE in flow.text(r))
        assert flow.place_count(root, PLACE) == 1 and '150 m' in flow.text(root)
        assert 'Remover dos favoritos' in flow.text(root)
        flow.add_place(NEW_PLACE, '-23.1,-46.1', 'current-place', work=True)
        root = flow.snapshot('current-both-places')
        # Search explicitly to verify each record even when the list is taller than the viewport.
        for name in (PLACE, NEW_PLACE):
            flow.enter('Buscar por nome', name, 'current-find-'+('old' if name == PLACE else 'new'))
            root = flow.wait('current-record', lambda r: name in flow.text(r))
            assert flow.place_count(root, name) == 1, 'Expected exactly one place result'
        reopened_pid = launch(ui, args.output, 'current-reopen')
        assert reopened_pid != current_pid
        flow.wait('current-reopened-home', lambda r: any(PROFILE in t for t in flow.text(r)))
        flow.tap(current_strings['nav_places'], 'current-reopened-places')
        for name in (PLACE, NEW_PLACE):
            flow.enter('Buscar por nome', name, 'current-reopened-find-'+('old' if name == PLACE else 'new'))
            root = flow.wait('current-reopened-record', lambda r: name in flow.text(r))
            assert flow.place_count(root, name) == 1
            if name == PLACE:
                assert 'Remover dos favoritos' in flow.text(root) and '150 m' in flow.text(root)
        report.update(passed=True, before=before, after=after, uidPreserved=True, firstInstallTimePreserved=True,
            processIds=[old_pid, baseline_reopened, current_pid, reopened_pid],
            profilePreserved=True, placeAndFavoritePreserved=True,newPlaceWriteObserved=True,
            bothPlacesPreservedAfterReopen=True,
            historicalSchemaVersionFromSource=11,currentSchemaVersionFromSource=12,
            observedDatabaseSchemaReadDirectly=False,snapshots=len(flow.observations))
    except Exception as error:
        report['error'] = f'{type(error).__name__}: {error}'
        raise
    finally:
        (args.output/'result.json').write_text(json.dumps(report, indent=2)+'\n')


if __name__ == '__main__':
    main()
