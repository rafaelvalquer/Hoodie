import importlib.util
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("native_alignment", Path(__file__).with_name("verify-native-16kb.py"))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


def elf(alignment=16384, address=0, bits=64):
    data = bytearray(128)
    data[:6] = b"\x7fELF" + bytes([2 if bits == 64 else 1, 1])
    if bits == 64:
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, address, 0, 128, 128, alignment)
    else:
        struct.pack_into("<I", data, 28, 52)
        struct.pack_into("<HH", data, 42, 32, 1)
        struct.pack_into("<IIIIIIII", data, 52, 1, 0, address, 0, 128, 128, 5, alignment)
    return data


class NativeAlignmentTest(unittest.TestCase):
    def test_32_and_64_bit_load_segments(self):
        for bits in (32, 64):
            self.assertEqual(16384, native.inspect_elf(elf(bits=bits))[0]["alignment"])

    def test_4kb_segment_rejected(self):
        with self.assertRaisesRegex(ValueError, "incompatible"):
            native.inspect_elf(elf(alignment=4096))

    def test_incongruent_address_rejected(self):
        with self.assertRaisesRegex(ValueError, "incompatible"):
            native.inspect_elf(elf(address=4096))

    def test_bad_headers_rejected(self):
        with self.assertRaises((ValueError, struct.error)):
            native.inspect_elf(b"truncated")

    def test_one_bad_transitive_library_rejects_entire_bundle(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "test.aab"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("base/lib/arm64-v8a/libsqlcipher.so", elf())
                output.writestr("base/lib/x86_64/transitive.so", elf(alignment=4096))
            with self.assertRaises(ValueError):
                native.inspect_archive(archive)

    def test_unaligned_uncompressed_apk_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "test.apk"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("lib/arm64-v8a/libsqlcipher.so", elf())
            with self.assertRaisesRegex(ValueError, "ZIP-aligned"):
                native.inspect_archive(archive)

    def test_compressed_apk_still_requires_valid_elf(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "test.apk"
            with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as output:
                output.writestr("lib/arm64-v8a/libsqlcipher.so", elf())
            self.assertTrue(native.inspect_archive(archive)["libraries"][0]["compressed"])


if __name__ == "__main__":
    unittest.main()
