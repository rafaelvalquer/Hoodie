"""Require a focused Android window before instrumentation; read a complete snapshot."""
import argparse
import re
import subprocess
import sys
import time
from pathlib import Path


class WindowNotReady(ValueError):
    """The window manager has not assigned focus yet; not an ANR approval."""


def verify(snapshot, package=None):
    # Use `dumpsys window displays`, which excludes historical last-ANR windows.
    focused = re.findall(r'^\s*mCurrentFocus=(.*)$', snapshot, re.MULTILINE)
    if any('Application Not Responding' in value for value in focused):
        raise ValueError('Android ANR dialog owns window focus')
    if not focused or any(value.strip() == 'null' for value in focused):
        raise WindowNotReady('Android has no focused window')
    if package is not None and any(f' {package}/' not in value for value in focused):
        raise ValueError(f'Expected focused package {package}')


def wait_for_focus(read_snapshot, output, timeout=45, clock=time.monotonic, sleep=time.sleep):
    """Wait only for initial focus assignment; a current ANR fails immediately."""
    if timeout <= 0:
        raise ValueError('Focus timeout must be positive')
    output.mkdir(parents=True, exist_ok=True)
    deadline = clock() + timeout
    attempt = 0
    while True:
        remaining = deadline - clock()
        if remaining <= 0:
            raise WindowNotReady(f'Android has no focused window after {timeout} seconds')
        snapshot = read_snapshot(remaining)
        attempt += 1
        (output / f'preflight-window-{attempt:03d}.txt').write_text(snapshot, encoding='utf-8')
        (output / 'preflight-window.txt').write_text(snapshot, encoding='utf-8')
        if clock() >= deadline:
            raise WindowNotReady(f'Focus observation exceeded {timeout} seconds')
        try:
            verify(snapshot)
        except WindowNotReady:
            remaining = deadline - clock()
            if remaining <= 0:
                raise WindowNotReady(f'Android has no focused window after {timeout} seconds')
            sleep(min(1, remaining))
        else:
            return attempt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshot', nargs='?', type=Path)
    parser.add_argument('package', nargs='?')
    parser.add_argument('--wait-for-focus', type=Path, metavar='EVIDENCE_DIR')
    args = parser.parse_args()
    try:
        if args.wait_for_focus:
            if args.snapshot or args.package:
                parser.error('--wait-for-focus cannot be combined with a snapshot or package')
            def read_snapshot(remaining):
                return subprocess.check_output(
                    ['adb', 'shell', 'dumpsys', 'window', 'displays'],
                    timeout=min(10, remaining), text=True, encoding='utf-8')
            attempts = wait_for_focus(read_snapshot, args.wait_for_focus)
            print(f'Android focused window ready after {attempts} snapshot(s)')
        elif args.snapshot:
            verify(args.snapshot.read_text(encoding='utf-8'), args.package)
        else:
            parser.error('Supply a snapshot or --wait-for-focus')
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f'::error::{error}. See environment evidence.', file=sys.stderr)
        sys.exit(1)
