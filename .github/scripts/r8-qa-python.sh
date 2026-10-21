#!/usr/bin/env bash
# Test-only, isolated dependency runtime; never included in Android artifacts.
set -euo pipefail
: "${RUNNER_TEMP:?CI temporary directory required}"
: "${GITHUB_ENV:?GitHub Actions environment file required}"
case "${1:-}" in
  app/build/reports/r8-qa/python-verifier|app/build/reports/backup-verifier-python) out=$1 ;;
  *) echo 'Unsupported verifier report directory' >&2; exit 1 ;;
esac
mkdir -p "$out"
python3 -c 'import sys; assert sys.version_info >= (3, 11), "Python >=3.11 required"'
venv="$RUNNER_TEMP/hoodie-backup-qa-python"
test ! -e "$venv"
python3 -m venv "$venv"
"$venv/bin/python" -m pip install --disable-pip-version-check --require-hashes --only-binary=:all: \
  -r .github/requirements-backup-qa.txt > "$out/install.log" 2>&1
"$venv/bin/python" -m pip freeze > "$out/installed-versions.txt"
"$venv/bin/python" .github/scripts/test_synthetic_backup_auth.py > "$out/test-results.txt" 2>&1
printf 'HOODIE_BACKUP_PYTHON=%s\n' "$venv/bin/python" >> "$GITHUB_ENV"
