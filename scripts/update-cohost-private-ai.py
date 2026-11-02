import pathlib,subprocess,json,os,time,ssl,urllib.request,uuid,hashlib,datetime
APP=pathlib.Path('/opt/card-advisor');STAGE=pathlib.Path(__file__).resolve().parent;API='card-advisor-cohost-api-1'
def run(args):
 p=subprocess.run(args,capture_output=True,text=True,timeout=180)
 if p.returncode:raise RuntimeError('COMMAND_FAILED')
 return p.stdout
def inventory():
 ids=run(['docker','ps','-aq']).split();f='{{json .Id}} {{json .Name}} {{json .Image}} {{json .State.StartedAt}} {{json .State.Running}} {{json .RestartCount}} {{json .HostConfig.Memory}} {{json .HostConfig.MemorySwap}} {{json .HostConfig.NanoCpus}}'
 return sorted(run(['docker','inspect','--format',f,*ids]).splitlines())
def oldhealth():
 with urllib.request.urlopen('http://100.71.169.91:8081/api/health',timeout=10) as r:assert r.status==200
assert os.geteuid()==0 and pathlib.Path('/var/lib/cloud/data/instance-id').read_text().strip()=='i-04208c6e8df9328d9'
assert STAGE.parent==pathlib.Path('/home/ubuntu') and STAGE.name.startswith('card-advisor-ai-incoming-')
m=json.loads((STAGE/'manifest.json').read_text());assert hashlib.sha256((STAGE/'api.tar').read_bytes()).hexdigest()==m['archiveSha256']
assert run(['docker','inspect','--format','{{.Image}}',API]).strip()==m['previousImageId']
oldhealth();before=inventory();preserved=[x for x in before if '"/'+API+'"' not in x];serve=run(['tailscale','serve','status','--json'])
ev=APP/'evidence'/('ai-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));ev.mkdir(mode=0o700)
(ev/'before.json').write_text(json.dumps({'containers':before,'serve':json.loads(serve)},indent=2))
runtime=(APP/'runtime.env').read_text();(ev/'runtime.env').write_text(runtime);os.chmod(ev/'runtime.env',0o600)
base=['docker','compose','--env-file',str(APP/'runtime.env'),'-f',str(APP/'compose.yaml'),'-f',str(APP/'compose.images.yaml'),'-f',str(APP/'compose.preview.yaml')]
override=APP/'compose.ai.yaml';assert not override.exists()
key=APP/'secrets/openai-api-key.txt';config=APP/'secrets/openai.properties';assert not key.exists() and not config.exists()
assert (STAGE/'openai-api-key.txt').stat().st_size>0
os.replace(STAGE/'openai-api-key.txt',key);key.write_text(key.read_text(encoding='utf-8-sig').strip());os.chown(key,10001,10001);os.chmod(key,0o400)
config.write_text('enabled=true\nmodel=gpt-4.1-mini\napiKeyFile=/run/secrets/openai-api-key.txt\n');os.chown(config,10001,10001);os.chmod(config,0o400)
override.write_text('services:\n  api:\n    environment:\n      OPENAI_CONFIG_FILE: /run/secrets/openai.properties\n    secrets:\n      - source: OPENAI_CONFIG\n        target: openai.properties\n      - source: OPENAI_KEY\n        target: openai-api-key.txt\nsecrets:\n  OPENAI_CONFIG:\n    file: /opt/card-advisor/secrets/openai.properties\n  OPENAI_KEY:\n    file: /opt/card-advisor/secrets/openai-api-key.txt\n')
report={'sourceCommit':m['sourceCommit'],'success':False,'financeEnabled':False,'existingDbAccessed':False,'apkRebuildRequired':False}
ctx=ssl.create_default_context(cafile=str(APP/'certificates/localhost.crt'));token=None
def api(path,method='GET',body=None,extra=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 if extra:h.update(extra)
 req=urllib.request.Request('https://localhost:19443/v1'+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
 with urllib.request.urlopen(req,context=ctx,timeout=15) as r:return json.load(r)
def check_ai(value):
 assert value['source']=='OPENAI_VALIDATED' and value['status']=='READY'
 assert value['paragraphs'] and all(p in [f['text'] for f in value['facts']] for p in value['paragraphs'])
try:
 run(['docker','load','-i',str(STAGE/'api.tar')]);assert run(['docker','image','inspect','--format','{{.Id}}',m['imageTag']]).strip()==m['imageId']
 (APP/'runtime.env').write_text('\n'.join('CARD_API_IMAGE='+m['imageTag'] if s.startswith('CARD_API_IMAGE=') else s for s in runtime.splitlines())+'\n')
 command=base+['-f',str(override)];run(command+['config','--quiet']);run(command+['up','-d','--no-deps','--pull','never','api'])
 for _ in range(45):
  try:
   if api('/health')['status']=='UP':break
  except Exception:time.sleep(3)
 else:raise RuntimeError('HEALTH_FAILED')
 c=json.loads(run(['docker','inspect',API]))[0];env=dict(x.split('=',1) for x in c['Config']['Env'] if '=' in x)
 assert all(not env.get(k,'') for k in ['CODEF_CONFIG_FILE','CODEF_STORAGE_KEY_FILE','ACCOUNT_MAIL_CONFIG_FILE'])
 assert c['HostConfig']['Memory']==805306368 and c['HostConfig']['MemorySwap']==805306368 and c['HostConfig']['NanoCpus']==500000000
 session=api('/auth/register','POST',{'email':'ai-deploy-'+uuid.uuid4().hex+'@example.test','password':uuid.uuid4().hex+'-Synthetic'});token=session['accessToken']
 try:
  month=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).strftime('%Y-%m')
  api('/demo/setup','POST')
  assert api('/insights?month='+month)['assistance']['status']=='CONSENT_REQUIRED'
  api('/me/consents/AI','PUT',{'accepted':True,'policyVersion':'2026-10-19-v1','expectedVersion':0})
  checkout=api('/checkout-sessions','POST',{'orderValueWon':50000,'pointUsedWon':0,'prepaidMoneyUsedWon':0,'cardChargeWon':50000,'category':'SHOPPING','paymentMethod':'NAVER_PAY_CARD','installments':1,'splitApproval':'NO','confirmedByUser':True})
  recommendation=api('/recommendations','POST',{'checkoutId':checkout['id'],'snapshotVersion':1,'mode':'BENEFIT','maxOpportunityCostWon':0})
  check_ai(api('/recommendations/'+recommendation['id']+'/explanation','POST'))
  check_ai(api('/insights?month='+month)['assistance'])
  api('/me/consents/AI','PUT',{'accepted':False,'policyVersion':'2026-10-19-v1','expectedVersion':1})
  assert api('/insights?month='+month)['assistance']['status']=='CONSENT_REQUIRED'
  status=api('/finance/codef/status');assert status['privatePreviewEnabled'] and status['syntheticProvider'] and not status['realInstitutionAccessEnabled']
  connection=api('/demo/finance-preview','POST',{})['connection']
  held=api('/finance/connections/'+connection['id']+'/held-cards/refresh','POST',{'confirmed':True,'expectedConnectionVersion':connection['version']});assert len(held['cards'])==2
  report['actualServerAiExplanationAndInsights']=True;report['consentAndWithdrawalPassed']=True;report['syntheticHeldCardsPassed']=True
 finally:
  api('/me','DELETE',extra={'X-Confirm-Delete':'DELETE_ACCOUNT'});token=None
 assert [x for x in inventory() if '"/'+API+'"' not in x]==preserved;oldhealth();assert run(['tailscale','serve','status','--json'])==serve
 for _ in range(20):
  if run(['docker','inspect','--format','{{.State.Health.Status}}',API]).strip()=='healthy':break
  time.sleep(2)
 assert run(['docker','inspect','--format','{{.State.Health.Status}} {{.State.OOMKilled}} {{.RestartCount}}',API]).strip()=='healthy false 0'
 report.update({'success':True,'existingContainersPreserved':True,'tailscalePreserved':True,'memoryMiB':768,'evidence':str(ev),'imageId':m['imageId']})
except Exception as error:
 report['failureType']=type(error).__name__
 (APP/'runtime.env').write_text(runtime)
 run(base+['up','-d','--no-deps','--pull','never','api'])
 report['rolledBack']=True
finally:
 (ev/'result.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
raise SystemExit(0 if report['success'] else 1)
