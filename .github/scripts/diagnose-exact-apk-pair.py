"""Cross-host control for the preserved c40 APKs; never a replacement quality gate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

GOLDEN_CLASS = 'com.hoodie.app.ui.golden.ScreenGoldenMatrixTest'
PACKAGES = ('com.hoodie.app', 'com.hoodie.app.test')
HASHES = ('33a3b55dd099ca041c7f482f4deb101b5964168265562ab75b856c6e24066955',
          'a32b0997e90824c28476657a6e28aab5a76c0da44cc91e7d188facfb9bf88410')


def summarize(raw):
    """Count actual runner events; Android's process exit alone does not prove tests passed."""
    fields = {}
    started, results = [], []
    for line in raw.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, sep, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if sep:
                fields[key] = value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.partition(': ')[2])
            if code == 2:  # Runner progress, not a test result.
                fields = {}
                continue
            case = (fields.get('class'), fields.get('test'))
            if case[0] != GOLDEN_CLASS or not case[1]:
                raise ValueError(f'Unexpected or unidentified test event: {case}, code {code}')
            if code == 1:
                started.append(case)
            elif code in (0, -1, -2, -3, -4):
                results.append((case, code))
            else:
                raise ValueError(f'Unknown test status {code}')
            fields = {}
    if len(started) != 180 or len(set(started)) != 180:
        raise ValueError(f'Expected 180 unique starts, got {len(started)}')
    ended = [case for case, _ in results]
    if len(ended) != 180 or set(ended) != set(started) or len(set(ended)) != 180:
        raise ValueError('Missing, duplicated or unmatched test completion')
    if re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\s*$', raw, re.M) != ['-1']:
        raise ValueError('Instrumentation did not finish normally')
    return dict(tests=180, caseOrder=[case[1] for case in started], passed=sum(code == 0 for _, code in results),
                failures=[dict(classname=case[0], name=case[1], status=code)
                          for case, code in results if code != 0],
                skipped=sum(code in (-3, -4) for _, code in results))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--app', required=True, type=Path)
    parser.add_argument('--test', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    env = dict(os.environ, ANDROID_SERIAL=args.serial)
    report = dict(scope=__doc__, sourceCommit='c40f864bb8920ddafd7065c7d23235f4fd41e6f1',
                  releaseApproved=False, completed=False)
    owned = []

    def adb(*command, timeout=30, check=True):
        result = subprocess.run([args.adb, '-s', args.serial, *command], env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=timeout, encoding='utf-8', errors='replace')
        if check and result.returncode:
            raise RuntimeError(f'ADB command failed: {command}: {result.stdout}')
        return result

    def capture():
        for name, command in [('logcat', ('logcat', '-d', '-v', 'threadtime')),
                              ('crashes', ('logcat', '-d', '-b', 'crash')),
                              ('window', ('shell', 'dumpsys', 'window', 'displays')),
                              ('properties', ('shell', 'getprop')),
                              ('surfaceflinger', ('shell', 'dumpsys', 'SurfaceFlinger'))]:
            try:
                (args.output / f'{name}.txt').write_text(adb(*command, check=False).stdout, encoding='utf-8')
            except (OSError, subprocess.SubprocessError) as error:
                report.setdefault('captureErrors', []).append(f'{name}: {error}')

    try:
        for path, expected in zip((args.app, args.test), HASHES):
            if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise ValueError(f'APK identity mismatch: {path}')
        report['apkSha256'] = list(HASHES)
        if adb('get-state').stdout.strip() != 'device':
            raise ValueError('Device is not online')
        if adb('shell', 'getprop', 'ro.build.version.sdk').stdout.strip() != '34':
            raise ValueError('Control requires API 34')
        packages = set(adb('shell', 'pm', 'list', 'packages').stdout.splitlines())
        if any(f'package:{p}' in packages for p in PACKAGES):
            raise ValueError('Control requires absent app/test packages; existing data will not be replaced')
        # The shared bootstrap must already have completed focus and boot-ANR checks.
        window = adb('shell', 'dumpsys', 'window', 'displays').stdout
        focus = re.findall(r'^\s*mCurrentFocus=(.*)$', window, re.M)
        if not focus or any('null' == f.strip() or 'Application Not Responding' in f for f in focus):
            raise ValueError('Focused healthy window required')
        events = adb('logcat', '-d', '-b', 'events', '-s', 'am_anr').stdout
        (args.output / 'preflight-anr.txt').write_text(events, encoding='utf-8')
        if re.search(r'am_anr\s*:', events):
            raise ValueError('Boot ANR recorded; no tests started')
        capture()
        for path, package in zip((args.app, args.test), PACKAGES):
            # Only this invocation's fresh installations may be removed in cleanup.
            adb('install', '-t', str(path.resolve()), timeout=120)
            owned.append(package)
        adb('logcat', '-c')
        raw_path = args.output / 'instrumentation.txt'
        with raw_path.open('w', encoding='utf-8') as log:
            result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument',
                                     '-w', '-r', '-e', 'class', GOLDEN_CLASS,
                                     '-e', 'verifyGoldens', 'true',
                                     'com.hoodie.app.test/androidx.test.runner.AndroidJUnitRunner'],
                                    env=env, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
        report['adbExitCode'] = result.returncode
        report.update(summarize(raw_path.read_text(encoding='utf-8')))
        report['completed'] = True
        report['diagnosticPassed'] = result.returncode == 0 and report['passed'] == 180 and not report['skipped']
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        report['error'] = str(error)
        report['diagnosticPassed'] = False
    finally:
        capture()
        if owned:
            try:
                adb('pull', '/sdcard/Android/data/com.hoodie.app/files/goldens', str(args.output / 'goldens'), timeout=120, check=False)
            except (OSError, subprocess.SubprocessError) as error:
                report.setdefault('captureErrors', []).append(f'goldens: {error}')
        for package in reversed(owned):
            try:
                removed = adb('uninstall', package, timeout=60, check=False)
                if removed.returncode:
                    report.setdefault('cleanupErrors', []).append(f'{package}: {removed.stdout}')
            except (OSError, subprocess.SubprocessError) as error:
                report.setdefault('cleanupErrors', []).append(f'{package}: {error}')
        (args.output / 'result.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    return 0 if report.get('diagnosticPassed') else 1


if __name__ == '__main__':
    sys.exit(main())
