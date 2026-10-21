"""Independent crypto audit: the frozen Android fixture and adversarial files."""
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('auth',Path(__file__).with_name('verify-synthetic-backup.py'))
auth=importlib.util.module_from_spec(spec)
spec.loader.exec_module(auth)
ROOT=Path(__file__).resolve().parents[2]
FIXTURE=ROOT/'app/src/test/resources/backup-v1-minimal.hoodiebackup'


class SyntheticAuthenticationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blob=FIXTURE.read_bytes()
        base=ROOT/'app/build/reports/synthetic-backup-auth-tests'
        base.mkdir(parents=True,exist_ok=True)
        cls.out=Path(tempfile.mkdtemp(dir=base))

    def inspect(self,blob):
        path=self.out/(hashlib.sha256(blob).hexdigest()+'.hoodiebackup')
        if not path.exists():path.write_bytes(blob)
        return auth.inspect(path)

    def test_frozen_android_fixture_authenticates_without_changes(self):
        result=auth.inspect(FIXTURE)
        self.assertEqual('c0d7739652d6b5e1a8e1c6df1b7cf592342db6d334c6c1b6d16fa016b43e20a7',result['sha256'])
        self.assertEqual(3,result['authenticatedUnits'])
        self.assertEqual(1,result['counts']['places'])
        self.assertFalse(result['includeDigital'])
        self.assertFalse(result['rowSchemaValidated'])

    def test_header_ciphertext_and_final_tag_mutation_are_rejected(self):
        for offset in [16,39,49,len(self.blob)-1]:
            with self.subTest(offset=offset),self.assertRaises((ValueError,auth.InvalidTag)):
                mutated=bytearray(self.blob);mutated[offset]^=1
                self.inspect(mutated)

    def test_truncation_and_missing_authenticated_terminator_are_rejected(self):
        for size in [0,39,45,len(self.blob)-24,len(self.blob)-1]:
            with self.subTest(size=size),self.assertRaises(ValueError):self.inspect(self.blob[:size])

    def test_extra_bytes_after_terminator_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'Bytes after'):self.inspect(self.blob+b'extra')

    def test_bad_kdf_is_rejected_before_derivation(self):
        for iterations in [-1,0,599999,1200001]:
            mutated=bytearray(self.blob);struct.pack_into('>i',mutated,12,iterations)
            with self.subTest(iterations=iterations),patch.object(auth.hashlib,'pbkdf2_hmac') as kdf:
                with self.assertRaisesRegex(ValueError,'KDF limit'):self.inspect(mutated)
                kdf.assert_not_called()

    def test_negative_oversized_length_and_wrong_sequence_are_rejected(self):
        for offset,value in [(40,1),(44,-1),(44,262145)]:
            mutated=bytearray(self.blob);struct.pack_into('>i',mutated,offset,value)
            with self.subTest(offset=offset,value=value),self.assertRaises(ValueError):self.inspect(mutated)

    def test_duplicate_json_fields_nonfinite_and_depth_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'Duplicate JSON'):json.loads('{"kind":1,"kind":2}',object_pairs_hook=auth.object_pairs)
        with self.assertRaisesRegex(ValueError,'Nonfinite'):auth.validate_json(float('inf'))
        nested={}
        for _ in range(14):nested={'nested':nested}
        with self.assertRaisesRegex(ValueError,'depth'):auth.validate_json(nested)

    def test_authenticated_wrong_counts_are_rejected(self):
        header=self.blob[:40]
        key=hashlib.pbkdf2_hmac('sha256',b'hoodie recovery phrase',header[16:32],600000,32)
        cipher=auth.AESGCM(key)
        units=[];position=40
        while True:
            framing=self.blob[position:position+8];index,size=struct.unpack('>ii',framing);position+=8
            plain=cipher.decrypt(header[32:40]+struct.pack('>I',index),self.blob[position:position+size+16],header+framing)
            position+=size+16
            if not size:break
            units.append(json.loads(plain))
        units[0]['counts']['places']=2
        # A new negative test file with fresh test header; never regenerate the frozen fixture.
        new_header=b'HOODIEB1'+struct.pack('>ii',1,600000)+bytes(range(16))+b'nonce001'
        self.assertEqual(40,len(new_header))
        new_cipher=auth.AESGCM(hashlib.pbkdf2_hmac('sha256',b'hoodie recovery phrase',new_header[16:32],600000,32))
        output=bytearray(new_header)
        for index,plain in enumerate([json.dumps(u).encode() for u in units]+[b'']):
            framing=struct.pack('>ii',index,len(plain))
            output+=framing+new_cipher.encrypt(new_header[32:40]+struct.pack('>I',index),plain,new_header+framing)
        with self.assertRaisesRegex(ValueError,'manifest count mismatch'):self.inspect(output)


if __name__=='__main__':unittest.main()
