#!/usr/bin/env bash
# Orchestration regression: no device, Gradle build or application tests run.
set -euo pipefail
root=$(pwd)
source_script=$(realpath "${1:-.github/scripts/connected-tests.sh}")
out="$root/app/build/reports/qa-boot-gate-tests"
mkdir -p "$out"
for scenario in boot-anr healthy diagnostic-word java-fatal native-fatal capture-logcat capture-events capture-crashes target-native full-native target-capture full-capture target-anr; do
  fixture_dir=$(mktemp -d "$out/$scenario.XXXXXX")
  mkdir -p "$fixture_dir/bin" "$fixture_dir/.github/scripts"
  cp "$source_script" "$fixture_dir/connected-tests.sh"
  for helper in summarize-instrumentation.py verify-junit.py verify-emulator-boot.py; do
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
    boot-anr|target-anr)
      # Actual event from run 39416243303, before the ANR dialog owned focus.
      printf '%s\n' '10-20 11:52:55.237   546 3146 I am_anr : [0,1190,com.google.android.apps.nexuslauncher,885767749,Input dispatching timed out (Application does not have a focused window)]' > "$fixture_dir/events.txt" ;;
    diagnostic-word) printf '%s\n' "10-20 11:52:57.000 100 100 D adbd: service requested exec logcat '-s' 'am_anr'" > "$fixture_dir/events.txt" ;;
    *) printf '%s\n' '--------- beginning of events' > "$fixture_dir/events.txt" ;;
  esac
  case "$scenario" in
    java-fatal)
      printf '%s\n' '10-20 23:07:28.229 1424 2495 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: ActivityManager' > "$fixture_dir/crashes.txt" ;;
    native-fatal|target-native|full-native)
      # Retained real API35 system crashes; never filter by application owner.
      cp "$root/docs/validation/release-v1/r8-upgrade-725-api35-system-crashes.txt" "$fixture_dir/crashes.txt" ;;
    diagnostic-word)
      printf '%s\n' "D adbd: grep 'FATAL EXCEPTION' 'Fatal signal'" > "$fixture_dir/crashes.txt" ;;
    *) printf '%s\n' '--------- beginning of crash' > "$fixture_dir/crashes.txt" ;;
  esac
  cat > "$fixture_dir/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -eu
phase=0
if [[ -f gradle-started.txt ]]; then phase=$(wc -l < gradle-started.txt); fi
case "$*" in
  --version) echo qa-fixture-adb ;;
  'logcat -d -v threadtime')
    if [[ "$QA_SCENARIO" == capture-logcat ]]; then echo 'log read failed' >&2; exit 47; fi ;;
  *am_anr*)
    if [[ "$QA_SCENARIO" == capture-events ]]; then echo 'events read failed' >&2; exit 47; fi
    if [[ "$QA_SCENARIO" == target-anr && "$phase" -eq 0 ]]; then
      echo '--------- beginning of events'
    else cat "$QA_EVENTS_FILE"; fi ;;
  'logcat -d -b crash'|'logcat -d -b crash -v threadtime')
    if [[ "$QA_SCENARIO" == capture-crashes ]]; then echo 'crash read failed' >&2; exit 47; fi
    if [[ "$QA_SCENARIO" == *-capture && "$phase" -ge "$QA_FAILURE_AFTER" ]]; then echo 'runtime crash read failed' >&2; exit 47; fi
    if [[ "$QA_SCENARIO" == *-native && "$phase" -lt "$QA_FAILURE_AFTER" ]]; then
      echo '--------- beginning of crash'
    else cat "$QA_CRASHES_FILE"; fi ;;
  'logcat -c') echo cleared >> logcat-cleared.txt ;;
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
    export QA_CRASHES_FILE="$fixture_dir/crashes.txt" QA_SCENARIO="$scenario"
    case "$scenario" in target-*) export QA_FAILURE_AFTER=1 ;; full-*) export QA_FAILURE_AFTER=2 ;; *) export QA_FAILURE_AFTER=99 ;; esac
    test "$(command -v adb)" = "$fixture_dir/bin/adb"
    test "$(adb --version)" = qa-fixture-adb
    bash connected-tests.sh
  ) > "$fixture_dir/run.log" 2>&1 || status=$?
  if [[ "$scenario" == target-* || "$scenario" == full-* ]]; then
    count=1; stage=targeted
    if [[ "$scenario" == full-* ]]; then count=2; stage=full; fi
    test "$status" -ne 0
    test "$(wc -l < "$fixture_dir/gradle-started.txt")" -eq "$count"
    test "$(wc -l < "$fixture_dir/logcat-cleared.txt")" -eq "$count"
    for file in logcat.txt anr-events.txt crashes.txt; do
      test -f "$fixture_dir/app/build/instrumentation-evidence/$stage/$file"
    done
    if [[ "$scenario" == *-native ]]; then
      cmp "$fixture_dir/crashes.txt" "$fixture_dir/app/build/instrumentation-evidence/$stage/crashes.txt"
    fi
  elif [[ "$scenario" != healthy && "$scenario" != diagnostic-word ]]; then
    if [[ "$status" == 0 || -e "$fixture_dir/gradle-started.txt" || -e "$fixture_dir/logcat-cleared.txt" ]]; then
      echo "Unhealthy preflight ($scenario) started Gradle, cleared evidence or passed; regression failed: $fixture_dir" >&2
      exit 1
    fi
    test -d "$fixture_dir/app/build/instrumentation-evidence/environment-preflight-failure"
    for file in preflight-logcat.txt preflight-anr-events.txt preflight-crashes.txt; do
      test -f "$fixture_dir/app/build/instrumentation-evidence/$file"
    done
    if [[ "$scenario" == native-fatal || "$scenario" == java-fatal ]]; then
      cmp "$fixture_dir/crashes.txt" "$fixture_dir/app/build/instrumentation-evidence/preflight-crashes.txt"
    fi
  else
    if [[ "$status" != 0 ]]; then cat "$fixture_dir/run.log"; exit 1; fi
    test "$(wc -l < "$fixture_dir/gradle-started.txt")" -eq 2
    test "$(wc -l < "$fixture_dir/logcat-cleared.txt")" -eq 2
  fi
done
echo '13 orchestration scenarios passed: unhealthy preflight blocks Gradle; targeted failures block full tests; full failures block approval; evidence retained, no real emulator or Gradle.'
