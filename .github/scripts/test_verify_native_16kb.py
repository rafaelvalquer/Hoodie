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


def elf_with_relro(address, memory_size, bits=64, mutable_tail=0):
    data = elf(bits=bits)
    data.extend(bytes(128))
    if bits == 64:
        struct.pack_into("<HH", data, 54, 56, 2)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 6, address % 16384, address, 0, memory_size, memory_size + mutable_tail, 16384)
        struct.pack_into("<IIQQQQQQ", data, 120, 0x6474E552, 4, 0, address, 0, memory_size, memory_size, 1)
    else:
        struct.pack_into("<HH", data, 42, 32, 2)
        struct.pack_into("<IIIIIIII", data, 52, 1, address % 16384, address, 0, memory_size, memory_size + mutable_tail, 6, 16384)
        struct.pack_into("<IIIIIIII", data, 84, 0x6474E552, 0, address, 0, memory_size, memory_size, 4, 1)
    return data


def with_writable_neighbor(data, address):
    struct.pack_into("<HH", data, 54, 56, 3)
    struct.pack_into("<IIQQQQQQ", data, 176, 1, 6, address % 16384, address, 0, 0, 16, 16384)
    return data


class NativeAlignmentTest(unittest.TestCase):
    def test_relro_rejects_protection_of_following_mutable_data(self):
        # Android's documented failing example ends at 0xe1000; the
        # compatible variant ends at 0xe0000. RELRO p_align itself is 1.
        with self.assertRaisesRegex(ValueError, "RELRO protects writable"):
            native.inspect_elf(elf_with_relro(0xdfaf0, 0x1510, mutable_tail=0x1000))
        result = native.inspect_elf_details(elf_with_relro(0xdfaf0, 0x510, mutable_tail=0x1000))
        self.assertEqual(0xe0000, result["relro_segments"][0]["protected_end"])

    def test_relro_in_own_load_can_end_before_page_boundary(self):
        result = native.inspect_elf_details(elf_with_relro(0x4680, 0x980))
        self.assertEqual(0x5000, result["relro_segments"][0]["end"])
        self.assertEqual(0x8000, result["relro_segments"][0]["protected_end"])

    def test_32_bit_relro_uses_4kb_pages(self):
        result = native.inspect_elf_details(elf_with_relro(0xdfaf0, 0x1510, bits=32, mutable_tail=0x1000))
        self.assertEqual(4096, result["relro_page_size"])

    def test_dedicated_relro_cannot_protect_a_separate_writable_load(self):
        for address in (0x4000, 0x5680):
            with self.subTest(address=address), self.assertRaisesRegex(ValueError, "RELRO protects writable"):
                native.inspect_elf(with_writable_neighbor(elf_with_relro(0x4680, 0x980), address))

    def test_writable_neighbor_after_protected_page_remains_compatible(self):
        result = native.inspect_elf_details(with_writable_neighbor(elf_with_relro(0x4680, 0x980), 0x8680))
        self.assertEqual(2, len(result["load_segments"]))

    def test_absent_relro_is_reported_without_weakening_load_checks(self):
        self.assertEqual([], native.inspect_elf_details(elf())["relro_segments"])
        with self.assertRaisesRegex(ValueError, "LOAD incompatible"):
            native.inspect_elf_details(elf(alignment=4096))

    def test_one_bad_relro_in_transitive_library_rejects_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "test.aab"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("base/lib/arm64-v8a/libsqlcipher.so", elf_with_relro(0xdfaf0, 0x510))
                output.writestr("base/lib/x86_64/transitive.so", elf_with_relro(0xdfaf0, 0x1510, mutable_tail=0x1000))
            with self.assertRaisesRegex(ValueError, "RELRO protects writable"):
                native.inspect_archive(archive)

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
