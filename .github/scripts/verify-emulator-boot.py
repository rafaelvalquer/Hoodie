"""Reject recorded boot ANRs/crashes before installing or testing an APK."""
import re
import sys
from pathlib import Path


def verify(events: str, crashes: str) -> None:
    # Match Android log records, not command echoes containing these words.
    if re.search(r'\bam_anr\s*:', events):
        raise ValueError('Android recorded an ANR during boot; no tests started')
    if re.search(r'\b(?:F|E)\s+[^\r\n]*?:\s*(?:\*{3}\s+)?FATAL EXCEPTION\b', crashes):
        raise ValueError('Android recorded a fatal Java exception during boot; no tests started')
    if re.search(r'\bF\s+libc\s*:\s*Fatal signal\b', crashes):
        raise ValueError('Android recorded a fatal native signal during boot; no tests started')


if __name__ == '__main__':
    try:
        verify(*(Path(p).read_text(encoding='utf-8', errors='replace') for p in sys.argv[1:3]))
    except ValueError as error:
        print(f'::error::{error}', file=sys.stderr)
        raise SystemExit(1)
    print('Boot snapshot contains no recorded ANR or fatal crash; runtime tests still required.')
