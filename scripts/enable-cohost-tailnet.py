"""Run with sudo on the authorized cohost; preserve existing Serve and containers."""
import datetime, json, pathlib, subprocess, urllib.request

def run(*args):
    return subprocess.check_output(args, text=True, timeout=45)

def inventory():
    # Request only non-secret identity/status/limits; never read environment or Config.
    template='{{json .Id}} {{json .Name}} {{json .Image}} {{json .State.StartedAt}} {{json .State.Running}} {{json .RestartCount}} {{json .HostConfig.Memory}} {{json .HostConfig.NanoCpus}}'
    return sorted(run('docker','inspect','--format',template,*run('docker','ps','-aq').split()).splitlines())

assert pathlib.Path('/var/lib/cloud/data/instance-id').read_text().strip()=='i-04208c6e8df9328d9'
root=pathlib.Path('/opt/card-advisor/evidence/tailnet-20261028')
root.mkdir(exist_ok=True)
before=json.loads(run('tailscale','serve','status','--json'))
assert '19443' not in before.get('TCP',{}), 'Card port already configured; inspect instead of replacing'
containers=inventory()
(root/'before.json').write_text(json.dumps({'serve':before,'containers':containers},indent=2))
run('tailscale','serve','--bg','--https=19443','https+insecure://127.0.0.1:19443')
after=json.loads(run('tailscale','serve','status','--json'))
host='life-infra-map-db.taile29cc8.ts.net:19443'
assert after['TCP']['19443']=={'HTTPS':True}
assert after['Web'][host]['Handlers']['/']['Proxy']=='https+insecure://127.0.0.1:19443'
assert not after.get('AllowFunnel',{}).get(host,False)
preserved=json.loads(json.dumps(after))
del preserved['TCP']['19443'];del preserved['Web'][host]
assert preserved==before, 'Unexpected change in existing Serve settings'
assert inventory()==containers, 'Container state changed'
with urllib.request.urlopen('http://100.71.169.91:8081/api/health',timeout=10) as response:
    assert response.status==200
report={'time':datetime.datetime.now(datetime.timezone.utc).isoformat(),'url':'https://'+host,
    'card_funnel':False,'existing_serve_preserved':True,'all_containers_preserved':True,
    'existing_service_health':200,'after':after}
(root/'result.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='after'}))
