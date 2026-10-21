#!/usr/bin/env bash
# Exercise the real path/ownership guards, stopping at a stubbed network boundary.
set -euo pipefail
root=$(pwd)
script="$root/.github/scripts/install-qa-emulator.sh"
mkdir -p "$root/app/build/reports"
fixture=$(mktemp -d "$root/app/build/reports/emulator-install-guard.XXXXXX")
mkdir "$fixture/bin"
cat > "$fixture/bin/curl" <<'STUB'
#!/usr/bin/env bash
printf 'download reached\n' > "$RUNNER_TEMP/download-attempt.txt"
exit 71
STUB
chmod +x "$fixture/bin/curl"

check() {
  local out=$1 expected=$2 occupied=${3:-false} trial status=0
  trial=$(mktemp -d "$fixture/case.XXXXXX")
  mkdir "$trial/temp" "$trial/work"
  if [[ "$occupied" = true ]]; then mkdir "$trial/temp/hoodie-emulator-15917651"; fi
  (
    cd "$trial/work"
    RUNNER_TEMP="$trial/temp" GITHUB_ENV="$trial/github-env" PATH="$fixture/bin:$PATH" bash "$script" "$out"
  ) > "$trial/output.txt" 2>&1 || status=$?
  test "$status" -eq "$expected"
  if [[ "$expected" = 71 ]]; then
    test -f "$trial/temp/download-attempt.txt"
  else
    test ! -e "$trial/temp/download-attempt.txt"
  fi
}

for out in \
  app/build/instrumentation-evidence/emulator-install \
  app/build/reports/r8-qa/emulator-install \
  app/build/reports/r8-upgrade-qa/emulator-install \
  app/build/reports/native-runtime/emulator-install \
  app/build/reports/exact-golden-control/emulator-install \
  app/build/reports/screen-review/emulator-install \
  app/build/reports/frozen-r8-control/emulator-install; do
  check "$out" 71
done
for out in \
  app/build/reports/r8-upgrade-qa/emulator-install-extra \
  app/build/reports/r8-upgrade-qa/../emulator-install \
  ../emulator-install \
  /tmp/emulator-install; do
  check "$out" 1
done
check app/build/reports/r8-upgrade-qa/emulator-install 1 true
echo '12 emulator installer guard cases passed; no network or emulator executed.'
