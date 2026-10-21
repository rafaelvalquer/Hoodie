"""Reject recorded ANRs/crashes, identifying the phase actually observed."""
import argparse
import re
import sys
from pathlib import Path


def verify(events: str, crashes: str, *, phase: str = 'boot') -> None:
    if phase not in ('boot', 'runtime'):
        raise ValueError('Unknown observation phase')
    context = 'during boot; no tests started' if phase == 'boot' else 'during runtime observation; collected test evidence must be inspected'
    # Match Android log records, not command echoes containing these words.
    if re.search(r'\bam_anr\s*:', events):
        raise ValueError(f'Android recorded an ANR {context}')
    if re.search(r'\b(?:F|E)\s+[^\r\n]*?:\s*(?:\*{3}\s+)?FATAL EXCEPTION\b', crashes):
        raise ValueError(f'Android recorded a fatal Java exception {context}')
    if re.search(r'\bF\s+libc\s*:\s*Fatal signal\b', crashes):
        raise ValueError(f'Android recorded a fatal native signal {context}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('events', type=Path)
    parser.add_argument('crashes', type=Path)
    parser.add_argument('--phase', choices=('boot', 'runtime'), default='boot')
    args = parser.parse_args()
    try:
        verify(*(p.read_text(encoding='utf-8', errors='replace') for p in (args.events, args.crashes)), phase=args.phase)
    except ValueError as error:
        print(f'::error::{error}', file=sys.stderr)
        raise SystemExit(1)
    print('Boot snapshot contains no recorded ANR or fatal crash; runtime tests still required.' if args.phase == 'boot'
          else 'Runtime snapshot contains no recorded ANR or fatal crash; this alone does not approve release.')
