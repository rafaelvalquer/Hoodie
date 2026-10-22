"""One Intel/macOS frozen-APK observation; no baseline or production approval."""
import importlib.util
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import time


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def require_renderer(snapshot):
    common = module('macos_capture', 'capture-screen-review.py')
    renderer = common.verify_renderer(snapshot, 'swangle')
    if 'SwiftShader' not in renderer:
        raise ValueError('ANGLE must actually use the software SwiftShader backend')
    return renderer


def stop_health_streams(streams, output, report, health):
    for name, process, stdout, stderr in streams:
        before = process.poll()
        if before is not None:
            report.setdefault('streamErrors', []).append(f'{name}: exited before collection ended ({before})')
        else:
            process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
            report.setdefault('streamErrors', []).append(f'{name}: required forced collector shutdown')
        finally:
            stdout.close()
            stderr.close()
    if streams:
        try:
            health.verify((output / 'continuous-anr.txt').read_text(errors='replace'),
                          (output / 'continuous-crashes.txt').read_text(errors='replace'), phase='runtime')
        except (OSError, ValueError) as error:
            report['continuousHealthError'] = str(error)


def main():
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or platform.system() != 'Darwin' or
            platform.machine() != 'x86_64'):
        raise ValueError('Disposable Intel macOS GitHub runner required')
    if subprocess.check_output(['sysctl', '-n', 'machdep.cpu.vendor'], text=True).strip() != 'GenuineIntel':
        raise ValueError('Actual Intel CPU required')
    sdk = Path(os.environ['ANDROID_HOME'])
    binary = Path(os.environ['HOODIE_EMULATOR_BIN'])
    root = Path(os.environ['RUNNER_TEMP'])
    avd_home = root / 'hoodie-macos-avd34'
    if avd_home.exists():
        raise ValueError('Never reuse or replace an existing AVD directory')
    out = Path('app/build/reports/screen-review/emulator')
    out.mkdir(parents=True, exist_ok=False)
    avd_home.mkdir()
    name, serial = 'Hoodie_QA_API34', 'emulator-5554'
    env = dict(os.environ, ANDROID_AVD_HOME=str(avd_home), ANDROID_SERIAL=serial,
               ANDROID_SDK_ROOT=str(sdk), QA_REVIEW_GPU='swangle')
    adb_bin = sdk / 'platform-tools/adb'
    image = sdk / 'system-images/android-34/google_apis/x86_64'
    properties = (image / 'source.properties').read_text()
    fields = dict((line.split('=', 1)[0].strip(), line.split('=', 1)[1].strip())
                  for line in properties.splitlines() if '=' in line)
    (out / 'emulator-image.properties').write_text(properties)
    (out / 'emulator-image-package.xml').write_bytes((image / 'package.xml').read_bytes())
    if (fields.get('Pkg.Revision') != '14' or fields.get('AndroidVersion.ApiLevel') != '34' or
            fields.get('SystemImage.Abi') != 'x86_64' or fields.get('SystemImage.TagId') != 'google_apis'):
        raise ValueError('API34 revision14 required')
    report = dict(environment='github_intel_macos', requestedGpu='swangle', avd=name, serial=serial,
                  driverCommit=os.environ['GITHUB_SHA'], bootCompleted=False, recordingCompleted=False,
                  qualityApproved=False, baselineApproved=False, releaseApproved=False,
                  scope='Separate platform observation; does not approve the existing Linux-only comparator')
    process = None
    streams = []

    def adb(*args, timeout=30):
        return subprocess.check_output([str(adb_bin), '-s', serial, *args], env=env, timeout=timeout)

    def capture(prefix):
        errors = []
        for suffix, args in [('logcat.txt', ('logcat', '-d', '-v', 'threadtime')),
                             ('crashes.txt', ('logcat', '-d', '-b', 'crash')),
                             ('anr.txt', ('logcat', '-d', '-b', 'events', '-v', 'threadtime', '-s', 'am_anr')),
                             ('properties.txt', ('shell', 'getprop')),
                             ('surfaceflinger.txt', ('shell', 'dumpsys', 'SurfaceFlinger'))]:
            try:
                (out / (prefix + '-' + suffix)).write_bytes(adb(*args))
            except (OSError, subprocess.SubprocessError) as error:
                errors.append(suffix + ': ' + str(error))
        if errors:
            raise ValueError('Evidence collection failed: ' + '; '.join(errors))

    health = module('macos_health', 'verify-emulator-boot.py')
    try:
        with (out / 'create-avd.txt').open('wb') as log:
            subprocess.run([str(sdk / 'cmdline-tools/23.0/bin/avdmanager'), 'create', 'avd', '--name', name,
                            '--package', 'system-images;android-34;google_apis;x86_64', '--device', 'pixel_8',
                            '--path', str(avd_home / (name + '.avd')), '--abi', 'x86_64'],
                           input=b'no\n', stdout=log, stderr=subprocess.STDOUT, env=env, check=True, timeout=120)
        config = avd_home / (name + '.avd') / 'config.ini'
        values = {'hw.lcd.width': '1080', 'hw.lcd.height': '2400', 'hw.lcd.density': '420',
                  'hw.cpu.ncore': '4', 'hw.ramSize': '4096', 'hw.keyboard': 'yes', 'vm.heapSize': '256'}
        lines = [line for line in config.read_text().splitlines() if line.partition('=')[0].strip() not in values]
        config.write_text('\n'.join(lines + [f'{key}={value}' for key, value in values.items()]) + '\n')
        (out / 'emulator-config.ini').write_bytes(config.read_bytes())
        command = [str(binary), '-avd', name, '-sysdir', str(image), '-port', '5554', '-no-window',
                   '-gpu', 'swangle', '-memory', '4096', '-cores', '4', '-accel', 'on',
                   '-timezone', 'Etc/UTC', '-no-snapshot', '-noaudio', '-no-boot-anim']
        (out / 'launch-command.json').write_text(json.dumps(command, indent=2) + '\n')
        with (out / 'emulator-stdout.txt').open('wb') as stdout, (out / 'emulator-stderr.txt').open('wb') as stderr:
            process = subprocess.Popen(command, stdout=stdout, stderr=stderr, env=env, start_new_session=True)
        report['ownedProcessGroup'] = process.pid
        started = time.monotonic()
        observations = []
        while time.monotonic() - started < 300:
            try:
                os.killpg(process.pid, 0)
            except ProcessLookupError:
                raise ValueError('Owned emulator process group exited during boot')
            try:
                boot = adb('shell', 'getprop', 'sys.boot_completed', timeout=10).strip().decode()
                observation = dict(seconds=time.monotonic() - started, boot=boot)
            except (OSError, subprocess.SubprocessError) as error:
                observation = dict(seconds=time.monotonic() - started, error=str(error))
            observations.append(observation)
            (out / 'boot-observations.json').write_text(json.dumps(observations, indent=2) + '\n')
            if observation.get('boot') == '1':
                report['bootCompleted'] = True
                break
            time.sleep(2)
        else:
            raise ValueError('Initial 300-second boot observation expired; no tests started')
        if adb('emu', 'avd', 'name').decode().splitlines()[0].strip() != name:
            raise ValueError('Owned AVD identity mismatch')
        if adb('shell', 'getprop', 'ro.build.fingerprint').decode().strip() != 'google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys':
            raise ValueError('Frozen system image fingerprint mismatch')
        for label, args in [('anr', ['-b', 'events', '-v', 'threadtime', '-s', 'am_anr']),
                            ('crashes', ['-b', 'crash', '-v', 'threadtime'])]:
            stdout = (out / ('continuous-' + label + '.txt')).open('wb')
            stderr = (out / ('continuous-' + label + '-stderr.txt')).open('wb')
            try:
                reader = subprocess.Popen([str(adb_bin), '-s', serial, 'logcat', *args],
                                          stdout=stdout, stderr=stderr, env=env)
            except OSError:
                stdout.close(); stderr.close()
                raise
            streams.append((label, reader, stdout, stderr))
        for args in [('svc', 'power', 'stayon', 'true'), ('settings', 'put', 'system', 'screen_off_timeout', '2147483647'),
                     ('wm', 'dismiss-keyguard'), ('settings', 'put', 'global', 'window_animation_scale', '0.0'),
                     ('settings', 'put', 'global', 'transition_animation_scale', '0.0'),
                     ('settings', 'put', 'global', 'animator_duration_scale', '0.0'),
                     ('setprop', 'debug.hwui.renderer', 'skiagl'), ('settings', 'put', 'system', 'font_scale', '1.0'),
                     ('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1'),
                     ('settings', 'put', 'secure', 'stylus_handwriting_enabled', '0')]:
            adb('shell', *args)
        window = module('macos_window', 'verify-emulator-window.py')
        window.wait_for_focus(lambda remaining: adb('shell', 'dumpsys', 'window', 'displays', timeout=min(10, remaining)).decode(),
                              out / 'initial-focus')
        capture('boot')
        health.verify((out / 'boot-anr.txt').read_text(errors='replace'),
                      (out / 'boot-crashes.txt').read_text(errors='replace'))
        report['actualRenderer'] = require_renderer((out / 'boot-surfaceflinger.txt').read_text(errors='replace'))
        if any(reader.poll() is not None for _, reader, _, _ in streams):
            raise ValueError('Continuous health collection exited before instrumentation')
        report['recordingStarted'] = True
        subprocess.run(['python3', '.github/scripts/capture-screen-review.py',
                        '--pair-root', os.environ['REVIEW_PAIR_ROOT'],
                        '--output', 'app/build/reports/screen-review/intel-macos'], env=env, check=True)
        report['recordingCompleted'] = True
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        report['error'] = str(error)
    finally:
        if process is not None:
            try:
                capture('final')
                health.verify((out / 'final-anr.txt').read_text(errors='replace'),
                              (out / 'final-crashes.txt').read_text(errors='replace'), phase='runtime')
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                report['finalHealthError'] = str(error)
            try:
                stop_health_streams(streams, out, report, health)
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                report.setdefault('streamErrors', []).append('Collector cleanup failed: ' + str(error))
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                report['shutdownError'] = 'Owned launcher did not exit after SIGTERM; no other process was targeted'
        (out / 'controller-result.json').write_text(json.dumps(report, indent=2) + '\n')
    if (not report['recordingCompleted'] or report.get('error') or report.get('finalHealthError') or
            report.get('shutdownError') or report.get('streamErrors') or report.get('continuousHealthError')):
        raise SystemExit('Intel macOS observation failed; evidence retained, no approval')


if __name__ == '__main__':
    main()
