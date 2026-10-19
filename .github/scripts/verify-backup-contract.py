#!/usr/bin/env python3
"""Preserve the first V1 archive and require an explicit compatibility review on schema PRs."""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--base')
args = parser.parse_args()
expected = 'c0d7739652d6b5e1a8e1c6df1b7cf592342db6d334c6c1b6d16fa016b43e20a7'
for name in ('app/src/test/resources/backup-v1-minimal.hoodiebackup',
             'app/src/androidTest/assets/backup-v1-minimal.hoodiebackup'):
    if hashlib.sha256(Path(name).read_bytes()).hexdigest() != expected:
        raise SystemExit(f'Permanent first-version fixture changed: {name}. Preserve V1; add a new fixture instead.')
print('Original V1 fixture preserved in both test suites.')
if args.base:
    if not re.fullmatch('[0-9a-fA-F]{40}', args.base):
        raise SystemExit('Expected a full base commit SHA')
    changed = subprocess.check_output(['git', 'diff', '--name-only', args.base, 'HEAD'], text=True).splitlines()
    protected = ('app/src/main/java/com/hoodie/app/core/database/',
                 'app/src/main/java/com/hoodie/app/core/datastore/',
                 'app/src/main/java/com/hoodie/app/engine/backup/', 'app/schemas/')
    requires_review = any(name.startswith(protected) for name in changed)
    reviewed = any(name == 'docs/backup/FORMAT-V1.md' or
                   name.startswith('docs/backup/contract-reviews/') and name.endswith('.md') for name in changed)
    if requires_review and not reviewed:
        raise SystemExit('Room/DataStore/backup changed. Document fields, V1 compatibility, adapters and fixture-import evidence in docs/backup/contract-reviews/<change>.md (or FORMAT-V1.md).')
