#!/usr/bin/env python3
"""Quality gate: fail on missing reports, zero executed tests, failures or skips."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

reports = list(Path(sys.argv[1]).rglob("TEST-*.xml"))
totals = dict(tests=0, failures=0, errors=0, skipped=0)
actual = dict(tests=0, failures=0, errors=0, skipped=0)
skipped = []
for report in reports:
    root = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(root.get(key, 0))
    for case in root.iter("testcase"):
        actual['tests'] += 1
        actual['failures'] += case.find('failure') is not None
        actual['errors'] += case.find('error') is not None
        actual['skipped'] += case.find('skipped') is not None
        if case.find("skipped") is not None:
            skipped.append(f"{case.get('classname')}#{case.get('name')}")
print(json.dumps(dict(suites=len(reports), **actual, reportedTotals=totals, skippedTests=skipped), indent=2))
if not reports or not actual['tests'] or actual != totals or any(actual[key] for key in ('failures', 'errors', 'skipped')):
    sys.exit(1)
