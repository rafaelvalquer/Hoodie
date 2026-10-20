#!/usr/bin/env bash
# Development certificate only. This is not the protected upload/release path.
set -euo pipefail
set +x
: "${ANDROID_HOME:?Android SDK required}"
: "${BUNDLETOOL_JAR:?Verified bundletool required}"
: "${RUNNER_TEMP:?Runner temporary directory required}"
out=app/build/reports/r8-qa
mkdir -p "$out"
umask 077
private_dir=$(mktemp -d "$RUNNER_TEMP/hoodie-qa-signing.XXXXXX")
signing_metadata="$private_dir/signing.json"
password_file="$private_dir/password"
trap 'rm -f -- "$signing_metadata" "$password_file"; rmdir -- "$private_dir"' EXIT
./gradlew :app:bundleHomologacao :app:writeQaSigningMetadata \
  --init-script .github/scripts/r8-qa-signing.init.gradle \
  -PqaSigningMetadata="$signing_metadata" -PHOODIE_VERSION_CODE=7 \
  --no-configuration-cache --no-parallel --max-workers=2
./gradlew --stop
key=$(python3 - "$signing_metadata" <<'PY'
import json, pathlib, sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))['storeFile'])
PY
)
test -s "$key"
export QA_DEBUG_PASS=android
keytool -exportcert -keystore "$key" -alias androiddebugkey \
  -storepass:env QA_DEBUG_PASS -file "$out/development-certificate.der" >/dev/null
aab=app/build/outputs/bundle/homologacao/app-homologacao.aab
java -jar "$BUNDLETOOL_JAR" validate --bundle="$aab" > "$out/bundle-validation.txt"
jarsigner -verify -strict -keystore "$key" -storepass:env QA_DEBUG_PASS "$aab" > "$out/aab-signature.txt"
printf '%s' "$QA_DEBUG_PASS" > "$password_file"
java -jar "$BUNDLETOOL_JAR" build-apks --bundle="$aab" --output="$out/homologacao.apks" \
  --mode=universal --ks="$key" --ks-key-alias=androiddebugkey \
  --ks-pass="file:$password_file" --key-pass="file:$password_file"
unzip -p "$out/homologacao.apks" universal.apk > "$out/homologacao-from-aab.apk"
apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"
"$apksigner" verify --verbose --print-certs "$out/homologacao-from-aab.apk" > "$out/apk-signature.txt"
actual=$(sha256sum "$out/development-certificate.der" | cut -d' ' -f1)
grep -qi "$actual" <(tr -d ':' < "$out/apk-signature.txt")
NATIVE_REPORT_DIR="$out/native" bash .github/scripts/verify-native-16kb.sh "$aab" "$out/homologacao-from-aab.apk"
python3 - <<'PY'
import hashlib, json, pathlib, re, subprocess, zipfile
out = pathlib.Path('app/build/reports/r8-qa')
config = pathlib.Path('app/build/generated/source/buildConfig/homologacao/com/hoodie/app/BuildConfig.java').read_text(encoding='utf-8')
assert 'LOCAL_ENCRYPTED_BACKUP_V1 = true;' in config
assert 'APPLICATION_ID = "com.hoodie.app.homologacao";' in config
assert 'VERSION_CODE = 7;' in config
aab = pathlib.Path('app/build/outputs/bundle/homologacao/app-homologacao.aab')
mapping = pathlib.Path('app/build/outputs/mapping/homologacao/mapping.txt')
assert mapping.stat().st_size > 0
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
apk = out/'homologacao-from-aab.apk'
with zipfile.ZipFile(apk) as archive:
    dex = {name: hashlib.sha256(archive.read(name)).hexdigest()
           for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)}
    native = {name: hashlib.sha256(archive.read(name)).hexdigest()
              for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
    resources = hashlib.sha256(archive.read('resources.arsc')).hexdigest()
assert dex
report = dict(commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
    appSourceTree=subprocess.check_output(['git','rev-parse','HEAD:app/src'],text=True).strip(),
    versionCode=7, scope='Development signing; no upload key, Play submission, physical or production approval',
    aabSha256=sha(aab), apkSha256=sha(apk), mappingSha256=sha(mapping),
    certificateSha256=sha(out/'development-certificate.der'), apkDexSha256=dex,
    apkNativeSha256=native, resourcesArscSha256=resources,
    javaVersion=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr,
    runtimeVerified=False, fullBackupJourneyApproved=False)
(out/'build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
PY
