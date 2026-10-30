"""Authorized update of /opt/card-advisor only. Run as root from the upload directory."""
import datetime,hashlib,json,os,pathlib,secrets,shutil,ssl,subprocess,time,urllib.request,uuid
STAGE=pathlib.Path(__file__).resolve().parent;APP=pathlib.Path('/opt/card-advisor');DB='card-advisor-cohost-db-1';API='card-advisor-cohost-api-1'
def run(args,timeout=90):
 p=subprocess.run(args,capture_output=True,text=True,timeout=timeout)
 if p.returncode:raise RuntimeError('Command failed: '+' '.join(args[:3]))
 return p.stdout
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def inventory():
 ids=run(['docker','ps','-aq']).split();fmt='{{json .Id}} {{json .Name}} {{json .Image}} {{json .State.StartedAt}} {{json .State.Running}} {{json .RestartCount}} {{json .HostConfig.Memory}} {{json .HostConfig.MemorySwap}} {{json .HostConfig.NanoCpus}}'
 return sorted(run(['docker','inspect','--format',fmt,*ids]).splitlines())
def health_existing():
 with urllib.request.urlopen('http://100.71.169.91:8081/api/health',timeout=10) as r:assert r.status==200
assert os.geteuid()==0 and pathlib.Path('/var/lib/cloud/data/instance-id').read_text().strip()=='i-04208c6e8df9328d9'
assert STAGE.parent==pathlib.Path('/home/ubuntu') and STAGE.name.startswith('card-advisor-preview-incoming-')
m=json.loads((STAGE/'manifest.json').read_text());assert sha(STAGE/'api.tar')==m['imageArchiveSha256'] and sha(STAGE/'app.apk')==m['apkSha256']
assert shutil.disk_usage('/').free>15*1024**3
before=inventory();preserved=[x for x in before if '"/'+API+'"' not in x];serve=json.loads(run(['tailscale','serve','status','--json']));health_existing()
assert run(['docker','inspect','--format','{{.Image}}',API]).strip()==m['expectedPreviousImageId'],'Card API changed since preflight'
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ');evidence=APP/'evidence'/('preview-'+stamp);evidence.mkdir(mode=0o700)
(evidence/'before.json').write_text(json.dumps({'containers':before,'serve':serve},indent=2))
report={'sourceCommit':m['sourceCommit'],'imageId':m['imageId'],'apkSha256':m['apkSha256'],'success':False,'existingDatabaseAccessed':False,'realInstitutionAccessEnabled':False}
old_env=(APP/'runtime.env').read_text();(evidence/'runtime-before.env').write_text(old_env);os.chmod(evidence/'runtime-before.env',0o600)
base=['docker','compose','--env-file',str(APP/'runtime.env'),'-f',str(APP/'compose.yaml'),'-f',str(APP/'compose.images.yaml')]
def query(sql):return run(['docker','exec',DB,'psql','-U','card_advisor','-d','card_advisor','-At','-v','ON_ERROR_STOP=1','-c',sql]).strip()
print('Backing up card-only database and checking restore',flush=True)
backup=evidence/'card-before.pgdump'
with backup.open('wb') as f:subprocess.run(['docker','exec',DB,'pg_dump','-U','card_advisor','-d','card_advisor','-Fc'],stdout=f,check=True,timeout=90)
os.chmod(backup,0o600)
restore='card-advisor-restore-'+uuid.uuid4().hex[:10]
try:
 image=run(['docker','inspect','--format','{{.Config.Image}}',DB]).strip()
 run(['docker','run','-d','--rm','--name',restore,'--network','none','--memory','256m','--memory-swap','256m','--cpus','0.25','-e','POSTGRES_HOST_AUTH_METHOD=trust','-e','POSTGRES_DB=verify_restore',image])
 for _ in range(30):
  if subprocess.run(['docker','exec',restore,'pg_isready','-U','postgres'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0:break
  time.sleep(1)
 with backup.open('rb') as f:subprocess.run(['docker','exec','-i',restore,'pg_restore','--no-owner','--no-privileges','-U','postgres','-d','verify_restore'],stdin=f,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True,timeout=90)
 assert run(['docker','exec',restore,'psql','-U','postgres','-d','verify_restore','-At','-c','SELECT max(version::int) FROM flyway_schema_history WHERE success;']).strip()=='29'
 report['cardBackupRestoreVerified']=True
finally:run(['docker','stop',restore])
backup_key=evidence/'backup-key';backup_key.write_text(secrets.token_urlsafe(48));os.chmod(backup_key,0o400)
run(['openssl','enc','-aes-256-cbc','-pbkdf2','-salt','-in',str(backup),'-out',str(backup)+'.enc','-pass','file:'+str(backup_key)]);backup.unlink()
preview_key=APP/'secrets'/'preview.properties'
if not preview_key.exists():preview_key.write_text('activeKeyId=private-preview-v1\nkeys.private-preview-v1='+__import__('base64').b64encode(secrets.token_bytes(32)).decode()+'\n')
os.chown(preview_key,10001,10001);os.chmod(preview_key,0o400)
override=APP/'compose.preview.yaml'
override.write_text('services:\n  api:\n    environment:\n      SPRING_PROFILES_ACTIVE: prod,synthetic-preview\n      CARD_PREVIEW_KEY_FILE: /run/secrets/preview.properties\n    secrets:\n      - source: CARD_PREVIEW_KEY\n        target: preview.properties\nsecrets:\n  CARD_PREVIEW_KEY:\n    file: /opt/card-advisor/secrets/preview.properties\n')
print('Loading verified image; only the card API will be recreated',flush=True)
run(['docker','load','-i',str(STAGE/'api.tar')],timeout=120)
assert run(['docker','image','inspect','--format','{{.Id}}',m['imageTag']]).strip()==m['imageId']
updated='\n'.join('CARD_API_IMAGE='+m['imageTag'] if line.startswith('CARD_API_IMAGE=') else line for line in old_env.splitlines())+'\n'
assert updated!=old_env;(APP/'runtime.env').write_text(updated)
commands=base+['-f',str(override)]
run(commands+['config','--quiet'])
try:
 run(commands+['up','-d','--no-deps','--pull','never','api'],timeout=120)
 ctx=ssl.create_default_context(cafile=str(APP/'certificates'/'localhost.crt'));endpoint='https://localhost:19443/v1'
 def api(path,method='GET',body=None,token=None,extra=None):
  headers={'Content-Type':'application/json'}
  if token:headers['Authorization']='Bearer '+token
  if extra:headers.update(extra)
  req=urllib.request.Request(endpoint+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
  with urllib.request.urlopen(req,context=ctx,timeout=15) as r:return json.load(r)
 for _ in range(40):
  try:
   if api('/health').get('status')=='UP':break
  except Exception:time.sleep(3)
 else:raise RuntimeError('Card preview health failed')
 assert query('SELECT max(version::int) FROM flyway_schema_history WHERE success;')=='30'
 limits=run(['docker','inspect','--format','{{.HostConfig.Memory}} {{.HostConfig.MemorySwap}} {{.HostConfig.NanoCpus}}',API]).strip();assert limits=='805306368 805306368 500000000'
 blanks=run(['docker','inspect','--format','{{range .Config.Env}}{{if or (eq . "CODEF_CONFIG_FILE=") (eq . "CODEF_STORAGE_KEY_FILE=") (eq . "OPENAI_CONFIG_FILE=") (eq . "ACCOUNT_MAIL_CONFIG_FILE=")}}{{println .}}{{end}}{{end}}',API]).splitlines();assert len(blanks)==4
 print('Checking private preview API with disposable synthetic users',flush=True)
 session=api('/auth/register','POST',{'email':'deploy-preview-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24)});token=session['accessToken']
 status=api('/finance/codef/status',token=token);assert status['privatePreviewEnabled'] and status['syntheticProvider'] and not status['realInstitutionAccessEnabled']
 c=api('/demo/finance-preview','POST',{},token)['connection'];prefix='/finance/connections/'+c['id'];root=prefix+'/held-cards'
 held=api(root+'/refresh','POST',{'expectedConnectionVersion':c['version'],'confirmed':True},token);assert len(held['cards'])==2
 card=held['cards'][0];data={'expectedVersion':card['version'],'expectedConnectionVersion':c['version'],'confirmed':True}
 api(root+'/'+card['id']+'/register','POST',data,token);api(root+'/'+card['id']+'/register','POST',data,token)
 mine=api('/cards',token=token);assert len(mine)==1 and mine[0]['source']=='IMPORTED_UNVERIFIED' and mine[0]['rateBp'] is None
 c=api(prefix,token=token)
 api(prefix+'/disconnect','POST',{'expectedVersion':c['version']},token,{'Idempotency-Key':str(uuid.uuid4())})
 api('/me','DELETE',token=token,extra={'X-Confirm-Delete':'DELETE_ACCOUNT'})
 assert query("SELECT count(*) FROM app_users WHERE id='"+session['userId']+"';")=='0'
 report['serverSyntheticFlowPassed']=True
 assert [x for x in inventory() if '"/'+API+'"' not in x]==preserved;health_existing()
 download=APP/'downloads';download.mkdir(exist_ok=True);target=download/'card-advisor-preview.apk';shutil.copyfile(STAGE/'app.apk',target);os.chmod(target,0o644);assert sha(target)==m['apkSha256']
 run(['tailscale','serve','--bg','--https=19443','--set-path=/download/card-advisor-preview.apk',str(target)])
 after=json.loads(run(['tailscale','serve','status','--json']));host='life-infra-map-db.taile29cc8.ts.net:19443';assert not after.get('AllowFunnel',{}).get(host,False)
 stripped=json.loads(json.dumps(after));del stripped['Web'][host]['Handlers']['/download/card-advisor-preview.apk'];assert stripped==serve
 report.update({'success':True,'schemaVersion':30,'existingContainersAndCardDbPreserved':True,'existingServiceHealth':200,'cardApiLimits':limits,'apkUrl':'https://'+host+'/download/card-advisor-preview.apk','realProviderConfigPathsBlank':True,'evidence':str(evidence)})
except Exception:
 print('Preview update failed; reverting only the card API image/config',flush=True)
 (APP/'runtime.env').write_text(old_env);run(base+['up','-d','--no-deps','--pull','never','api'],timeout=120)
 (evidence/'result.json').write_text(json.dumps(report,indent=2));raise
(evidence/'result.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
