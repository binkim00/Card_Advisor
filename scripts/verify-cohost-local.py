"""Build and load-check an isolated synthetic cohost stack on local Docker only."""
import argparse
import concurrent.futures
import datetime
import hashlib
import json
import os
import pathlib
import secrets
import shutil
import socket
import ssl
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.repo.resolve()
    source = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    run_id = datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:6]
    project = 'card-advisor-synthetic-' + run_id
    out = root / '.run' / 'cohost-local' / run_id
    out.mkdir(parents=True)
    report = {'source_commit': source, 'project': project, 'synthetic_only': True, 'server_deployment': False, 'passed': False}

    def command(argv, timeout=60, log=None, env=None):
        if log:
            with (out / log).open('w', encoding='utf-8') as stream:
                p = subprocess.run(argv, cwd=root, env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=timeout)
        else:
            p = subprocess.run(argv, cwd=root, env=env, capture_output=True, text=True, timeout=timeout)
        if p.returncode:
            raise RuntimeError('Command failed: ' + ' '.join(str(v) for v in argv[:3]) + (', see ' + log if log else ': ' + p.stderr[:500]))
        return '' if log else p.stdout

    def inventory(ids=None):
        ids = ids if ids is not None else command(['docker', 'ps', '-aq']).split()
        if not ids:
            return {}
        data = json.loads(command(['docker', 'inspect', *ids]))
        return {x['Id']: {'name': x['Name'], 'image': x['Image'], 'running': x['State']['Running'],
                'started_at': x['State']['StartedAt'], 'restart_count': x['RestartCount'],
                'mounts': sorted((m['Destination'], m.get('Name', ''), m['Source']) for m in x['Mounts'])} for x in data}

    assert not os.environ.get('DOCKER_HOST'), 'Do not use a remote Docker host'
    context = command(['docker', 'context', 'show']).strip()
    endpoint = json.loads(command(['docker', 'context', 'inspect', context]))[0]['Endpoints']['docker']['Host']
    assert endpoint.startswith('npipe://'), 'This verifier requires local Windows Docker Desktop'
    report['docker_context'] = context
    report['existing_before'] = baseline = inventory()
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 19443))
    build = out / 'image'
    build.mkdir()
    for name in ('Dockerfile', '.dockerignore'):
        shutil.copyfile(root / 'deploy/cohost' / name, build / name)
    jar = root / 'backend/target/backend-0.1.0.jar'
    shutil.copyfile(jar, build / 'app.jar')
    report['jar_sha256'] = hashlib.sha256(jar.read_bytes()).hexdigest()
    image = 'card-advisor-api:synthetic-' + run_id
    print('Building local API image for ' + source[:7], flush=True)
    command(['docker', 'build', '--label', 'org.opencontainers.image.revision=' + source, '-t', image, str(build)], timeout=600, log='image-build.log')
    report['image_id'] = command(['docker', 'image', 'inspect', '--format', '{{.Id}}', image]).strip()
    secret_dir = out / 'synthetic-secrets'
    secret_dir.mkdir()
    tls_password = secrets.token_urlsafe(24)
    (secret_dir / 'DB_PASSWORD').write_text(secrets.token_urlsafe(24), encoding='utf-8')
    (secret_dir / 'TLS_KEYSTORE_PASSWORD').write_text(tls_password, encoding='utf-8')
    env = os.environ.copy()
    env['COHOST_SYNTH_TLS'] = tls_password
    keytool = pathlib.Path(r'C:\Program Files\Java\jdk-21.0.11\bin\keytool.exe')
    command([str(keytool), '-genkeypair', '-alias', 'synthetic-local', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1', '-storetype', 'PKCS12', '-keystore', str(secret_dir / 'tls.p12'), '-storepass:env', 'COHOST_SYNTH_TLS', '-noprompt'], env=env, log='synthetic-tls.log')
    command([str(keytool), '-exportcert', '-rfc', '-alias', 'synthetic-local', '-keystore', str(secret_dir / 'tls.p12'), '-storepass:env', 'COHOST_SYNTH_TLS', '-file', str(out / 'ca.pem')], env=env, log='synthetic-cert-export.log')
    env_file = out / 'synthetic.env'
    env_file.write_text('CARD_API_IMAGE=' + image + '\nCARD_SECRETS_DIR=' + secret_dir.as_posix() + '\n', encoding='utf-8')
    compose = ['docker', 'compose', '--project-name', project, '--env-file', str(env_file), '-f', str(root / 'deploy/cohost/compose.yaml')]
    cfg = json.loads(command(compose + ['config', '--format', 'json']))
    assert cfg['name'] == project
    assert cfg['networks']['private_db']['internal'], 'DB must remain isolated'
    report['runtime_network'] = 'unchanged_cohost_topology_private_db_and_api_egress'
    report['provider_configuration'] = 'all_blank_no_financial_ai_or_mail_credentials_loaded'
    assert not cfg['services']['db'].get('ports')
    api_cfg = cfg['services']['api']
    assert api_cfg['ports'][0]['host_ip'] == '127.0.0.1' and api_cfg['environment']['DB_URL'] == 'jdbc:postgresql://db:5432/card_advisor'
    for key in ['CODEF_CONFIG_FILE', 'CODEF_STORAGE_KEY_FILE', 'OPENAI_CONFIG_FILE', 'ACCOUNT_MAIL_CONFIG_FILE']:
        assert api_cfg['environment'][key] == ''
    assert not set(command(compose + ['ps', '-aq']).split()), 'Test project must start empty'
    started = False
    sample_stop = threading.Event()
    samples = []
    sample_errors = []
    try:
        print('Starting isolated HTTPS API and empty synthetic DB', flush=True)
        started = True
        command(compose + ['up', '-d', '--wait', '--wait-timeout', '180'], timeout=240, log='startup.log')
        ids = command(compose + ['ps', '-q']).split()
        details = json.loads(command(['docker', 'inspect', *ids]))
        by_service = {x['Config']['Labels']['com.docker.compose.service']: x for x in details}
        assert set(by_service) == {'api', 'db'}
        for name, limit, nano in [('api', 768 * 1024**2, 500000000), ('db', 384 * 1024**2, 250000000)]:
            item = by_service[name]
            assert item['Config']['Labels']['com.docker.compose.project'] == project
            assert item['HostConfig']['Memory'] == limit and item['HostConfig']['MemorySwap'] == limit
            assert item['HostConfig']['NanoCpus'] == nano and not item['State']['OOMKilled']
        report['runtime_limits'] = {name: {'memory_bytes': x['HostConfig']['Memory'], 'memory_swap_bytes': x['HostConfig']['MemorySwap'], 'nano_cpus': x['HostConfig']['NanoCpus']} for name, x in by_service.items()}
        api_id, db_id = by_service['api']['Id'], by_service['db']['Id']
        ctx = ssl.create_default_context(cafile=str(out / 'ca.pem'))
        base = 'https://127.0.0.1:19443'
        def call(path, method='GET', data=None, token=None, expected=200):
            headers = {'Content-Type': 'application/json'}
            if token:
                headers['Authorization'] = 'Bearer ' + token
            req = urllib.request.Request(base + path, data=json.dumps(data).encode() if data is not None else None, headers=headers, method=method)
            try:
                with urllib.request.urlopen(req, context=ctx, timeout=20) as response:
                    status, body = response.status, response.read()
            except urllib.error.HTTPError as exc:
                status, body = exc.code, exc.read()
            assert status == expected, f'{method} {path.split("?")[0]} expected {expected}, got {status}'
            return json.loads(body) if body else None
        assert call('/v1/health')['status'] == 'UP'
        try:
            urllib.request.urlopen(base + '/v1/health', timeout=5)
            raise AssertionError('Untrusted synthetic certificate unexpectedly accepted')
        except urllib.error.URLError as exc:
            assert isinstance(exc.reason, ssl.SSLCertVerificationError)
        report['https_trust_and_host_validation'] = 'passed'
        original_urlopen = urllib.request.urlopen
        def trusted_urlopen(*a, **kwargs):
            kwargs.setdefault('context', ctx)
            return original_urlopen(*a, **kwargs)
        smoke = (root / 'scripts/api_smoke.py').read_text(encoding='utf-8')
        smoke = smoke.replace('BASE="http://127.0.0.1:18080"', 'BASE=' + repr(base))
        smoke = smoke.replace('out=pathlib.Path(__file__).resolve().parents[1]/".run"/"api-smoke.json"', 'out=pathlib.Path(' + repr(str(out / 'api-smoke.json')) + ')')
        urllib.request.urlopen = trusted_urlopen
        try:
            exec(compile(smoke, str(root / 'scripts/api_smoke.py'), 'exec'), {'__file__': str(root / 'scripts/api_smoke.py')})
        finally:
            urllib.request.urlopen = original_urlopen
        report['contract_smoke'] = json.loads((out / 'api-smoke.json').read_text(encoding='utf-8'))

        def sampler():
            while not sample_stop.is_set():
                try:
                    metrics = command(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *ids], timeout=10)
                    samples.append({'at': time.time(), 'containers': [json.loads(line) for line in metrics.splitlines()]})
                except Exception as exc:
                    sample_errors.append(type(exc).__name__)
                sample_stop.wait(1)
        monitor = threading.Thread(target=sampler, daemon=True)
        monitor.start()
        month = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).strftime('%Y-%m')
        users = []
        for _ in range(8):
            auth = call('/v1/auth/register', 'POST', {'email': 'cohost-' + uuid.uuid4().hex + '@example.test', 'password': secrets.token_urlsafe(24)}, expected=201)
            demo = call('/v1/demo/setup', 'POST', token=auth['accessToken'])
            users.append((auth['accessToken'], demo['cards'][0]['id']))
        order = {'orderValueWon': 50000, 'pointUsedWon': 0, 'prepaidMoneyUsedWon': 0, 'cardChargeWon': 50000, 'category': 'SHOPPING', 'paymentMethod': 'NAVER_PAY_CARD', 'installments': 1, 'splitApproval': 'NO', 'confirmedByUser': True}

        def worker(index, deadline):
            token, card = users[index]
            latencies, errors = [], []
            writes = 0
            iteration = 0
            while time.monotonic() < deadline:
                begin = time.perf_counter()
                try:
                    mode = iteration % 6
                    if mode == 0:
                        call('/v1/dashboard?month=' + month, token=token)
                    elif mode == 1:
                        call('/v1/cards/performance?month=' + month, token=token)
                    elif mode == 2:
                        call('/v1/budgets/' + month + '/summary', token=token)
                    elif mode == 3:
                        entry = {'clientId': str(uuid.uuid4()), 'amountWon': 100, 'cardId': card, 'cardChargeWon': 100, 'purchasedAt': month + '-01', 'category': 'SHOPPING', 'included': True}
                        call('/v1/transactions/manual', 'POST', entry, token=token)
                        writes += 1
                    elif mode == 4:
                        checkout = call('/v1/checkout-sessions', 'POST', order, token=token)
                        latencies.append((time.perf_counter() - begin) * 1000)
                        begin = time.perf_counter()
                        rec = call('/v1/recommendations', 'POST', {'checkoutId': checkout['id'], 'snapshotVersion': 1, 'mode': 'BALANCED', 'maxOpportunityCostWon': 1000}, token=token)
                        assert rec['result']['candidates']
                    else:
                        call('/v1/transactions?month=' + month, token=token)
                    latencies.append((time.perf_counter() - begin) * 1000)
                except Exception as exc:
                    errors.append(str(exc)[:150])
                iteration += 1
            return {'latencies': latencies, 'errors': errors, 'writes': writes}

        phases = []
        expected_writes = 0
        for concurrency, seconds in [(2, 10), (8, 30)]:
            print(f'Load phase: {concurrency} clients, {seconds} seconds', flush=True)
            begin = time.monotonic()
            with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
                results = list(pool.map(lambda i: worker(i, begin + seconds), range(concurrency)))
            elapsed = time.monotonic() - begin
            times = sorted(t for r in results for t in r['latencies'])
            errors = [e for r in results for e in r['errors']]
            expected_writes += sum(r['writes'] for r in results)
            phases.append({'clients': concurrency, 'duration_seconds': round(elapsed, 2), 'successful_requests': len(times), 'errors': len(errors), 'error_examples': errors[:5], 'requests_per_second': round(len(times) / elapsed, 2), 'p50_ms': round(times[len(times)//2], 2) if times else None, 'p95_ms': round(times[min(len(times)-1, int(len(times)*.95))], 2) if times else None, 'max_ms': round(max(times), 2) if times else None})
            report['load_phases'] = phases
            assert times and not errors, 'Load phase had request errors'
        report['load_phases'] = phases
        counts = command(['docker', 'exec', db_id, 'psql', '-U', 'card_advisor', '-d', 'card_advisor', '-At', '-c', 'SELECT (SELECT count(*) FROM app_users), (SELECT count(*) FROM cards), (SELECT count(*) FROM ledger_entries), (SELECT count(*) FROM financial_transactions), (SELECT max(version::int) FROM flyway_schema_history WHERE success);']).strip().split('|')
        report['synthetic_database'] = dict(zip(['users','cards','ledger_entries','financial_transactions','flyway_version'], map(int, counts)))
        assert int(counts[0]) == 8 and int(counts[2]) == expected_writes and int(counts[3]) == 0
        report['expected_ledger_writes'] = expected_writes
        report['cgroup'] = {name: command(['docker', 'exec', item['Id'], 'sh', '-c', 'cat /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.swap.max /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.events /sys/fs/cgroup/cpu.max']).strip() for name, item in by_service.items()}
        report['measured_resources'] = {}
        for name, limit, quota in [('api', 768 * 1024**2, 50000), ('db', 384 * 1024**2, 25000)]:
            lines = report['cgroup'][name].splitlines()
            events = {key: int(value) for key, value in (line.split() for line in lines[3:-1])}
            assert int(lines[0]) == limit and int(lines[1]) == 0
            assert int(lines[2]) <= limit and events['oom'] == events['oom_kill'] == 0
            assert list(map(int, lines[-1].split())) == [quota, 100000]
            report['measured_resources'][name] = {'peak_mib': round(int(lines[2]) / 1024**2, 2), 'memory_events': events}
        final = json.loads(command(['docker', 'inspect', *ids]))
        report['new_container_state'] = {x['Config']['Labels']['com.docker.compose.service']: {'oom_killed': x['State']['OOMKilled'], 'restart_count':x['RestartCount'], 'health':x['State']['Health']['Status']} for x in final}
        assert all(not x['State']['OOMKilled'] and x['RestartCount'] == 0 and x['State']['Health']['Status'] == 'healthy' for x in final)
        report['passed'] = True
    except Exception as exc:
        report['error'] = type(exc).__name__ + ': ' + str(exc)[:500]
        if started:
            command(compose + ['logs', '--no-color', '--tail', '100'], log='failure-container-logs.txt')
        raise
    finally:
        sample_stop.set()
        if 'monitor' in locals():
            monitor.join(timeout=12)
        report['resource_samples'] = samples
        report['sampling_errors'] = sample_errors
        if started:
            command(compose + ['stop', '--timeout', '15'], timeout=45, log='stop.log')
        report['existing_after'] = inventory(list(baseline))
        report['existing_container_metadata_unchanged'] = report['existing_after'] == baseline
        report['synthetic_stack_stopped'] = not command(compose + ['ps', '--status', 'running', '-q']).strip()
        report['passed'] = report['passed'] and report['existing_container_metadata_unchanged'] and report['synthetic_stack_stopped']
        (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'report': str(out / 'report.json'), 'passed': report['passed'], 'error':report.get('error'), 'existing_container_metadata_unchanged':report['existing_container_metadata_unchanged']}, ensure_ascii=False), flush=True)
    if not report['passed']:
        raise RuntimeError('Verification did not pass; inspect the saved report')


if __name__ == '__main__':
    main()
