"""One-time private cohost install; run as root on the specifically authorized host."""
import contextlib
import datetime
import hashlib
import io
import json
import os
import pathlib
import secrets
import shutil
import socket
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

STAGE = pathlib.Path(__file__).resolve().parent
APP = pathlib.Path('/opt/card-advisor')
PROJECT = 'card-advisor-cohost'

def run(argv, timeout=45, log=None):
    if log:
        with log.open('w') as stream:
            proc = subprocess.run(argv, stdout=stream, stderr=subprocess.STDOUT, timeout=timeout)
    else:
        proc = subprocess.run(argv, capture_output=True, text=True, timeout=timeout)
    if proc.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(argv[:3]))
    return '' if log else proc.stdout

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def inventory(ids=None):
    ids = ids if ids is not None else run(['docker','ps','-aq']).split()
    if not ids:
        return {}
    data = json.loads(run(['docker','inspect',*ids]))
    return {x['Id']: {'name':x['Name'], 'image':x['Image'], 'running':x['State']['Running'], 'started_at':x['State']['StartedAt'],
            'restart_count':x['RestartCount'], 'health':x['State'].get('Health',{}).get('Status'),
            'config_sha256':hashlib.sha256(json.dumps({'config':x['Config'],'host':x['HostConfig']},sort_keys=True).encode()).hexdigest(),
            'mounts':sorted((m['Destination'],m.get('Name',''),m['Source']) for m in x['Mounts'])} for x in data}

def capacity():
    mem = {line.split(':')[0]:int(line.split()[1])*1024 for line in pathlib.Path('/proc/meminfo').read_text().splitlines() if line.startswith(('MemTotal:','MemAvailable:','SwapTotal:','SwapFree:'))}
    return {'memory_bytes':mem, 'disk_free_bytes':shutil.disk_usage('/').free, 'load_average':os.getloadavg()}

def existing_health():
    with urllib.request.urlopen('http://100.71.169.91:8081/api/health',timeout=8) as response:
        assert response.status == 200
        return {'spring_api_http_status':response.status}

def main():
    assert os.geteuid() == 0
    assert STAGE.parent == pathlib.Path('/home/ubuntu') and STAGE.name.startswith('card-advisor-incoming-')
    assert pathlib.Path('/var/lib/cloud/data/instance-id').read_text().strip() == 'i-04208c6e8df9328d9'
    assert not APP.exists(), 'Preserve an existing card install'
    assert not run(['docker','ps','-aq','--filter','label=com.docker.compose.project='+PROJECT]).strip()
    with socket.socket() as probe:
        probe.bind(('127.0.0.1',19443))
    manifest = json.loads((STAGE/'manifest.json').read_text())
    for name, expected in manifest['files'].items():
        path = STAGE/name
        assert path.parent == STAGE and sha(path) == expected, 'Payload integrity mismatch'
    baseline = inventory()
    running = [x for x in baseline.values() if x['running']]
    stopped = [x for x in baseline.values() if not x['running']]
    assert len(running) == 13 and len(stopped) == 1 and stopped[0]['name'] == '/life-infra-map-storage-init', 'Existing workload inventory changed'
    assert all(x['health'] in (None,'healthy') for x in running), 'Existing running health must be good'
    before = capacity()
    assert before['memory_bytes']['MemAvailable'] >= 1792*1024**2
    assert before['disk_free_bytes'] >= 15*1024**3
    health_before = existing_health()
    for name in [manifest['api_tag'],manifest['db_tag']]:
        assert subprocess.run(['docker','image','inspect',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode != 0, 'Do not replace an existing image tag'
    os.umask(0o077)
    APP.mkdir(mode=0o755)
    evidence = APP/'evidence'/manifest['release']
    evidence.mkdir(parents=True)
    report = {'release':manifest['release'], 'source_commit':manifest['source_commit'], 'app_source_commit':manifest['app_source_commit'], 'started_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'capacity_before':before, 'existing_before':baseline, 'existing_health_before':health_before, 'public_exposure':False, 'real_financial_configuration':False, 'success':False}
    compose = ['docker','compose','--project-name',PROJECT,'--env-file',str(APP/'runtime.env'),'-f',str(APP/'compose.yaml'),'-f',str(APP/'compose.images.yaml')]
    started = False
    try:
        print('Loading verified images; no existing container changes',flush=True)
        run(['docker','load','-i',str(STAGE/'images.tar.gz')],timeout=240,log=evidence/'image-load.log')
        for tag, expected in [(manifest['api_tag'],manifest['api_id']),(manifest['db_tag'],manifest['db_id'])]:
            assert run(['docker','image','inspect','--format','{{.Id}}',tag]).strip() == expected
        for name in ['compose.yaml','compose.images.yaml','manifest.json']:
            shutil.copyfile(STAGE/name,APP/name)
        secret_dir = APP/'secrets'
        secret_dir.mkdir(mode=0o700)
        for name in ['DB_PASSWORD','TLS_KEYSTORE_PASSWORD']:
            path = secret_dir/name
            path.write_text(secrets.token_urlsafe(36))
            path.chmod(0o400)
            os.chown(path,10001,10001)
        private_key = secret_dir/'new-private.key'
        public_dir = APP/'certificates'
        public_dir.mkdir(mode=0o755)
        cert = public_dir/'localhost.crt'
        run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(private_key),'-out',str(cert),'-days','365','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],log=evidence/'certificate-generation.log')
        run(['openssl','pkcs12','-export','-inkey',str(private_key),'-in',str(cert),'-out',str(secret_dir/'tls.p12'),'-name','card-advisor-private','-passout','file:'+str(secret_dir/'TLS_KEYSTORE_PASSWORD')],log=evidence/'keystore-generation.log')
        (secret_dir/'tls.p12').chmod(0o400)
        os.chown(secret_dir/'tls.p12',10001,10001)
        assert private_key.parent == secret_dir and private_key.name == 'new-private.key'
        private_key.unlink()
        cert.chmod(0o644)
        (APP/'runtime.env').write_text('CARD_API_IMAGE='+manifest['api_tag']+'\nCARD_SECRETS_DIR='+str(secret_dir)+'\n')
        config = json.loads(run(compose+['config','--format','json']))
        assert config['name'] == PROJECT and set(config['services']) == {'api','db'}
        assert config['services']['api']['ports'][0]['host_ip'] == '127.0.0.1'
        assert not config['services']['db'].get('ports')
        for name, limit, cpu in [('api',768*1024**2,.5),('db',384*1024**2,.25)]:
            service = config['services'][name]
            assert int(service['mem_limit']) == int(service['memswap_limit']) == limit and float(service['cpus']) == cpu
        assert config['networks']['private_db']['internal']
        assert all(not v.get('external') for v in config['networks'].values())
        assert all(not v.get('external') for v in config['volumes'].values())
        assert inventory(list(baseline)) == baseline, 'Existing containers changed before launch'
        print('Starting only card-advisor-cohost API and new private DB',flush=True)
        started = True
        run(compose+['up','-d','--no-build','--pull','never','--wait','--wait-timeout','180','db','api'],timeout=240,log=evidence/'startup.log')
        ids = run(compose+['ps','-q']).split()
        deployed = json.loads(run(['docker','inspect',*ids]))
        by_service = {x['Config']['Labels']['com.docker.compose.service']:x for x in deployed}
        assert set(by_service) == {'api','db'}
        for name, expected in [('api',manifest['api_id']),('db',manifest['db_id'])]:
            item = by_service[name]
            assert item['Image'] == expected and item['Config']['Labels']['com.docker.compose.project'] == PROJECT
            assert item['State']['Health']['Status'] == 'healthy'
            assert all(k.startswith(PROJECT+'_') for k in item['NetworkSettings']['Networks'])
        assert by_service['api']['HostConfig']['PortBindings'] == {'18443/tcp':[{'HostIp':'127.0.0.1','HostPort':'19443'}]}
        assert not by_service['db']['HostConfig']['PortBindings']
        actual_env = dict(x.split('=',1) for x in by_service['api']['Config']['Env'] if '=' in x)
        for key in ['CODEF_CONFIG_FILE','CODEF_STORAGE_KEY_FILE','OPENAI_CONFIG_FILE','ACCOUNT_MAIL_CONFIG_FILE']:
            assert actual_env[key] == ''
        assert 'DB_PASSWORD' not in actual_env and 'TLS_KEYSTORE_PASSWORD' not in actual_env
        ctx = ssl.create_default_context(cafile=str(cert))
        original_urlopen = urllib.request.urlopen
        def trusted_urlopen(*args, **kwargs):
            kwargs.setdefault('context',ctx)
            return original_urlopen(*args,**kwargs)
        source = (STAGE/'api_smoke.py').read_text()
        source = source.replace('BASE="http://127.0.0.1:18080"','BASE="https://127.0.0.1:19443"')
        source = source.replace('out=pathlib.Path(__file__).resolve().parents[1]/".run"/"api-smoke.json"','out=pathlib.Path('+repr(str(evidence/'api-smoke.json'))+')')
        print('Checking HTTPS synthetic API-to-DB flow and existing service health',flush=True)
        urllib.request.urlopen = trusted_urlopen
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                exec(compile(source,str(STAGE/'api_smoke.py'),'exec'),{'__file__':str(STAGE/'api_smoke.py')})
        finally:
            urllib.request.urlopen = original_urlopen
        report['smoke'] = json.loads((evidence/'api-smoke.json').read_text())
        assert report['smoke']['count'] == 16
        counts = run(['docker','exec',by_service['db']['Id'],'psql','-U','card_advisor','-d','card_advisor','-At','-c','SELECT (SELECT count(*) FROM app_users),(SELECT count(*) FROM cards),(SELECT count(*) FROM ledger_entries),(SELECT count(*) FROM financial_transactions),(SELECT max(version::int) FROM flyway_schema_history WHERE success);']).strip().split('|')
        assert list(map(int,counts)) == [0,0,0,0,29]
        report['new_db_after_smoke'] = {'users':0,'cards':0,'ledger_entries':0,'financial_transactions':0,'flyway_version':29}
        report['runtime'] = {}
        for name, limit, quota in [('api',768*1024**2,50000),('db',384*1024**2,25000)]:
            item = by_service[name]
            assert item['HostConfig']['Memory'] == item['HostConfig']['MemorySwap'] == limit
            text = run(['docker','exec',item['Id'],'sh','-c','cat /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.swap.max /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.events /sys/fs/cgroup/cpu.max'])
            lines = text.strip().splitlines()
            events = dict((k,int(v)) for k,v in (line.split() for line in lines[3:-1]))
            assert int(lines[0]) == limit and int(lines[1]) == 0 and events['oom'] == events['oom_kill'] == 0
            assert list(map(int,lines[-1].split())) == [quota,100000]
            report['runtime'][name] = {'container_id':item['Id'],'image_id':item['Image'],'memory_limit_bytes':limit,'memory_peak_mib':round(int(lines[2])/1024**2,2),'cpu_quota':quota,'cpu_period':100000,'oom_events':events['oom'],'oom_kills':events['oom_kill']}
        for address in ['100.71.169.91','172.31.23.232']:
            try:
                connection = socket.create_connection((address,19443),timeout=2)
            except OSError:
                continue
            connection.close()
            raise AssertionError('Card API accessible through a non-loopback host address')
        report['non_loopback_connection_checks'] = 'refused_on_both_host_private_addresses'
        report['existing_after'] = inventory(list(baseline))
        report['existing_unchanged'] = report['existing_after'] == baseline
        assert report['existing_unchanged']
        report['existing_health_after'] = existing_health()
        report['capacity_after'] = capacity()
        assert report['capacity_after']['memory_bytes']['MemAvailable'] > 768*1024**2
        assert report['capacity_after']['disk_free_bytes'] > 13*1024**3
        final = json.loads(run(['docker','inspect',*ids]))
        assert all(x['State']['Running'] and x['State']['Health']['Status']=='healthy' and x['RestartCount']==0 and not x['State']['OOMKilled'] for x in final)
        report['success'] = True
        report['healthy_new_containers'] = 2
    except Exception as exc:
        report['error'] = type(exc).__name__+': '+str(exc)[:200]
        if started:
            run(compose+['logs','--no-color','--tail','80'],log=evidence/'failure.log')
            own = run(['docker','ps','-q','--filter','label=com.docker.compose.project='+PROJECT]).split()
            if own:
                run(['docker','stop','--time','15',*own],timeout=45,log=evidence/'rollback-card-only.log')
        raise
    finally:
        report['finished_at_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        (evidence/'deployment.json').write_text(json.dumps(report,indent=2))
        print(json.dumps({'success':report['success'],'report':str(evidence/'deployment.json'),'error':report.get('error'),'runtime':report.get('runtime'),'existing_unchanged':report.get('existing_unchanged'),'capacity_after':report.get('capacity_after')},ensure_ascii=False),flush=True)

if __name__ == '__main__':
    main()
