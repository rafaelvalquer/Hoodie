"""Verify two fresh app processes and report Android's actual launch states."""
import json
from pathlib import Path
import re
import sys


def summarize(output):
    process_ids = []
    launch_states = []
    for opening in (1, 2):
        if (output / f'stopped-pid-{opening}.txt').read_text(encoding='utf-8').strip():
            raise ValueError(f'Opening {opening}: app process survived force-stop')
        launch = (output / f'launch-{opening}.txt').read_text(encoding='utf-8')
        if not re.search(r'^Status: ok\s*$', launch, re.MULTILINE):
            raise ValueError(f'Opening {opening}: activity launch did not succeed')
        state = re.search(r'^LaunchState: (COLD|WARM)\s*$', launch, re.MULTILINE)
        if not state:
            raise ValueError(f'Opening {opening}: missing or unexpected launch state')
        pid = (output / f'pid-{opening}.txt').read_text(encoding='utf-8').strip()
        if not re.fullmatch(r'[1-9][0-9]*', pid):
            raise ValueError(f'Opening {opening}: expected exactly one app PID')
        process_ids.append(int(pid))
        launch_states.append(state.group(1))
    if process_ids[0] == process_ids[1]:
        raise ValueError('App process was reused between openings')
    return dict(pageSize=16384, derivedApkProcessRestart=True,
                derivedApkColdLaunch=all(state == 'COLD' for state in launch_states),
                processIds=process_ids, androidLaunchStates=launch_states,
                databaseMigrationAndBackupCoverage='separate instrumented suite; full release journey requires homologation')


if __name__ == '__main__':
    try:
        output = Path(sys.argv[1])
        result = summarize(output)
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    except (OSError, ValueError) as error:
        print(f'::error::{error}. See preserved runtime evidence.', file=sys.stderr)
        sys.exit(1)
