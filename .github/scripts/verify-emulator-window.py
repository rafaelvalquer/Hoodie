"""Require a focused Android window before instrumentation; read a complete snapshot."""
import re
import sys
from pathlib import Path


def verify(snapshot, package=None):
    # Use `dumpsys window displays`, which excludes historical last-ANR windows.
    focused = re.findall(r'^\s*mCurrentFocus=(.*)$', snapshot, re.MULTILINE)
    if not focused or any(value.strip() == 'null' for value in focused):
        raise ValueError('Android has no focused window')
    if any('Application Not Responding' in value for value in focused):
        raise ValueError('Android ANR dialog owns window focus')
    if package is not None and any(f' {package}/' not in value for value in focused):
        raise ValueError(f'Expected focused package {package}')


if __name__ == '__main__':
    try:
        verify(Path(sys.argv[1]).read_text(encoding='utf-8'),
               sys.argv[2] if len(sys.argv) > 2 else None)
    except (OSError, ValueError) as error:
        print(f'::error::{error}. See environment evidence.', file=sys.stderr)
        sys.exit(1)
