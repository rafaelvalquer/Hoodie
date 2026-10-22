import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('verify_release_ci', Path(__file__).with_name('verify-release-ci.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ReleaseCiGateTest(unittest.TestCase):
    def setUp(self):
        self.sha = 'a' * 40
        self.repo = 'owner/hoodie'
        self.run = dict(id=20, run_attempt=1, head_sha=self.sha, path=gate.WORKFLOW,
                        event='push', repository={'full_name': self.repo},
                        head_repository={'full_name': self.repo}, status='completed', conclusion='success')
        self.runs = [{'workflow_runs': [copy.deepcopy(self.run)]}]
        self.jobs = [{'jobs': [dict(id=i, run_id=20, run_attempt=1, head_sha=self.sha,
                                   name=name, status='completed', conclusion='success')
                               for i, name in enumerate(gate.REQUIRED, 1)]}]

    def verify(self):
        return gate.verify(self.runs, self.run, self.jobs, self.sha, self.repo)

    def test_all_four_jobs_approve_quality_without_approving_release(self):
        result = self.verify()
        self.assertEqual(set(gate.REQUIRED), set(result['requiredJobs']))
        self.assertTrue(result['qualityApproved'])
        self.assertFalse(result['releaseApproved'])

    def test_each_mandatory_job_must_be_present(self):
        original = copy.deepcopy(self.jobs)
        for name in gate.REQUIRED:
            with self.subTest(name=name):
                self.jobs = [{'jobs': [j for j in original[0]['jobs'] if j['name'] != name]}]
                with self.assertRaises(ValueError): self.verify()

    def test_green_workflow_cannot_hide_skipped_or_failed_jobs(self):
        for name in gate.REQUIRED:
            for conclusion in ('skipped', 'failure', 'cancelled', 'neutral', 'timed_out', None):
                with self.subTest(name=name, conclusion=conclusion):
                    jobs = copy.deepcopy(self.jobs)
                    next(j for j in jobs[0]['jobs'] if j['name'] == name)['conclusion'] = conclusion
                    with self.assertRaises(ValueError):
                        gate.verify(self.runs, self.run, jobs, self.sha, self.repo)

    def test_job_must_match_run_attempt_commit_and_terminal_state(self):
        for key, value in [('run_id', 19), ('run_attempt', 2), ('head_sha', 'b' * 40), ('status', 'in_progress')]:
            with self.subTest(key=key):
                jobs = copy.deepcopy(self.jobs)
                jobs[0]['jobs'][0][key] = value
                with self.assertRaises(ValueError):
                    gate.verify(self.runs, self.run, jobs, self.sha, self.repo)

    def test_duplicate_ids_or_required_names_are_rejected(self):
        for duplicate_id in (True, False):
            with self.subTest(duplicate_id=duplicate_id):
                jobs = copy.deepcopy(self.jobs)
                duplicate = copy.deepcopy(jobs[0]['jobs'][0])
                if not duplicate_id: duplicate['id'] = 99
                jobs[0]['jobs'].append(duplicate)
                with self.assertRaises(ValueError):
                    gate.verify(self.runs, self.run, jobs, self.sha, self.repo)

    def test_latest_run_cannot_fall_back_to_an_older_green_run(self):
        for status, conclusion in [('in_progress', None), ('queued', None), ('completed', 'failure')]:
            with self.subTest(status=status, conclusion=conclusion):
                newer = dict(self.run, id=21, status=status, conclusion=conclusion)
                with self.assertRaises(ValueError):
                    gate.verify(self.runs + [{'workflow_runs': [newer]}], self.run,
                                self.jobs, self.sha, self.repo)

    def test_wrong_workflow_repository_and_event_are_rejected(self):
        mutations = [('path', 'another.yml'), ('repository', {'full_name': 'other/repo'}),
                     ('head_repository', {'full_name': 'fork/repo'}), ('event', 'pull_request'),
                     ('event', 'pull_request_target'), ('head_sha', 'b' * 40)]
        for key, value in mutations:
            with self.subTest(key=key, value=value):
                run = dict(self.run, **{key: value})
                with self.assertRaises(ValueError):
                    gate.verify(self.runs, run, self.jobs, self.sha, self.repo)

    def test_refetched_run_cannot_change_attempt(self):
        self.run['run_attempt'] = 2
        with self.assertRaises(ValueError): self.verify()

    def test_paginated_jobs_and_manual_ci_dispatch_are_supported(self):
        self.run['event'] = 'workflow_dispatch'
        self.runs = {'workflow_runs': [self.run]}
        self.jobs = [{'jobs': self.jobs[0]['jobs'][:2]}, {'jobs': self.jobs[0]['jobs'][2:]}]
        self.assertTrue(self.verify()['qualityApproved'])

    def test_pull_request_cannot_supply_release_quality_evidence(self):
        self.runs = [{'workflow_runs': [dict(self.run, event='pull_request')]}]
        with self.assertRaises(ValueError): self.verify()

    def cli(self, directory, *extra):
        for name, data in [('runs', self.runs), ('run', self.run), ('jobs', self.jobs)]:
            (directory / f'{name}.json').write_text(json.dumps(data))
        return subprocess.run([sys.executable, str(Path(gate.__file__)), '--runs',
                               str(directory / 'runs.json'), '--sha', self.sha,
                               '--repository', self.repo, *extra], capture_output=True, text=True)

    def test_cli_selects_run_and_writes_quality_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            selection = self.cli(directory)
            self.assertEqual(0, selection.returncode, selection.stderr)
            self.assertEqual('20', selection.stdout.strip())
            result = self.cli(directory, '--run', str(directory / 'run.json'),
                              '--jobs', str(directory / 'jobs.json'), '--output', str(directory / 'quality.json'))
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(self.verify(), json.loads((directory / 'quality.json').read_text()))

    def test_cli_rejects_green_workflow_with_skipped_instrumentation(self):
        self.jobs[0]['jobs'][-1]['conclusion'] = 'skipped'
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            result = self.cli(directory, '--run', str(directory / 'run.json'),
                              '--jobs', str(directory / 'jobs.json'), '--output', str(directory / 'quality.json'))
            self.assertNotEqual(0, result.returncode)
            self.assertFalse((directory / 'quality.json').exists())


if __name__ == '__main__':
    unittest.main()
