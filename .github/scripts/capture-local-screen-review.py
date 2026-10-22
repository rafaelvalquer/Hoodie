"""Record frozen APKs on an explicitly owned local Windows AVD, without CI approval.

The CI-only collector remains unchanged. This adapter has a separate ownership
gate and reports a different driver/environment; it cannot masquerade as a CI run.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import struct
import subprocess


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


review = load('local_review_common', 'capture-screen-review.py')
health_gate = load('local_review_health', 'verify-emulator-boot.py')
ROOT = Path(__file__).resolve().parents[2]


def validate_owner(path, repo=ROOT):
    path, repo = path.resolve(), repo.resolve()
    if path.name != 'owner.json' or not path.is_relative_to(repo / 'app/build/reports/local-screen-review'):
        raise ValueError('Local owner must be inside the dedicated workspace report directory')
    owner = json.loads(path.read_text(encoding='utf-8'))
    nonce = owner['nonce']
    if not isinstance(nonce, str) or not re.fullmatch('[0-9a-f]{32}', nonce):
        raise ValueError('Invalid local owner nonce')
    if owner['kind'] != 'hoodie-local-screen-review-v1' or owner['avd'] != 'Hoodie_Angle_Intel_' + nonce[:12]:
        raise ValueError('Explicitly owned disposable AVD required')
    port = owner['port']
    if type(port) is not int or not 5556 <= port <= 5680 or port % 2 or owner['serial'] != f'emulator-{port}':
        raise ValueError('Invalid explicitly selected local emulator serial')
    avd = (repo / 'tools/runtime/avd' / (owner['avd'] + '.avd')).resolve()
    if not avd.is_relative_to(repo / 'tools/runtime/avd') or Path(owner['avdPath']).resolve() != avd:
        raise ValueError('AVD directory escapes the owned workspace')
    if (avd / '.hoodie-local-review-owner').read_text().strip() != nonce:
        raise ValueError('AVD marker does not match the owner')
    ini = avd.with_suffix('.ini').read_text(encoding='utf-8')
    paths = [line.partition('=')[2].strip() for line in ini.splitlines() if line.startswith('path=')]
    if len(paths) != 1 or Path(paths[0]).resolve() != avd:
        raise ValueError('AVD registration does not match the owned directory')
    prepared = json.loads((path.parent / 'preparation-result.json').read_text(encoding='utf-8'))
    if (prepared.get('passed') is not True or prepared.get('avd') != owner['avd'] or
            prepared.get('serial') != owner['serial'] or prepared.get('nonce') != nonce or
            prepared.get('apkInstalled') is not False):
        raise ValueError('Fresh successful preparation required')
    return owner


def verify_device(owner, adb):
    # Every command before this gate completes is read-only and explicitly serial-bound.
    if adb('get-state').stdout.strip() != b'device':
        raise ValueError('Owned device is not online')
    name = adb('emu', 'avd', 'name').stdout.decode().splitlines()
    if not name or name[0].strip() != owner['avd']:
        raise ValueError('Device is not the owned AVD')
    if adb('shell', 'getprop', 'debug.hoodie.local_review_nonce').stdout.decode().strip() != owner['nonce']:
        raise ValueError('Device does not carry the owned session nonce')
    if adb('shell', 'getprop', 'ro.build.version.sdk').stdout.strip() != b'34':
        raise ValueError('API34 required')
    if adb('shell', 'getconf', 'PAGE_SIZE').stdout.strip() != b'4096':
        raise ValueError('4KB reference required')
    packages = set(adb('shell', 'pm', 'list', 'packages').stdout.decode().splitlines())
    if any('package:' + name in packages for name in review.PACKAGES):
        raise ValueError('Existing application data must not be replaced')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--owner', required=True, type=Path)
    parser.add_argument('--pair-root', required=True, type=Path)
    parser.add_argument('--adb', required=True, type=Path)
    args = parser.parse_args()
    if os.name != 'nt' or os.environ.get('GITHUB_ACTIONS') == 'true':
        raise ValueError('This adapter requires local Windows, never a simulated CI environment')
    owner = validate_owner(args.owner)
    output = args.owner.resolve().parent / 'capture'
    output.mkdir(exist_ok=False)
    report = dict(sourceCommit=review.SOURCE, sourceTree=review.SOURCE_TREE,
                  apkSha256=list(review.HASHES), requestedGpu='swangle', completed=False,
                  environment='local_windows_owned_avd', recordingOnly=True,
                  comparisonPerformed=False, baselineApproved=False, qualityApproved=False,
                  releaseApproved=False, sameDriverAsRetainedCi=False,
                  hostPlatform=platform.platform(), hostProcessor=platform.processor())
    owned = []
    verified = False

    def adb(*command, timeout=30, check=True):
        return subprocess.run([str(args.adb), '-s', owner['serial'], *command], capture_output=True,
                              timeout=timeout, check=check)

    def capture():
        for name, command in [('properties', ('shell', 'getprop')),
                              ('surfaceflinger', ('shell', 'dumpsys', 'SurfaceFlinger')),
                              ('logcat', ('logcat', '-d', '-v', 'threadtime')),
                              ('crashes', ('logcat', '-d', '-b', 'crash')),
                              ('anr', ('logcat', '-d', '-b', 'events', '-v', 'threadtime', '-s', 'am_anr')),
                              ('window', ('shell', 'dumpsys', 'window', 'displays'))]:
            try:
                result = adb(*command)
                (output / (name + '.txt')).write_bytes(result.stdout)
            except (OSError, subprocess.SubprocessError) as error:
                report.setdefault('captureErrors', []).append(f'{name}: {error}')

    try:
        tree = subprocess.check_output(['git', 'rev-parse', review.SOURCE + ':app/src'], text=True).strip()
        if tree != review.SOURCE_TREE:
            raise ValueError('Frozen source object mismatch')
        paths = [args.pair_root / 'outputs/apk/debug/app-debug.apk',
                 args.pair_root / 'outputs/apk/androidTest/debug/app-debug-androidTest.apk']
        for path, digest in zip(paths, review.HASHES):
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                raise ValueError('Frozen APK identity mismatch')
        report['driverCommit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        report['driverFiles'] = {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                                 for name in ('capture-local-screen-review.py', 'capture-screen-review.py', 'verify-emulator-boot.py')}
        for name, digest in report['driverFiles'].items():
            committed = subprocess.check_output(['git', 'show', 'HEAD:.github/scripts/' + name])
            # Python normalizes source line endings; retain raw hashes separately above.
            working = Path(__file__).with_name(name).read_bytes()
            if committed.replace(b'\r\n', b'\n') != working.replace(b'\r\n', b'\n'):
                raise ValueError('Collector must match its committed driver source')
        verify_device(owner, adb)
        verified = True
        capture()
        if report.get('captureErrors'):
            raise ValueError('Preflight log collection failed; no APK installed')
        health_gate.verify((output / 'anr.txt').read_text(errors='replace'),
                           (output / 'crashes.txt').read_text(errors='replace'))
        window = (output / 'window.txt').read_text(errors='replace')
        focus = re.findall(r'^\s*mCurrentFocus=(.*)$', window, re.M)
        if not focus or any(value.strip() == 'null' or 'Application Not Responding' in value for value in focus):
            raise ValueError('Healthy focused window required')
        renderer = review.verify_renderer((output / 'surfaceflinger.txt').read_text(errors='replace'), 'swangle')
        if 'SwiftShader' not in renderer:
            raise ValueError('Actual ANGLE/SwiftShader renderer required')
        report['rendererDescription'] = renderer
        for package, path in zip(review.PACKAGES, paths):
            adb('install', '-t', str(path.resolve()), timeout=120)
            owned.append(package)
        report['instrumentationStarted'] = True
        raw = output / 'instrumentation.txt'
        with raw.open('xb') as stream:
            result = subprocess.run([str(args.adb), '-s', owner['serial'], 'shell', 'am', 'instrument', '-w', '-r',
                                     '-e', 'class', review.CLASS, '-e', 'recordGoldens', 'true',
                                     'com.hoodie.app.test/androidx.test.runner.AndroidJUnitRunner'],
                                    stdout=stream, stderr=subprocess.STDOUT, timeout=1800)
        report['adbExit'] = result.returncode
        if result.returncode:
            raise ValueError('Instrumentation transport failed')
        report['caseOrder'] = review.verify_runner(raw.read_text(encoding='utf-8'))
        adb('pull', '/sdcard/Android/data/com.hoodie.app/files/goldens/screens', str(output / 'images'), timeout=120)
        images = list((output / 'images').rglob('*.png'))
        expected = review.expected_images()
        if len(images) != 222 or {path.name for path in images} != set(expected):
            raise ValueError('Incomplete image inventory')
        inventory = []
        for path in sorted(images):
            blob = path.read_bytes()
            if blob[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', blob[16:24]) != expected[path.name]:
                raise ValueError('Invalid PNG dimensions')
            inventory.append(dict(file=path.name, sha256=hashlib.sha256(blob).hexdigest(), dimensions=expected[path.name]))
        report.update(completed=True, cases=180, images=inventory)
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        report['error'] = str(error)
    finally:
        if verified:
            health = review.finish_capture(report, output, owned, adb, capture)
            # Retain separate post-cleanup buffers, including crashes caused by uninstall.
            try:
                events = adb('logcat', '-d', '-b', 'events', '-v', 'threadtime', '-s', 'am_anr').stdout
                crashes = adb('logcat', '-d', '-b', 'crash').stdout
                (output / 'post-cleanup-anr.txt').write_bytes(events)
                (output / 'post-cleanup-crashes.txt').write_bytes(crashes)
                health_gate.verify(events.decode(errors='replace'), crashes.decode(errors='replace'), phase='runtime')
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                report.setdefault('captureErrors', []).append('Post-cleanup: ' + str(error))
            health['captureErrors'] = report.get('captureErrors', [])
            (output / 'health.json').write_text(json.dumps(health, indent=2) + '\n', encoding='utf-8')
        (output / 'result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    if (not verified or not report['completed'] or health['androidFailures'] or
            health['captureErrors'] or health['cleanupErrors']):
        raise SystemExit('Local recording incomplete or unhealthy; original evidence retained')
    print('180 cases/222 candidates recorded locally; no baseline, CI, or release approval')


if __name__ == '__main__':
    main()
