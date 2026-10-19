#!/usr/bin/env python3
"""Fail closed on every shipped ELF LOAD segment and uncompressed APK ZIP offset."""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


def inspect_elf(data):
    if data[:4] != b"\x7fELF" or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError("Unsupported ELF header")
    endian = "<" if data[5] == 1 else ">"
    is64 = data[4] == 2
    phoff = struct.unpack_from(endian + ("Q" if is64 else "I"), data, 32 if is64 else 28)[0]
    size, count = struct.unpack_from(endian + "HH", data, 54 if is64 else 42)
    if size < (56 if is64 else 32) or count == 0 or phoff + size * count > len(data):
        raise ValueError("Invalid ELF program headers")
    loads = []
    for index in range(count):
        fields = struct.unpack_from(endian + ("IIQQQQQQ" if is64 else "IIIIIIII"), data, phoff + index * size)
        if fields[0] != 1:
            continue
        offset, address, alignment = (fields[2], fields[3], fields[7]) if is64 else (fields[1], fields[2], fields[7])
        if alignment < 16384 or alignment & (alignment - 1) or (address - offset) % 16384:
            raise ValueError(f"LOAD incompatible with 16 KB: align={alignment}, offset={offset}, address={address}")
        loads.append({"offset": offset, "address": address, "alignment": alignment})
    if not loads:
        raise ValueError("ELF has no LOAD segments")
    return loads


def inspect_archive(path):
    libraries = []
    with zipfile.ZipFile(path) as archive, open(path, "rb") as raw:
        for entry in archive.infolist():
            if not entry.filename.endswith(".so"):
                continue
            data = archive.read(entry)
            segments = inspect_elf(data)
            offset = None
            if path.suffix == ".apk" and entry.compress_type == zipfile.ZIP_STORED:
                raw.seek(entry.header_offset)
                header = raw.read(30)
                name_length, extra_length = struct.unpack_from("<HH", header, 26)
                offset = entry.header_offset + 30 + name_length + extra_length
                if offset % 16384:
                    raise ValueError(f"{entry.filename}: uncompressed APK library is not ZIP-aligned: {offset}")
            libraries.append({"path": entry.filename, "sha256": hashlib.sha256(data).hexdigest(),
                              "load_segments": segments, "zip_offset": offset,
                              "compressed": entry.compress_type != zipfile.ZIP_STORED})
    if not libraries or not any(item["path"].endswith("/libsqlcipher.so") for item in libraries):
        raise ValueError("Native SQLCipher library missing from package")
    return {"artifact": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "libraries": libraries}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("archives", nargs="+", type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    reports = [inspect_archive(path) for path in args.archives]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps({"static_alignment_passed": True, "runtime_verified": False,
                                       "archives": reports}, indent=2) + "\n", encoding="utf-8")
