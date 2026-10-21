"""Capture review candidates from the frozen 67bd2d7b APKs; never promotes baselines.

Two independent CI jobs use identical APKs and all 180 cases/222 images. This
is recording evidence, not a successful baseline comparison or release gate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess

SOURCE = '67bd2d7b5925b087c7beb3dd4418ce308a5752ec'
SOURCE_TREE = '377e31042687d57e8272544c6c4c62f31f49b27f'
HASHES = ('c52c0cc8784d0fc68b3b9e5010c722a15aa5d105e178d6bf7b3952ea99eb5e27',
          '5620a465bdfb5b9d6d1f87221d1267e012ff764c1ad0e114ba944322b60a03d4')
CLASS = 'com.hoodie.app.ui.golden.ScreenGoldenMatrixTest'
PACKAGES = ('com.hoodie.app', 'com.hoodie.app.test')


def expected_images():
    images = {}
    for screen in ('home','diary','digital','onboarding','settings','place_picker'):
        for state in ('normal','loading','error','empty','full'):
            for width,height in ((360,640),(360,800),(411,891)):
                for font in (100,130):
                    name = f'{screen}_{state}_{width}x{height}_f{font}'
                    images[name+'.png'] = (width,height)
                    if state == 'full':
                        images[name+'_bottom.png'] = (width,height)
                        if screen == 'diary':
                            images[name+'_map.png'] = (width,height)
    return images


def verify_runner(raw):
    expected = {'screenMatchesReviewedBaseline['+name.removesuffix('.png')+']'
                for name in expected_images() if not name.endswith(('_bottom.png','_map.png'))}
    fields, starts, finishes = {}, [], []
    for line in raw.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key,sep,value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if sep:fields[key] = value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.partition(': ')[2])
            if code == 2:
                fields = {}
                continue
            case = fields.get('test')
            if fields.get('class') != CLASS or case not in expected:
                raise ValueError('Unexpected or unidentified test event')
            if code == 1:starts.append(case)
            elif code == 0:finishes.append(case)
            else:raise ValueError('Failed, skipped or unknown test status')
            fields = {}
    if len(starts) != 180 or len(finishes) != 180 or set(starts) != expected or set(finishes) != expected:
        raise ValueError('Missing or duplicated case start/completion')
    if re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\s*$',raw,re.M) != ['-1']:
        raise ValueError('Runner did not complete normally')
    return starts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pair-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Disposable GitHub CI only')
    args.output.mkdir(parents=True, exist_ok=False)
    serial = os.environ['ANDROID_SERIAL']
    def adb(*command, timeout=30, check=True):
        return subprocess.run(['adb','-s',serial,*command],capture_output=True,timeout=timeout,check=check)
    def capture():
        for file,command in [('properties',('shell','getprop')),('surfaceflinger',('shell','dumpsys','SurfaceFlinger')),
                             ('logcat',('logcat','-d','-v','threadtime')),('crashes',('logcat','-d','-b','crash')),
                             ('anr',('logcat','-d','-b','events','-v','threadtime','-s','am_anr')),
                             ('window',('shell','dumpsys','window','displays'))]:
            try:(args.output/(file+'.txt')).write_bytes(adb(*command,check=False).stdout)
            except subprocess.SubprocessError as e:report.setdefault('captureErrors',[]).append(type(e).__name__)
    paths = (args.pair_root/'outputs/apk/debug/app-debug.apk',
             args.pair_root/'outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    report = dict(sourceCommit=SOURCE,sourceTree=SOURCE_TREE,apkSha256=list(HASHES),
        recordingOnly=True,comparisonPerformed=False,releaseApproved=False,completed=False)
    owned = []
    try:
        current_tree = subprocess.check_output(['git','rev-parse','HEAD:app/src'],text=True).strip()
        if current_tree != SOURCE_TREE:raise ValueError('Frozen candidate no longer matches app sources')
        for path,digest in zip(paths,HASHES):
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:raise ValueError('APK identity mismatch')
        if adb('emu','avd','name').stdout.decode().splitlines()[0].strip() != 'Hoodie_QA_API34':
            raise ValueError('Disposable reference AVD required')
        if adb('shell','getprop','ro.build.version.sdk').stdout.strip() != b'34':raise ValueError('API34 required')
        if adb('shell','getconf','PAGE_SIZE').stdout.strip() != b'4096':raise ValueError('4KB reference required')
        packages = set(adb('shell','pm','list','packages').stdout.decode().splitlines())
        if any('package:'+p in packages for p in PACKAGES):raise ValueError('Existing app/test data must not be replaced')
        capture()
        for package,path in zip(PACKAGES,paths):
            adb('install','-t',str(path.resolve()),timeout=120)
            owned.append(package)
        raw = args.output/'instrumentation.txt'
        with raw.open('xb') as stream:
            result = subprocess.run(['adb','-s',serial,'shell','am','instrument','-w','-r',
                '-e','class',CLASS,'-e','recordGoldens','true',
                'com.hoodie.app.test/androidx.test.runner.AndroidJUnitRunner'],stdout=stream,stderr=subprocess.STDOUT,timeout=1800)
        report['adbExit'] = result.returncode
        if result.returncode:raise ValueError('Instrumentation transport failed')
        report['caseOrder'] = verify_runner(raw.read_text(encoding='utf-8'))
        adb('pull','/sdcard/Android/data/com.hoodie.app/files/goldens/screens',str(args.output/'images'),timeout=120)
        images = list((args.output/'images').rglob('*.png'))
        expected = expected_images()
        if len(images) != 222 or {p.name for p in images} != set(expected):raise ValueError('Review image inventory mismatch')
        inventory = []
        for path in sorted(images):
            blob = path.read_bytes()
            if blob[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II',blob[16:24]) != expected[path.name]:
                raise ValueError('Invalid PNG header or viewport dimensions')
            inventory.append(dict(file=path.name,sha256=hashlib.sha256(blob).hexdigest(),dimensions=expected[path.name]))
        report.update(completed=True,cases=180,images=inventory)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        report['error'] = str(error)
    finally:
        capture()
        # These packages were proven absent and installed by this invocation only.
        for package in reversed(owned):
            try:adb('uninstall',package,timeout=60)
            except subprocess.SubprocessError as e:report.setdefault('cleanupErrors',[]).append(type(e).__name__)
        (args.output/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    if not report['completed']:raise SystemExit(1)
    failures = []
    for filename,pattern in [('crashes.txt',rb'Fatal signal|FATAL EXCEPTION'),('anr.txt',rb'am_anr\s*:')]:
        if re.search(pattern,(args.output/filename).read_bytes()):failures.append(filename)
    health = dict(androidFailures=failures,captureErrors=report.get('captureErrors',[]),
                  cleanupErrors=report.get('cleanupErrors',[]),releaseApproved=False,
                  scope='Recording health only; no baseline comparison or approval.')
    with (args.output/'health.json').open('x',encoding='utf-8') as f:
        f.write(json.dumps(health,indent=2)+'\n')
    if failures or health['captureErrors'] or health['cleanupErrors']:
        raise SystemExit('Android/evidence failure recorded; no review-run approval')
    print('180 cases/222 candidate images captured; no baseline or quality approval.')


if __name__ == '__main__':main()
