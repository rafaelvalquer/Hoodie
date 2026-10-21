#!/usr/bin/env bash
# SQLCipher migration evidence in a separate debug test package, on the owned AVD.
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
case "${R8_QA_API:-}" in 35|36) ;; *) exit 1 ;; esac
test "$(adb emu avd name | sed -n '1p' | tr -d '\r')" = "Hoodie_QA_API$R8_QA_API"
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$R8_QA_API"
test "$(adb shell getconf PAGE_SIZE | tr -d '\r')" = 16384
out=app/build/reports/r8-qa/runtime/database-tests
mkdir "$out"
# UTP may only manage its separate debug app; do not replace homologacao.
test -z "$(adb shell pm list packages com.hoodie.app | tr -d '\r' | grep -Ex 'package:com.hoodie.app(\.test)?' || true)"
r8_path=$(adb shell pm path com.hoodie.app.homologacao | tr -d '\r')
[[ "$r8_path" == package:* && "$r8_path" != *$'\n'* ]]
adb shell sha256sum "${r8_path#package:}" > "$out/r8-before.sha256"
status=0
./gradlew :app:connectedDebugAndroidTest -PHOODIE_VERSION_CODE=7 \
  --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8' \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hoodie.app.database.Migration1To2InstrumentedTest,com.hoodie.app.database.EncryptedDatabaseTest,com.hoodie.app.database.SqlCipherRuntimeTest || status=$?
adb logcat -d -v threadtime > "$out/logcat.txt"
adb logcat -d -b crash > "$out/crashes.txt"
adb shell sha256sum "${r8_path#package:}" > "$out/r8-after.sha256"
cmp "$out/r8-before.sha256" "$out/r8-after.sha256"
# Retain results before evaluating any failure. A failed test is never retried.
cp -R app/build/outputs/androidTest-results "$out/results"
python3 .github/scripts/verify-junit.py "$out/results" > "$out/junit-verification.json" || status=1
python3 - "$out" "$R8_QA_API" <<'PY'
import hashlib, json, pathlib, sys, xml.etree.ElementTree as ET
out = pathlib.Path(sys.argv[1])
build = json.loads(pathlib.Path('app/build/reports/r8-qa/build.json').read_text())
assert (out/'r8-before.sha256').read_text().split()[0] == build['apkSha256']
cases = [case for path in (out/'results').rglob('TEST-*.xml') for case in ET.parse(path).iter('testcase')]
classes = {case.get('classname') for case in cases}
assert classes == {'com.hoodie.app.database.'+name for name in
                   ('EncryptedDatabaseTest', 'Migration1To2InstrumentedTest', 'SqlCipherRuntimeTest')}
assert len(cases) == 13 and len({(c.get('classname'),c.get('name')) for c in cases}) == 13
assert any(c.get('name') == 'encryptedSchema11PreservesRelatedHistoryThroughMigrationAndReopen' for c in cases)
assert any(c.get('name') == 'allHistoricalVersionsReachCurrentSchemaOnDevice' for c in cases)
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
report = dict(commit=build['commit'],appSourceTree=build['appSourceTree'],api=int(sys.argv[2]),pageSize=16384,
    r8ApkUnchanged=True,r8ApkSha256=build['apkSha256'],
    debugApkSha256=sha(pathlib.Path('app/build/outputs/apk/debug/app-debug.apk')),
    testApkSha256=sha(pathlib.Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')),
    tests=len(cases),failures=sum(any(x.tag in ('failure','error') for x in c) for c in cases),
    skipped=sum(c.find('skipped') is not None for c in cases),releaseApproved=False,
    scope='Debug SQLCipher/database migration tests on the same 16KB AVD after the derived R8 APK UI/SAF journey. Does not prove R8 migration, prior-version APK upgrade, full history or physical-device acceptance.')
(out/'result.json').write_text(json.dumps(report,indent=2)+'\n')
PY
exit "$status"
