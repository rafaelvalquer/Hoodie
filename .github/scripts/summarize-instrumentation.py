#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
cases = []
for report in (root / "results").rglob("*.xml"):
    tree = ET.parse(report)
    for case in tree.iter("testcase"):
        failure = case.find("failure")
        error = case.find("error")
        problem = failure if failure is not None else error
        status = "failed" if problem is not None else "skipped" if case.find("skipped") is not None else "passed"
        item = dict(test=f"{case.get('classname')}#{case.get('name')}", status=status,
                    detail=problem.get("message", "") if problem is not None else "")
        cases.append(item)
        if status == "failed":
            # Preserve raw messages in the JSON; escape workflow command metacharacters.
            name = item['test'].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print(f"::error::Instrumented test failed: {name}")
(root / "summary.json").write_text(json.dumps(cases, indent=2) + "\n", encoding="utf-8")
print(f"Instrumented evidence: {len(cases)} cases, {sum(c['status']=='failed' for c in cases)} failed, {sum(c['status']=='skipped' for c in cases)} skipped")
