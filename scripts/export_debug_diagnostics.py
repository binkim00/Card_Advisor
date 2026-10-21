"""Read-only export of the app's allowlisted diagnostics; never starts/stops the app."""
import argparse
import io
import json
import os
import pathlib
import re
import subprocess
import tarfile
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
ADB = str(pathlib.Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
EVENTS = {'PROCESS_START', 'ACTIVITY_CREATED', 'ACTIVITY_STARTED', 'ACTIVITY_RESUMED',
          'ACTIVITY_PAUSED', 'ACTIVITY_STOPPED', 'ACTIVITY_DESTROYING', 'ACTIVITY_DESTROYED',
          'FIXED_ENTER', 'FIXED_EXIT', 'FIXED_LOAD_START', 'FIXED_LOAD_OK', 'FIXED_CANCELLED', 'EXCEPTION'}
SITES = {'FIXED_OPERATION', 'HTTP_IO', 'UNCAUGHT', 'TEST_OBSERVATION'}

def validate_record(record):
    base = {'schema', 'sequence', 'elapsedMs', 'pid', 'thread', 'event'}
    assert set(record) == (base | {'site', 'fatal', 'exceptions'} if record['event'] == 'EXCEPTION' else base)
    assert record['schema'] == 1 and record['thread'] in ('MAIN', 'OTHER') and record['event'] in EVENTS
    for key in ('sequence', 'elapsedMs', 'pid'):
        assert type(record[key]) is int and record[key] >= 0
    if record['event'] == 'EXCEPTION':
        assert record['site'] in SITES and type(record['fatal']) is bool
        assert 1 <= len(record['exceptions']) <= 8
        for exception in record['exceptions']:
            assert set(exception) == {'relation', 'type', 'frames'} and len(exception['frames']) <= 16
            assert exception['relation'] in ('root', 'cause', 'suppressed')
            assert re.fullmatch(r'[A-Za-z0-9_.$]{1,200}', exception['type'])
            for frame in exception['frames']:
                assert set(frame) == {'class', 'method', 'line'} and type(frame['line']) is int
                assert re.fullmatch(r'[A-Za-z0-9_.$]{1,200}', frame['class'])
                assert re.fullmatch(r'[A-Za-z0-9_$<>-]{1,96}', frame['method'])

def capture(device, output):
    result = subprocess.run([ADB, '-s', device, 'exec-out', 'run-as', 'dev.cardadvisor.android',
                             'tar', '-cf', '-', 'no_backup/debug-diagnostics'],
                            capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError('Debug records unavailable. Check device authorization and debug installation; no app state changed.')
    saved = {}
    with tarfile.open(fileobj=io.BytesIO(result.stdout), mode='r:') as archive:
        for member in archive.getmembers():
            if member.isdir():
                continue
            assert member.isfile(), 'Unexpected diagnostic entry type'
            match = re.fullmatch(r'no_backup/debug-diagnostics/(session-[0-9]+-[0-9]+-[a-f0-9-]{36})/(events(?:\.previous)?\.jsonl|first-(?:exception|uncaught)\.json)', member.name)
            assert match and member.size <= 65536, 'Unexpected diagnostic file or size'
            payload = archive.extractfile(member).read()
            assert b'PRIVATE_CANARY_' not in payload
            records = [json.loads(line) for line in payload.splitlines()] if member.name.endswith('.jsonl') else [json.loads(payload)]
            for record in records:
                validate_record(record)
            saved.setdefault(match.group(1), {})[match.group(2)] = payload
    assert 0 < len(saved) <= 8, 'No records or retention limit exceeded'
    # Validate everything before exporting. Never extract paths from an arbitrary archive.
    for session, files in saved.items():
        for name, payload in files.items():
            destination = pathlib.Path(output) / session / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload)
    return saved

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', default='emulator-5554')
    parser.add_argument('--output', type=pathlib.Path, default=ROOT / '.run/diagnostics-export' / str(uuid.uuid4()))
    args = parser.parse_args()
    try:
        saved = capture(args.device, args.output)
    except Exception:
        raise SystemExit('Export failed safely. Keep app data; check debug device connection. If recording was in progress, retry after it settles. No raw output saved.') from None
    print(json.dumps({'sessions': len(saved), 'output': str(args.output), 'readOnly': True}))
