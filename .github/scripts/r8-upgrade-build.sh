#!/usr/bin/env bash
set -euo pipefail
set +x
test "${GITHUB_ACTIONS:-}" = true
: "${GITHUB_SHA:?Current commit required}"
: "${RUNNER_TEMP:?Private temporary directory required}"
: "${BUNDLETOOL_JAR:?Verified bundletool required}"
: "${ANDROID_HOME:?SDK required}"
driver=$(pwd)
baseline=e44c17889a1430e77a78d1854e3924f28afbe85a
test "$(git rev-parse HEAD)" = "$GITHUB_SHA"
test "$(git -C qa-baseline rev-parse HEAD)" = "$baseline"
out="$driver/app/build/reports/r8-upgrade-qa"
mkdir -p "$(dirname "$out")"
mkdir "$out"
umask 077
private_dir=$(mktemp -d "$RUNNER_TEMP/hoodie-upgrade-signing.XXXXXX")
trap 'rm -f -- "$private_dir/baseline.json" "$private_dir/current.json" "$private_dir/password"; rmdir -- "$private_dir"' EXIT
export QA_DEBUG_PASS=android
printf '%s' "$QA_DEBUG_PASS" > "$private_dir/password"
for label in baseline current; do
  if [[ "$label" == baseline ]]; then source_dir="$driver/qa-baseline"; code=6; else source_dir="$driver"; code=7; fi
  artifact="$out/$label"
  mkdir "$artifact"
  bash "$source_dir/gradlew" -p "$source_dir" :app:bundleRelease :app:writeUpgradeSigningMetadata \
    --init-script "$driver/.github/scripts/r8-upgrade-signing.init.gradle" \
    -PupgradeSigningMetadata="$private_dir/$label.json" -PHOODIE_VERSION_CODE="$code" \
    --no-configuration-cache --no-parallel --max-workers=2
  bash "$source_dir/gradlew" -p "$source_dir" --stop
  git -C "$source_dir" diff --exit-code -- app/src/main app/build.gradle.kts app/proguard-rules.pro gradle gradle.properties
  key=$(python3 - "$private_dir/$label.json" <<'PY'
import json,pathlib,sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())['storeFile'])
PY
)
  cp "$source_dir/app/build/outputs/bundle/release/app-release.aab" "$artifact/bundle.aab"
  cp "$source_dir/app/build/outputs/mapping/release/mapping.txt" "$artifact/mapping.txt"
  cp "$source_dir/app/src/main/res/values/strings.xml" "$artifact/strings.xml"
  keytool -exportcert -keystore "$key" -alias androiddebugkey -storepass:env QA_DEBUG_PASS \
    -file "$artifact/development-certificate.der" >/dev/null
  jarsigner -verify -strict -keystore "$key" -storepass:env QA_DEBUG_PASS "$artifact/bundle.aab" > "$artifact/aab-signature.txt"
  java -jar "$BUNDLETOOL_JAR" validate --bundle="$artifact/bundle.aab" > "$artifact/bundle-validation.txt"
  java -jar "$BUNDLETOOL_JAR" build-apks --bundle="$artifact/bundle.aab" --output="$artifact/package.apks" \
    --mode=universal --ks="$key" --ks-key-alias=androiddebugkey \
    --ks-pass="file:$private_dir/password" --key-pass="file:$private_dir/password"
  unzip -p "$artifact/package.apks" universal.apk > "$artifact/derived.apk"
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$artifact/derived.apk" > "$artifact/apk-signature.txt"
  cert=$(sha256sum "$artifact/development-certificate.der" | cut -d' ' -f1)
  grep -qi "$cert" <(tr -d ':' < "$artifact/apk-signature.txt")
  "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$artifact/derived.apk" > "$artifact/badging.txt"
  NATIVE_REPORT_DIR="$artifact/native" bash "$driver/.github/scripts/verify-native-16kb.sh" "$artifact/bundle.aab" "$artifact/derived.apk"
  python3 - "$source_dir" "$artifact" "$code" <<'PY'
import hashlib,json,pathlib,re,subprocess,sys,zipfile
source,out,code=pathlib.Path(sys.argv[1]),pathlib.Path(sys.argv[2]),int(sys.argv[3])
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
config=(source/'app/build/generated/source/buildConfig/release/com/hoodie/app/BuildConfig.java').read_text()
assert 'DEBUG = false;' in config and f'VERSION_CODE = {code};' in config
assert 'APPLICATION_ID = "com.hoodie.app";' in config
if code==7:assert 'LOCAL_ENCRYPTED_BACKUP_V1 = false;' in config
badging=(out/'badging.txt').read_text()
assert re.search(r"^package: name='com.hoodie.app' versionCode='"+str(code)+"'",badging,re.M)
db=(source/'app/src/main/java/com/hoodie/app/core/database/HoodieDatabase.kt').read_text()
schema=int(re.search(r'HOODIE_DATABASE_VERSION\s*=\s*(\d+)',db)[1])
assert schema==(11 if code==6 else 12)
with zipfile.ZipFile(out/'derived.apk') as z:
    dex={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.dex')}
    native={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.so')}
assert dex and len(native)==12 and (out/'mapping.txt').stat().st_size>0
report=dict(commit=subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip(),
    mainSourceTree=subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD:app/src/main'],text=True).strip(),
    package='com.hoodie.app',versionCode=code,schemaVersionFromSource=schema,
    apkSha256=sha(out/'derived.apk'),aabSha256=sha(out/'bundle.aab'),mappingSha256=sha(out/'mapping.txt'),
    certificateSha256=sha(out/'development-certificate.der'),stringsSha256=sha(out/'strings.xml'),
    apkDexSha256=dex,apkNativeSha256=native,developmentSigningOnly=True,releaseApproved=False)
(out/'build.json').write_text(json.dumps(report,indent=2)+'\n')
PY
done
cmp "$out/baseline/development-certificate.der" "$out/current/development-certificate.der"
python3 - "$out" "$GITHUB_SHA" "$baseline" <<'PY'
import json,pathlib,sys
out=pathlib.Path(sys.argv[1]);read=lambda label:json.loads((out/label/'build.json').read_text())
old,new=read('baseline'),read('current')
assert old['commit']==sys.argv[3] and new['commit']==sys.argv[2]
assert old['certificateSha256']==new['certificateSha256']
(out/'pair.json').write_text(json.dumps(dict(baseline=old,current=new,runtimeVerified=False,releaseApproved=False,
    scope='Two unmodified release codebases with R8, development signing and QA version codes 6/7. APKs derived from AABs; no upload key or Play approval.'),indent=2)+'\n')
PY
