#!/usr/bin/env python3
"""Fail closed unless the latest trusted CI run approves every required job."""
import argparse
import json
from pathlib import Path

WORKFLOW = '.github/workflows/android-ci.yml'
REQUIRED = ('unit-tests', 'lint', 'assemble', 'instrumentation')


def pages(value):
    return value if isinstance(value, list) else [value]


def select_run(runs, sha, repository):
    candidates = [run for page in pages(runs) for run in page['workflow_runs']
                  if run['head_sha'] == sha and run['event'] in ('push', 'workflow_dispatch')]
    if not candidates:
        raise ValueError('No trusted Android CI run for the exact commit')
    run = max(candidates, key=lambda item: item['id'])
    check_run(run, sha, repository)
    return run


def check_run(run, sha, repository):
    if (run['head_sha'] != sha or run['path'] != WORKFLOW or
            run['event'] not in ('push', 'workflow_dispatch') or
            run['repository']['full_name'] != repository or
            run['head_repository']['full_name'] != repository):
        raise ValueError('CI source does not match the release candidate')
    if run['status'] != 'completed' or run['conclusion'] != 'success':
        raise ValueError('Latest trusted CI run has not completed successfully')


def verify(runs, run, jobs, sha, repository):
    selected = select_run(runs, sha, repository)
    check_run(run, sha, repository)
    if (run['id'], run['run_attempt']) != (selected['id'], selected['run_attempt']):
        raise ValueError('CI run or attempt changed during evidence collection')
    all_jobs = [job for page in pages(jobs) for job in page['jobs']]
    if len({job['id'] for job in all_jobs}) != len(all_jobs):
        raise ValueError('Duplicate job IDs in CI evidence')
    approved = {}
    for name in REQUIRED:
        matching = [job for job in all_jobs if job['name'] == name]
        if len(matching) != 1:
            raise ValueError(f'Required job missing or ambiguous: {name}')
        job = matching[0]
        if (job['run_id'] != run['id'] or job['run_attempt'] != run['run_attempt'] or
                job['head_sha'] != sha or job['status'] != 'completed' or
                job['conclusion'] != 'success'):
            raise ValueError(f'Required job not approved for this commit and attempt: {name}')
        approved[name] = job['id']
    return dict(commit=sha, repository=repository, runId=run['id'],
                runAttempt=run['run_attempt'], requiredJobs=approved,
                qualityApproved=True, releaseApproved=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', required=True, type=Path)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--run', type=Path)
    parser.add_argument('--jobs', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    runs = json.loads(args.runs.read_text())
    if args.run is None and args.jobs is None and args.output is None:
        print(select_run(runs, args.sha, args.repository)['id'])
        return
    if None in (args.run, args.jobs, args.output):
        parser.error('--run, --jobs and --output must be supplied together')
    result = verify(runs, json.loads(args.run.read_text()), json.loads(args.jobs.read_text()),
                    args.sha, args.repository)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('All four mandatory CI jobs approved for the exact release commit and attempt')


if __name__ == '__main__':
    main()
