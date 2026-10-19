#!/usr/bin/env bash
set -euo pipefail
set +x
: "${HOODIE_UPLOAD_STORE_FILE:?Upload keystore required}"
: "${HOODIE_UPLOAD_STORE_PASSWORD:?Keystore password required}"
: "${HOODIE_UPLOAD_KEY_ALIAS:?Key alias required}"
: "${HOODIE_UPLOAD_KEY_PASSWORD:?Key password required}"
: "${HOODIE_UPLOAD_CERT_SHA256:?Expected public upload certificate fingerprint required}"
: "${BUNDLETOOL_JAR:?Verified bundletool required}"
: "${VERSION_CODE:?Version code required}"
: "${VERSION_NAME:?Version name required}"
: "${PLAY_LAST_VERSION_CODE:?Record the highest code already submitted to any Play track (0 for none)}"
[[ "$VERSION_CODE" =~ ^[1-9][0-9]{0,9}$ ]]
[[ "$PLAY_LAST_VERSION_CODE" =~ ^(0|[1-9][0-9]{0,9})$ ]]
repository_code=$(sed -n 's/^HOODIE_VERSION_CODE=//p' gradle.properties | tr -d '\r')
[[ "$repository_code" =~ ^[1-9][0-9]*$ ]]
(( VERSION_CODE > repository_code && VERSION_CODE > PLAY_LAST_VERSION_CODE && VERSION_CODE <= 2100000000 ))
[[ "$VERSION_NAME" =~ ^1\.[0-9]+\.[0-9]+(-rc[1-9][0-9]*)?$ ]]
out=app/build/reports/release
mkdir -p "$out"
# Never expose passwords in argv or logs. Only the public certificate is exported.
keytool -exportcert -keystore "$HOODIE_UPLOAD_STORE_FILE" \
  -storepass:env HOODIE_UPLOAD_STORE_PASSWORD -alias "$HOODIE_UPLOAD_KEY_ALIAS" \
  -file "$out/upload-certificate.der" >/dev/null
actual=$(sha256sum "$out/upload-certificate.der" | cut -d' ' -f1)
expected=$(printf '%s' "$HOODIE_UPLOAD_CERT_SHA256" | tr -d ':' | tr '[:upper:]' '[:lower:]')
test "$actual" = "$expected"
./gradlew :app:lintRelease :app:assembleRelease :app:bundleRelease \
  -PHOODIE_VERSION_CODE="$VERSION_CODE" -PHOODIE_VERSION_NAME="$VERSION_NAME"
aab=app/build/outputs/bundle/release/app-release.aab
java -jar "$BUNDLETOOL_JAR" validate --bundle="$aab"
# jarsigner verifies both integrity and trust in the selected upload certificate.
jarsigner -verify -strict -keystore "$HOODIE_UPLOAD_STORE_FILE" \
  -storepass:env HOODIE_UPLOAD_STORE_PASSWORD "$aab" > "$out/signature.txt"
# bundletool 1.18.2 accepts only pass: or file:, unlike apksigner/keytool.
# Keep passwords out of argv and uploaded artifacts; the caller's keystore
# cleanup remains in its separate shell. No recursive cleanup is necessary.
umask 077
password_dir=$(mktemp -d "${RUNNER_TEMP:?Temporary signing directory required}/hoodie-bundletool.XXXXXX")
store_password_file="$password_dir/store-password"
key_password_file="$password_dir/key-password"
trap 'rm -f -- "$store_password_file" "$key_password_file"; rmdir -- "$password_dir"' EXIT
printf '%s' "$HOODIE_UPLOAD_STORE_PASSWORD" > "$store_password_file"
printf '%s' "$HOODIE_UPLOAD_KEY_PASSWORD" > "$key_password_file"
java -jar "$BUNDLETOOL_JAR" build-apks --bundle="$aab" --output="$out/hoodie.apks" \
  --mode=universal --ks="$HOODIE_UPLOAD_STORE_FILE" --ks-key-alias="$HOODIE_UPLOAD_KEY_ALIAS" \
  --ks-pass="file:$store_password_file" --key-pass="file:$key_password_file"
unzip -p "$out/hoodie.apks" universal.apk > "$out/hoodie-from-bundle.apk"
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)
"$apksigner" verify --verbose --print-certs "$out/hoodie-from-bundle.apk" > "$out/apk-signature.txt"
grep -qi "$actual" <(tr -d ':' < "$out/apk-signature.txt")
bash .github/scripts/verify-native-16kb.sh "$aab" "$out/hoodie-from-bundle.apk"
export RELEASE_CERT_SHA256=$actual
python3 - <<'PY'
import hashlib, json, os, pathlib, re, subprocess
p = pathlib.Path('app/build/outputs/bundle/release/app-release.aab')
config = pathlib.Path('app/build/generated/source/buildConfig/release/com/hoodie/app/BuildConfig.java').read_text()
backup_flag = re.search(r'LOCAL_ENCRYPTED_BACKUP_V1\s*=\s*(true|false)\s*;', config)
assert backup_flag, 'Generated backup feature flag missing'
report = dict(commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
              versionName=os.environ['VERSION_NAME'], versionCode=int(os.environ['VERSION_CODE']),
              uploadCertificateSha256=os.environ['RELEASE_CERT_SHA256'],
              aabSha256=hashlib.sha256(p.read_bytes()).hexdigest(),
              backupPublicEnabled=backup_flag.group(1) == 'true',
              passed=['lintRelease', 'assembleRelease', 'bundleRelease', 'certificateFingerprint',
                      'bundletoolValidate', 'jarsignerStrict', 'apksigner', 'native16kbStatic'],
              playApproval='not_submitted_by_this_workflow', physicalHomologation='external_gate_required')
pathlib.Path('app/build/reports/release/build.json').write_text(json.dumps(report,indent=2)+'\n')
PY
