"""Compare complete frozen ANGLE recordings across CPU vendors; never approves baselines."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
from PIL import Image, ImageChops

spec = importlib.util.spec_from_file_location('capture_review', Path(__file__).with_name('capture-screen-review.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_capture(root, label, driver):
    folder = root/label
    result = json.loads((folder/'result.json').read_text(encoding='utf-8'))
    health = json.loads((folder/'health.json').read_text(encoding='utf-8'))
    require(result['sourceCommit'] == capture.SOURCE and result['sourceTree'] == capture.SOURCE_TREE, 'Frozen source mismatch')
    require(result['apkSha256'] == list(capture.HASHES), 'Frozen APK mismatch')
    require(result['driverCommit'] == driver, 'Driver commit mismatch')
    require(result['completed'] is True and result['cases'] == 180 and result['adbExit'] == 0, 'Incomplete recording')
    require(result['recordingOnly'] is True and result['comparisonPerformed'] is False and result['releaseApproved'] is False, 'Unexpected recording scope')
    require(health['recordingCompleted'] is True and all(isinstance(health[k],list) and not health[k] for k in ('androidFailures','captureErrors','cleanupErrors')), 'Unhealthy recording')
    for name, pattern in [('crashes.txt', rb'Fatal signal|FATAL EXCEPTION'), ('anr.txt', rb'am_anr\s*:')]:
        require(not re.search(pattern, (folder/name).read_bytes()), 'Android failure in '+name)
    require(not re.search(rb'Fatal signal|FATAL EXCEPTION', (root/'emulator/emulator-final-crashes.txt').read_bytes()), 'Android failure after recording cleanup')
    require(not re.search(rb'am_anr\s*:|\bANR in ', (root/'emulator/emulator-final-logcat.txt').read_bytes()), 'Android ANR in final emulator log')
    order = capture.verify_runner((folder/'instrumentation.txt').read_text(encoding='utf-8'))
    require(result['caseOrder'] == order, 'Case order/provenance mismatch')
    require(result['requestedGpu'] == 'swangle', 'Unexpected requested renderer')
    renderer = capture.verify_renderer((folder/'surfaceflinger.txt').read_text(encoding='utf-8'), 'swangle')
    require(renderer == result['rendererDescription'] and 'SwiftShader driver-5.0.0' in renderer, 'Observed renderer mismatch')
    cpu = (root/'host-cpu.txt').read_text(encoding='utf-8')
    vendors = re.findall(r'^Vendor ID:\s+(\S+)\s*$', cpu, re.M)
    models = re.findall(r'^Model name:\s+(.+)$', cpu, re.M)
    require(len(vendors) == len(models) == 1 and vendors[0] in ('AuthenticAMD','GenuineIntel'), 'Missing or ambiguous CPU identity')
    expected = capture.expected_images()
    entries = result['images']
    require(len(entries) == len(expected) == 222 and {e['file'] for e in entries} == set(expected), 'Image inventory mismatch')
    files = list((folder/'images').rglob('*.png'))
    require(len(files) == 222 and {p.name for p in files} == set(expected), 'Retained image inventory mismatch')
    records = {}
    for entry in entries:
        name = entry['file']
        path = folder/'images'/name
        require(path.is_file() and not path.is_symlink(), 'Missing or unsafe image')
        require(hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], 'Image hash mismatch: '+name)
        require(entry['dimensions'] == list(expected[name]), 'Recorded viewport mismatch: '+name)
        with Image.open(path) as image:
            require(image.format == 'PNG' and image.size == expected[name], 'Decoded viewport mismatch: '+name)
        records[name] = path
    return dict(vendor=vendors[0], model=models[0].strip(), renderer=renderer, order=order, images=records)


def compare(first_root, second_root, driver):
    require(re.fullmatch(r'[0-9a-f]{40}', driver), 'Exact driver SHA required')
    first = load_capture(first_root, 'capture-a', driver)
    second = load_capture(second_root, 'capture-b', driver)
    require(first['renderer'] == second['renderer'], 'Different effective renderers')
    require(first['order'] == second['order'], 'Different case execution order')
    images = []
    for name in sorted(first['images']):
        with Image.open(first['images'][name]) as a, Image.open(second['images'][name]) as b:
            a, b = a.convert('RGBA'), b.convert('RGBA')
            require(a.getchannel('A').getextrema() == b.getchannel('A').getextrema() == (255,255), 'Nonopaque capture: '+name)
            a_bytes, b_bytes = a.tobytes(), b.tobytes()
            changed = maximum = 0
            if a_bytes != b_bytes:
                # Alpha is independently required opaque. RGB avoids an all-zero
                # difference alpha channel masking visible differences in getbbox.
                difference = ImageChops.difference(a,b).convert('RGB')
                red, green, blue = difference.split()
                largest = ImageChops.lighter(ImageChops.lighter(red,green),blue)
                changed = largest.width*largest.height - largest.histogram()[0]
                maximum = largest.getextrema()[1]
            images.append(dict(file=name, firstDecodedSha256=hashlib.sha256(a_bytes).hexdigest(),
                secondDecodedSha256=hashlib.sha256(b_bytes).hexdigest(), changedPixels=changed, maxChannelDelta=maximum))
    cross_vendor = first['vendor'] != second['vendor']
    identical = all(item['changedPixels'] == 0 for item in images)
    verdict = 'identical_across_cpu_vendors' if cross_vendor and identical else 'different_pixels' if cross_vendor else 'inconclusive_same_cpu_vendor'
    return dict(conclusion=verdict, crossVendorComparisonPassed=cross_vendor and identical,
        driverCommit=driver,sourceCommit=capture.SOURCE,sourceTree=capture.SOURCE_TREE,apkSha256=list(capture.HASHES),
        hosts=[{k:host[k] for k in ('vendor','model','renderer')} for host in (first,second)],
        casesPerHost=180,imagesPerHost=222,decodedPixelsIdentical=identical,
        changedImages=sum(item['changedPixels'] != 0 for item in images),images=images,
        qualityGateApproved=False,baselineApproval=False,releaseApproved=False,
        scope='Exact decoded PNG comparison of complete healthy frozen APK recordings. Same-vendor hosts are inconclusive. No visual-design, current-source, baseline or release approval; no automatic retries.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--first',type=Path,required=True)
    parser.add_argument('--second',type=Path,required=True)
    parser.add_argument('--driver-commit',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'New comparison output required')
    try:
        result = compare(args.first,args.second,args.driver_commit)
    except Exception as error:
        result = dict(conclusion='invalid_evidence',crossVendorComparisonPassed=False,error=f'{type(error).__name__}: {error}',
            qualityGateApproved=False,baselineApproval=False,releaseApproved=False)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('x',encoding='utf-8') as stream:
        json.dump(result,stream,indent=2)
        stream.write('\n')
    print(result['conclusion'])
    raise SystemExit(0 if result['crossVendorComparisonPassed'] else 1)


if __name__ == '__main__': main()
