"""Independent authentication/count audit of public synthetic Hoodie V1 backups.

Uses the public fixture phrase, never accepts production secrets, never writes
plaintext, and does not replace Android import/schema/relationship validation.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import struct
import sys

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DATASETS = ('places routines day_exceptions location_events context_events context_confirmations '
            'hoodie_activities timeline_events memories daily_device_usage daily_app_usage '
            'daily_context_app_usage app_category_overrides phone_app_sessions daily_screen_hourly '
            'daily_context_usage daily_phone_timeline mobility_sessions mobility_segments '
            'diary_corrections learned_routine_slots transport_patterns transport_evidence '
            'effective_routine_versions').split()
DIGITAL = set(DATASETS[9:17])
MAX_FILE = 512 * 1024 * 1024


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def object_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON field')
        result[key] = value
    return result


def validate_json(value, depth=0):
    require(depth <= 12, 'JSON depth limit')
    if isinstance(value, str):
        require(len(value.encode('utf-16-le', errors='surrogatepass'))//2 <= 65536, 'JSON string limit')
    elif isinstance(value, dict):
        for key, item in value.items():
            validate_json(key, depth+1)
            validate_json(item, depth+1)
    elif isinstance(value, list):
        for item in value:
            validate_json(item, depth+1)
    elif isinstance(value, float):
        require(math.isfinite(value), 'Nonfinite JSON number')


def inspect(path):
    path = Path(path)
    require(40 < path.stat().st_size <= MAX_FILE, 'File size limit')
    digest = hashlib.sha256()
    total = 0
    with path.open('rb') as stream:
        def take(size):
            nonlocal total
            require(total+size <= MAX_FILE, 'File size limit')
            blob = stream.read(size)
            require(len(blob) == size, 'Truncated backup')
            total += size
            digest.update(blob)
            return blob
        header = take(40)
        require(header[:8] == b'HOODIEB1', 'Invalid magic')
        version, iterations = struct.unpack('>ii', header[8:16])
        require(version == 1, 'Unsupported format')
        require(600000 <= iterations <= 1200000, 'KDF limit')
        key = hashlib.pbkdf2_hmac('sha256', b'hoodie recovery phrase', header[16:32], iterations, 32)
        cipher = AESGCM(key)
        sequence, manifest, counts, order = 0, None, Counter(), -1
        while True:
            framing = take(8)
            index, size = struct.unpack('>ii', framing)
            require(index == sequence <= 1000000, 'Invalid sequence')
            require(0 <= size <= 262144, 'Record size limit')
            plain = cipher.decrypt(header[32:40]+struct.pack('>I', index), take(size+16), header+framing)
            if size == 0:
                require(sequence >= 2, 'Manifest/settings missing')
                require(stream.read(1) == b'', 'Bytes after authenticated terminator')
                break
            unit = json.loads(plain.decode('utf-8'), object_pairs_hook=object_pairs,
                              parse_constant=lambda _: require(False, 'Nonfinite JSON number'))
            validate_json(unit)
            require(isinstance(unit, dict), 'JSON object required')
            if sequence == 0:
                require(unit.get('kind') == 'manifest' and type(unit.get('formatVersion')) is int and unit['formatVersion'] == 1, 'Manifest must be first')
                require(type(unit.get('databaseSchemaVersion')) is int and unit['databaseSchemaVersion'] == 12, 'Unsupported database schema')
                require(type(unit.get('includeDigital')) is bool, 'Invalid digital flag')
                expected = set(DATASETS) if unit['includeDigital'] else set(DATASETS)-DIGITAL
                require(isinstance(unit.get('counts'), dict) and set(unit['counts']) == expected, 'Dataset inventory mismatch')
                require(isinstance(unit.get('datasetVersions'), dict) and set(unit['datasetVersions']) == expected, 'Dataset version inventory mismatch')
                require(all(type(v) is int and v >= 0 for v in unit['counts'].values()), 'Invalid count')
                require(sum(unit['counts'].values()) <= 999998, 'Record count limit')
                require(all(type(v) is int and v == 1 for v in unit['datasetVersions'].values()), 'Unsupported dataset version')
                manifest = unit
            elif sequence == 1:
                require(unit.get('kind') == 'settings' and type(unit.get('version')) is int and unit['version'] == 1 and isinstance(unit.get('data'), dict), 'Settings must be second')
            else:
                dataset = unit.get('dataset')
                require(unit.get('kind') == 'record' and isinstance(dataset, str) and dataset in manifest['counts'], 'Invalid record dataset/kind')
                require(type(unit.get('version')) is int and unit['version'] == 1 and isinstance(unit.get('data'), dict), 'Invalid record envelope')
                position = DATASETS.index(dataset)
                require(position >= order, 'Dataset order changed')
                order = position
                counts[dataset] += 1
            sequence += 1
        require(all(counts[name] == count for name, count in manifest['counts'].items()), 'Authenticated manifest count mismatch')
    return dict(passed=True,sha256=digest.hexdigest(),bytes=total,formatVersion=version,
                iterations=iterations,authenticatedUnits=sequence,authenticatedTerminator=True,
                counts={name: counts[name] for name in manifest['counts']},includeDigital=manifest['includeDigital'],
                rowSchemaValidated=False,releaseApproved=False,
                scope='Independent AES-GCM/EOF, envelope/order and manifest-count audit with public synthetic phrase. No row-field/relationship, duplicate, migration, UI or physical-device acceptance.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backup', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    require(not args.output.exists(), 'Existing evidence must not be overwritten')
    result = inspect(args.backup)
    with args.output.open('x', encoding='utf-8') as f:
        f.write(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, InvalidTag, UnicodeError, OSError, RecursionError) as error:
        print('Synthetic backup verification failed: '+type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
