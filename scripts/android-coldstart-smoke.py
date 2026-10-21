"""Serial, cold-process Android tests. Local fault proxy; credentials never logged."""
import argparse, hashlib, http.client, http.server, json, os, pathlib, re, socket, subprocess, threading, uuid

ROOT=pathlib.Path(__file__).resolve().parents[1]
ADB=str(pathlib.Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')
DOCKER=r'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
parser=argparse.ArgumentParser()
parser.add_argument('--device',default='emulator-5554')
parser.add_argument('--modes',default='healthy,http500,malformed,missing_field,bad_item,disconnect,delay_close')
parser.add_argument('--allow-failure',action='store_true')
parser.add_argument('--apk',type=pathlib.Path,help='Optional preserved debug APK for before/after diagnosis')
args=parser.parse_args()
MODES={'healthy','http500','malformed','missing_field','bad_item','disconnect','delay_close'}
assert set(args.modes.split(','))<=MODES
state={'mode':'healthy','injected':False,'fixedRequests':0}
release=threading.Event()

class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def response(self,status,body):
  if isinstance(body,(dict,list)):body=json.dumps(body).encode()
  self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers()
  try:self.wfile.write(body)
  except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError):pass
 def do_GET(self):self.handle_request()
 def do_POST(self):self.handle_request()
 def do_PUT(self):self.handle_request()
 def handle_request(self):
  body=self.rfile.read(int(self.headers.get('Content-Length','0')))
  if self.path=='/__test/state':return self.response(200,state)
  if self.path=='/__test/release':release.set();return self.response(200,{})
  if self.path=='/__test/recover':state['mode']='healthy';return self.response(200,{})
  if self.path.startswith('/v1/fixed-costs?'):
   state['fixedRequests']+=1
   if state['mode']=='disconnect':
    state['injected']=True;self.connection.shutdown(socket.SHUT_RDWR);self.connection.close();return
   if not state['injected'] and state['mode']!='healthy':
    state['injected']=True;mode=state['mode']
    if mode=='http500':return self.response(500,{'error':{'code':'TEST_FAULT'}})
    if mode=='malformed':return self.response(200,b'{')
    if mode=='missing_field':return self.response(200,{'items':[]})
    if mode=='bad_item':
     month=self.path.split('month=')[1]
     return self.response(200,{'month':month,'mode':'MANUAL','scheduledRemainingWon':10000,'manualRemainingWon':30000,'appliedRemainingWon':30000,'items':[{'id':str(uuid.uuid4()),'version':1,'name':'합성 응답 일정','amountWon':10000,'category':'OTHER','dayOfMonth':1,'active':True,'dueDate':month+'-01','includedNetWon':0,'payments':[]}]})
    if mode=='delay_close':release.wait(15)
  connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=20)
  try:
   headers={k:v for k,v in self.headers.items() if k.lower() in ('authorization','content-type')}
   connection.request(self.command,self.path,body=body,headers=headers)
   response=connection.getresponse();self.response(response.status,response.read())
  finally:connection.close()

def adb(*command,check=True,timeout=120):
 return subprocess.run([ADB,'-s',args.device,*command],text=True,encoding='utf-8',errors='replace',capture_output=True,check=check,timeout=timeout)
def sql(query):
 r=subprocess.run([DOCKER,'--host','npipe:////./pipe/dockerDesktopLinuxEngine','compose','exec','-T','db','psql','-U','card_advisor','-d','card_advisor','-At','-v','ON_ERROR_STOP=1','-c',query],cwd=ROOT,capture_output=True,text=True,check=True,timeout=20)
 return r.stdout.strip()

server=http.server.ThreadingHTTPServer(('127.0.0.1',18081),Handler)
server.daemon_threads=True
threading.Thread(target=server.serve_forever,daemon=True).start()
batch=str(uuid.uuid4());base=ROOT/'.run/coldstart'/batch;base.mkdir(parents=True)
(ROOT/'.run/coldstart-current-run.txt').write_text(batch)
results=[]
app_apk=args.apk or ROOT/'android/app/build/outputs/apk/debug/app-debug.apk'
app_hash=hashlib.sha256(app_apk.read_bytes()).hexdigest()
try:
 for apk in [app_apk,ROOT/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
  adb('install','-r',str(apk))
 adb('reverse','tcp:18080','tcp:18081')
 for mode in args.modes.split(','):
  case=str(uuid.uuid4());folder=base/case;folder.mkdir()
  state.update(mode=mode,injected=False,fixedRequests=0);release.clear()
  adb('shell','am','force-stop','dev.cardadvisor.android')
  absent=not adb('shell','pidof','dev.cardadvisor.android',check=False).stdout.strip()
  assert absent,'App process did not stop'
  result=adb('shell','am','instrument','-w','-r','-e','class','dev.cardadvisor.android.FixedCostColdStartTest','-e','runId',case,'-e','fault',mode,'dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner',check=False)
  (folder/'instrumentation.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
  match=re.search(r'INSTRUMENTATION_STATUS: stack=(.*?)(?=\nINSTRUMENTATION_STATUS:|\nINSTRUMENTATION_RESULT:|\Z)',result.stdout,re.S)
  if match:(folder/'first-reported-exception.txt').write_text(match.group(1),encoding='utf-8')
  adb('pull',f'/sdcard/Android/data/dev.cardadvisor.android/files/coldstart/{case}/.',str(folder),check=False)
  passed=result.returncode==0 and 'OK (1 test)' in result.stdout
  email=f'cold-{case}@example.test'
  snapshot=json.loads(sql(f"WITH u AS (SELECT id FROM app_users WHERE email='{email}') SELECT json_build_object('users',(SELECT count(*) FROM u),'plans',(SELECT count(*) FROM fixed_cost_plans WHERE user_id IN (SELECT id FROM u)),'ledger',(SELECT count(*) FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),'budget',(SELECT payload FROM budgets WHERE user_id IN (SELECT id FROM u) LIMIT 1),'recommendations',(SELECT count(*) FROM recommendations WHERE user_id IN (SELECT id FROM u)));") )
  (folder/'database.json').write_text(json.dumps(snapshot,ensure_ascii=False,indent=2),encoding='utf-8')
  if passed:
   assert snapshot['users']==1 and snapshot['plans']==0 and snapshot['ledger']==0
   assert snapshot['budget']['totalWon']==120000 and snapshot['budget']['fixedRemainingWon']==30000
   assert snapshot['recommendations']==(0 if mode=='delay_close' else 1)
  # Keep diagnostics before removing only the account created by this exact test.
  deleted=int(sql(f"WITH d AS (DELETE FROM app_users WHERE email='{email}' RETURNING id) SELECT count(*) FROM d;"))
  assert deleted==snapshot['users']
  assert sql(f"SELECT count(*) FROM app_users WHERE email='{email}';")=='0'
  entry=dict(runId=case,mode=mode,passed=passed,processAbsentBeforeStart=absent,proxy=dict(state),deleted=deleted)
  results.append(entry);(base/'verification.json').write_text(json.dumps(dict(batch=batch,device=args.device,apkSha256=app_hash,cases=results),indent=2),encoding='utf-8')
  print(json.dumps(entry),flush=True)
  if not passed:
   for name in ['first-reported-exception.txt','teardown.txt','uncaught.txt']:
    path=folder/name
    if path.exists():print(name+': '+path.read_text(encoding='utf-8')[:6500],flush=True)
   if not args.allow_failure:raise RuntimeError('Cold-start test failed; evidence preserved')
finally:
 release.set();adb('shell','am','force-stop','dev.cardadvisor.android',check=False)
 adb('reverse','tcp:18080','tcp:18080',check=False)
 server.shutdown();server.server_close()
print('Evidence: '+str(base))
