"""A complete same-vendor capture or a one-channel pixel difference cannot prove portability."""
import hashlib
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location('comparison',Path(__file__).with_name('compare-screen-review.py'))
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)
DRIVER = 'a'*40
RENDERER = 'GLES: Google (ANGLE (SwiftShader driver-5.0.0)), OpenGL ES 3.1 (ANGLE 2.1.17841)'


class PortableRecordingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parent=Path(__file__).resolve().parents[2]/'app/build/reports/screen-comparison-tests'
        parent.mkdir(parents=True,exist_ok=True)
        cls.base=Path(tempfile.mkdtemp(dir=parent))
        cls.template=cls.base/'template'
        expected=comparison.capture.expected_images()
        blobs={}
        for size in set(expected.values()):
            stream=BytesIO();Image.new('RGBA',size,(30,35,42,255)).save(stream,format='PNG');blobs[size]=stream.getvalue()
        names=[n.removesuffix('.png') for n in expected if not n.endswith(('_bottom.png','_map.png'))]
        lines=[]
        order=[]
        for name in names:
            case='screenMatchesReviewedBaseline['+name+']';order.append(case)
            for status in (1,0):
                lines.extend([f'INSTRUMENTATION_STATUS: class={comparison.capture.CLASS}',f'INSTRUMENTATION_STATUS: test={case}',f'INSTRUMENTATION_STATUS_CODE: {status}'])
        runner='\n'.join(lines+['INSTRUMENTATION_CODE: -1',''])
        for side,label,vendor in [('first','capture-a','AuthenticAMD'),('second','capture-b','GenuineIntel')]:
            root=cls.template/side;folder=root/label;(folder/'images').mkdir(parents=True)
            (root/'emulator').mkdir()
            (root/'emulator/emulator-final-crashes.txt').write_bytes(b'')
            (root/'emulator/emulator-final-logcat.txt').write_bytes(b'')
            (root/'host-cpu.txt').write_text(f'Vendor ID: {vendor}\nModel name: Synthetic unit-test CPU\n')
            entries=[]
            for name,size in expected.items():
                blob=blobs[size];(folder/'images'/name).write_bytes(blob)
                entries.append(dict(file=name,sha256=hashlib.sha256(blob).hexdigest(),dimensions=list(size)))
            result=dict(sourceCommit=comparison.capture.SOURCE,sourceTree=comparison.capture.SOURCE_TREE,
                apkSha256=list(comparison.capture.HASHES),driverCommit=DRIVER,completed=True,cases=180,adbExit=0,
                recordingOnly=True,comparisonPerformed=False,releaseApproved=False,caseOrder=order,
                requestedGpu='swangle',rendererDescription=RENDERER,images=entries)
            (folder/'result.json').write_text(json.dumps(result))
            (folder/'health.json').write_text(json.dumps(dict(recordingCompleted=True,androidFailures=[],captureErrors=[],cleanupErrors=[])))
            (folder/'instrumentation.txt').write_text(runner)
            (folder/'surfaceflinger.txt').write_text(RENDERER+'\n')
            (folder/'crashes.txt').write_bytes(b'');(folder/'anr.txt').write_bytes(b'')

    def setUp(self):
        self.work=Path(tempfile.mkdtemp(dir=self.base))/'evidence'
        shutil.copytree(self.template,self.work)
        self.first,self.second=self.work/'first',self.work/'second'
        self.folder=self.second/'capture-b'

    def run_comparison(self):
        return comparison.compare(self.first,self.second,DRIVER)

    def change_result(self, change):
        path=self.folder/'result.json';data=json.loads(path.read_text());change(data);path.write_text(json.dumps(data))

    def change_pixel(self, value):
        name=sorted(comparison.capture.expected_images())[0];path=self.folder/'images'/name
        with Image.open(path) as image: updated=image.copy()
        updated.putpixel((0,0),value);updated.save(path,format='PNG')
        digest=hashlib.sha256(path.read_bytes()).hexdigest()
        self.change_result(lambda r:next(x for x in r['images'] if x['file']==name).update(sha256=digest))

    def test_complete_different_vendors_and_exact_pixels_pass_only_the_comparison(self):
        result=self.run_comparison()
        self.assertTrue(result['crossVendorComparisonPassed'])
        self.assertEqual(222,len(result['images']))
        self.assertFalse(result['qualityGateApproved'] or result['baselineApproval'] or result['releaseApproved'])

    def test_complete_same_vendor_is_inconclusive_even_with_identical_pixels(self):
        (self.second/'host-cpu.txt').write_text('Vendor ID: AuthenticAMD\nModel name: Another synthetic CPU\n')
        result=self.run_comparison()
        self.assertEqual('inconclusive_same_cpu_vendor',result['conclusion'])
        self.assertFalse(result['crossVendorComparisonPassed'])

    def test_one_channel_one_pixel_change_is_a_failure(self):
        self.change_pixel((31,35,42,255))
        result=self.run_comparison()
        self.assertFalse(result['crossVendorComparisonPassed'])
        self.assertEqual(1,result['changedImages'])
        difference=next(x for x in result['images'] if x['changedPixels'])
        self.assertEqual((1,1),(difference['changedPixels'],difference['maxChannelDelta']))

    def test_transparent_pixel_is_rejected(self):
        self.change_pixel((30,35,42,0))
        with self.assertRaisesRegex(ValueError,'Nonopaque'):self.run_comparison()

    def test_partial_recording_is_rejected(self):
        self.change_result(lambda r:r.update(completed=False))
        with self.assertRaisesRegex(ValueError,'Incomplete'):self.run_comparison()

    def test_missing_image_is_rejected(self):
        next((self.folder/'images').glob('*.png')).unlink()
        with self.assertRaisesRegex(ValueError,'inventory'):self.run_comparison()

    def test_duplicate_inventory_record_is_rejected(self):
        self.change_result(lambda r:r['images'].__setitem__(-1,r['images'][0]))
        with self.assertRaisesRegex(ValueError,'inventory'):self.run_comparison()

    def test_image_hash_mismatch_is_rejected(self):
        self.change_result(lambda r:r['images'][0].update(sha256='0'*64))
        with self.assertRaisesRegex(ValueError,'hash mismatch'):self.run_comparison()

    def test_wrong_driver_is_rejected(self):
        self.change_result(lambda r:r.update(driverCommit='b'*40))
        with self.assertRaisesRegex(ValueError,'Driver'):self.run_comparison()

    def test_wrong_apk_is_rejected(self):
        self.change_result(lambda r:r.update(apkSha256=['0'*64,'1'*64]))
        with self.assertRaisesRegex(ValueError,'APK'):self.run_comparison()

    def test_failed_case_is_rejected_even_with_complete_metadata(self):
        path=self.folder/'instrumentation.txt'
        path.write_text(path.read_text().replace('INSTRUMENTATION_STATUS_CODE: 0','INSTRUMENTATION_STATUS_CODE: -2',1))
        with self.assertRaisesRegex(ValueError,'Failed'):self.run_comparison()

    def test_android_crash_is_rejected_even_with_clean_health_metadata(self):
        (self.folder/'crashes.txt').write_text('Fatal signal 11 (SIGSEGV)')
        with self.assertRaisesRegex(ValueError,'Android failure'):self.run_comparison()

    def test_system_crash_after_recording_cleanup_is_also_rejected(self):
        (self.second/'emulator/emulator-final-crashes.txt').write_text('Fatal signal 11 (SIGSEGV)')
        with self.assertRaisesRegex(ValueError,'after recording cleanup'):self.run_comparison()

    def test_ambiguous_cpu_is_rejected(self):
        path=self.second/'host-cpu.txt';path.write_text(path.read_text()+'Vendor ID: AuthenticAMD\n')
        with self.assertRaisesRegex(ValueError,'CPU identity'):self.run_comparison()

    def test_claimed_case_order_must_match_the_raw_runner(self):
        self.change_result(lambda r:r.update(caseOrder=list(reversed(r['caseOrder']))))
        with self.assertRaisesRegex(ValueError,'Case order'):self.run_comparison()


if __name__ == '__main__':unittest.main()
