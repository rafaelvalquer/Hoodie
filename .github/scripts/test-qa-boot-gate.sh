#!/usr/bin/env bash
# Orchestration regression: no device, Gradle build or application tests run.
set -euo pipefail
root=$(pwd)
source_script=$(realpath "${1:-.github/scripts/connected-tests.sh}")
out="$root/app/build/reports/qa-boot-gate-tests"
mkdir -p "$out"
for scenario in boot-anr healthy diagnostic-word; do
  fixture_dir=$(mktemp -d "$out/$scenario.XXXXXX")
  mkdir -p "$fixture_dir/bin" "$fixture_dir/.github/scripts"
  cp "$source_script" "$fixture_dir/connected-tests.sh"
  for helper in summarize-instrumentation.py verify-junit.py; do
    cp "$root/.github/scripts/$helper" "$fixture_dir/.github/scripts/$helper"
  done
  cp "$root/.github/scripts/verify-emulator-window.py" "$fixture_dir/.github/scripts/focus-original.py"
  # Exercise the real focus CLI/parser with its OS read mocked. Native Windows
  # Python cannot execute the fake POSIX adb script, and must never find an SDK
  # adb.exe further down PATH during this isolated orchestration test.
  cat > "$fixture_dir/.github/scripts/verify-emulator-window.py" <<'FOCUS'
import runpy
import subprocess
subprocess.check_output = lambda *args, **kwargs: '  mCurrentFocus=Window{healthy u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity}'
runpy.run_path('.github/scripts/focus-original.py', run_name='__main__')
FOCUS
  case "$scenario" in
    boot-anr)
      # Actual event from run 39416243303, before the ANR dialog owned focus.
      printf '%s\n' '10-20 11:52:55.237   546 3146 I am_anr : [0,1190,com.google.android.apps.nexuslauncher,885767749,Input dispatching timed out (Application does not have a focused window)]' > "$fixture_dir/events.txt" ;;
    healthy) printf '%s\n' '--------- beginning of events' > "$fixture_dir/events.txt" ;;
    diagnostic-word) printf '%s\n' "10-20 11:52:57.000 100 100 D adbd: service requested exec logcat '-s' 'am_anr'" > "$fixture_dir/events.txt" ;;
  esac
  cat > "$fixture_dir/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -eu
case "$*" in
  --version) echo qa-fixture-adb ;;
  *am_anr*) cat "$QA_EVENTS_FILE" ;;
  *'dumpsys window'*) echo '  mCurrentFocus=Window{healthy u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity}' ;;
  *'getconf PAGE_SIZE'*) echo 4096 ;;
  *) : ;;
esac
ADB
  cat > "$fixture_dir/gradlew" <<'GRADLE'
#!/usr/bin/env bash
set -eu
echo started >> gradle-started.txt
mkdir -p app/build/outputs/androidTest-results/connected/debug
cat > app/build/outputs/androidTest-results/connected/debug/TEST-harness.xml <<'XML'
<testsuite name="orchestration-fixture" tests="1" failures="0" errors="0" skipped="0"><testcase classname="qa.orchestration" name="fixture"/></testsuite>
XML
GRADLE
  chmod +x "$fixture_dir/bin/adb" "$fixture_dir/gradlew"
  status=0
  (
    cd "$fixture_dir"
    export PATH="$fixture_dir/bin:$PATH"
    export QA_EVENTS_FILE="$fixture_dir/events.txt"
    test "$(command -v adb)" = "$fixture_dir/bin/adb"
    test "$(adb --version)" = qa-fixture-adb
    bash connected-tests.sh
  ) > "$fixture_dir/run.log" 2>&1 || status=$?
  if [[ "$scenario" == boot-anr ]]; then
    if [[ "$status" == 0 || -e "$fixture_dir/gradle-started.txt" ]]; then
      echo "Boot ANR started Gradle or passed; regression failed: $fixture_dir" >&2
      exit 1
    fi
    grep -q 'Boot ANR recorded before instrumentation' "$fixture_dir/run.log"
  else
    if [[ "$status" != 0 ]]; then cat "$fixture_dir/run.log"; exit 1; fi
    test "$(wc -l < "$fixture_dir/gradle-started.txt")" -eq 2
  fi
done
echo '3 orchestration scenarios passed: boot ANR blocks; healthy and diagnostic text permit both phases.'
