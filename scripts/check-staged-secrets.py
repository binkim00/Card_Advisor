#!/usr/bin/env python3
"""Inspect Git index bytes. Never print matched values or private source lines."""
import argparse
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile

RULES = [
    ('private-key', re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----')),
    ('openai-key', re.compile(rb'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b')),
    ('aws-access-key', re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b')),
    ('github-token', re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b')),
    ('slack-token', re.compile(rb'\bxox[baprs]-[A-Za-z0-9-]{20,}\b')),
    ('jwt', re.compile(rb'\beyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{20,}\b')),
    ('credential-url', re.compile(rb'(?i)\b(?:postgres(?:ql)?|mysql|https?)://[^\s/:]+:[^\s/@]{8,}@')),
]
PRIVATE_DIRS = {'.local', '.run', '.tools', 'outputs', 'secrets', 'backups', 'node_modules', '.gradle', 'target', 'build', 'data', 'artifacts', 'captures', 'screenshots', 'tmp', '.venv', 'venv', '.aws', '.ssh', '.kube', '.cxx', '.externalNativeBuild', '.pytest_cache', '.mypy_cache', '.ruff_cache'}
PRIVATE_SUFFIXES = {'.key', '.pem', '.p12', '.pfx', '.jks', '.keystore', '.dpapi', '.pgdump', '.dump', '.backup', '.db', '.sqlite', '.sqlite3', '.apk', '.aab', '.apks', '.log', '.hprof', '.crt', '.cer', '.der', '.jfr', '.heapdump', '.pgpass'}

def git(repo, *args):
    r = subprocess.run(['git', '-C', str(repo), *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if r.returncode:
        raise RuntimeError('Git command failed; output withheld')
    return r.stdout

def forbidden(name):
    p = PurePosixPath(name)
    return (any(c in PRIVATE_DIRS for c in p.parts)
            or p.suffix.lower() in PRIVATE_SUFFIXES
            or (p.name.startswith('.env') and p.name != '.env.example')
            or p.name in {'local.properties', 'codef.properties', 'mail.properties', 'openai.properties'}
            or p.name.endswith(('.env', '.local.properties', '.sql.gz', '.sql.zip', '.pgdump.gz'))
            or p.name.startswith(('application-local.', 'application-secrets.'))
            or any(ord(c) < 32 for c in name))

def local_values(repo):
    # Explicit opt-in. Read only text configuration, never keys/DB dumps/screens/logs.
    paths = [repo / '.env'] + sorted((repo / '.local').glob('*.properties')) + sorted((repo / '.local').glob('*.env'))
    values = set()
    for p in paths:
        if not p.is_file():
            continue
        for line in p.read_text(encoding='utf-8-sig').splitlines():
            if line.lstrip().startswith(('#', '!')) or '=' not in line:
                continue
            key, value = line.split('=', 1)
            if not re.search(r'password|secret|token|client.?id|public.?key', key, re.I):
                continue
            value = value.strip().strip('\"\'')
            if len(value) >= 12 and not re.search(r'generate-locally|change-me|placeholder|example|your[_-]', value, re.I):
                values.add(value.encode('utf-8'))
    return values

def audit(repo, compare_local=False):
    findings = []
    names = git(repo, 'diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z').split(b'\0')
    secrets = local_values(repo) if compare_local else set()
    index = {}
    for row in git(repo, 'ls-files', '--stage', '-z').split(b'\0'):
        if row:
            meta, path = row.split(b'\t', 1)
            mode, oid, stage = meta.split()
            if stage != b'0':
                raise RuntimeError('Unmerged index')
            index[path] = (mode, oid)
    requested = [raw for raw in names if raw]
    batch = subprocess.run(['git', '-C', str(repo), 'cat-file', '--batch'],
                           input=b''.join(index[raw][1] + b'\n' for raw in requested),
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if batch.returncode:
        raise RuntimeError('Index blob read failed')
    blobs = {}
    offset = 0
    for raw in requested:
        end = batch.stdout.index(b'\n', offset)
        oid, kind, size = batch.stdout[offset:end].split()
        size = int(size)
        offset = end + 1
        if kind != b'blob':
            raise RuntimeError('Non-file index entry requires review')
        blobs[raw] = batch.stdout[offset:offset + size]
        offset += size + 1
    for raw in names:
        if not raw:
            continue
        name = raw.decode('utf-8', 'strict')
        if forbidden(name):
            findings.append((name, 0, 'forbidden-private-path'))
            continue
        mode = index[raw][0]
        if mode == b'120000':
            findings.append((name, 0, 'symlink-review-required'))
            continue
        data = blobs[raw]
        if len(data) > 2 * 1024 * 1024:
            findings.append((name, 0, 'large-file-review-required'))
            continue
        binary = b'\0' in data or name.lower().endswith(('.png', '.jpg', '.jpeg', '.webp', '.gif', '.jar'))
        if binary:
            asset = name.startswith('android/app/src/main/res/') and name.lower().endswith(('.png', '.jpg', '.jpeg', '.webp', '.gif'))
            wrapper = name.endswith('/gradle/wrapper/gradle-wrapper.jar')
            if not (asset or wrapper):
                findings.append((name, 0, 'binary-review-required'))
            continue
        try:
            data.decode('utf-8-sig')
        except UnicodeDecodeError:
            findings.append((name, 0, 'non-utf8-review-required'))
            continue
        for rule, pattern in RULES:
            for match in pattern.finditer(data):
                findings.append((name, data[:match.start()].count(b'\n') + 1, rule))
        for value in secrets:
            start = data.find(value)
            if start >= 0:
                findings.append((name, data[:start].count(b'\n') + 1, 'matches-private-local-setting'))
    return sorted(set(findings))

def self_test():
    with tempfile.TemporaryDirectory(prefix='card-advisor-git-check-') as directory:
        repo = Path(directory)
        git(repo, 'init', '-q')
        (repo / 'README.md').write_text('API_KEY=<configure-locally>\n', encoding='utf-8')
        git(repo, 'add', '--', 'README.md')
        assert not audit(repo)
        # Validate the actual index, even when the worktree no longer has the secret.
        fake = 'sk-proj-' + 'A' * 48
        (repo / 'README.md').write_text(fake, encoding='utf-8')
        git(repo, 'add', '--', 'README.md')
        (repo / 'README.md').write_text('safe worktree', encoding='utf-8')
        assert any(rule == 'openai-key' for _, _, rule in audit(repo))
        (repo / '.local').mkdir()
        (repo / '.local/private.txt').write_text('synthetic', encoding='utf-8')
        git(repo, 'add', '-f', '--', '.local/private.txt')
        assert any(rule == 'forbidden-private-path' for _, _, rule in audit(repo))
        (repo / 'fixture.txt').write_text('-----BEGIN ' + 'PRIVATE KEY-----', encoding='utf-8')
        git(repo, 'add', '--', 'fixture.txt')
        assert any(rule == 'private-key' for _, _, rule in audit(repo))
        (repo / '.env').write_text('DB_PASSWORD=SyntheticLocalOnly92847', encoding='utf-8')
        (repo / 'copied-config.txt').write_text('SyntheticLocalOnly92847', encoding='utf-8')
        git(repo, 'add', '--', 'copied-config.txt')
        assert any(rule == 'matches-private-local-setting' for _, _, rule in audit(repo, True))
    print('Staged-secret self-test: 5 scenarios passed (synthetic temporary repository).')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit-local', action='store_true')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    repo = Path(git(Path.cwd(), 'rev-parse', '--show-toplevel').decode().strip())
    findings = audit(repo, args.audit_local)
    if findings:
        print('Commit blocked: staged files need review. Values are withheld.', file=sys.stderr)
        for name, line, rule in findings:
            print(f'{name}:{line}: {rule}', file=sys.stderr)
        return 1
    print('Staged-secret check passed' + (' including local-value comparison.' if args.audit_local else '.'))
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(f'Staged-secret check could not complete ({type(error).__name__}); details withheld.', file=sys.stderr)
        sys.exit(2)
