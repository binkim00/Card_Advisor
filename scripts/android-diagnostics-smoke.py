"""Verify private, redacted debug records. No screenshots, UI dumps, or raw test logs saved."""
import argparse, hashlib, http.client, http.server, io, json, os, pathlib, re, subprocess, tarfile, threading, uuid, zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
ADB=str(pathlib.Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')
DOCKER=r'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
parser=argparse.ArgumentParser();parser.add_argument('--device',default='emulator-5554');args=parser.parse_args()
batch=str(uuid.uuid4());out=ROOT/'.run/diagnostics'/batch;out.mkdir(parents=True)
(ROOT/'.run/diagnostics-current-run.txt').write_text(batch)
injected=False

class Proxy(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def reply(self,status,body):
  self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def do_GET(self):self.forward()
 def do_POST(self):self.forward()
 def forward(self):
  global injected
  body=self.rfile.read(int(self.headers.get('Content-Length','0')))
  if self.path.startswith('/v1/fixed-costs?') and not injected:
   injected=True;return self.reply(500,b'{"error":{"code":"SYNTHETIC_FAULT"}}')
  connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=15)
  try:
   connection.request(self.command,self.path,body=body,headers={k:v for k,v in self.headers.items() if k.lower() in ('authorization','content-type')})
   response=connection.getresponse();self.reply(response.status,response.read())
  finally:connection.close()

def adb(*command,check=True,binary=False,timeout=120):
 return subprocess.run([ADB,'-s',args.device,*command],capture_output=True,text=not binary,check=check,timeout=timeout,**({} if binary else dict(encoding='utf-8',errors='replace')))
def sql(query):
 return subprocess.run([DOCKER,'--host','npipe:////./pipe/dockerDesktopLinuxEngine','compose','exec','-T','db','psql','-U','card_advisor','-d','card_advisor','-At','-v','ON_ERROR_STOP=1','-c',query],cwd=ROOT,text=True,capture_output=True,check=True,timeout=20).stdout.strip()
def capture():
 from export_debug_diagnostics import capture as export_capture
 return export_capture(args.device,out/'records')

def latest(saved):return saved[sorted(saved)[-1]]
def records(files):
 return [json.loads(line) for name in ('events.previous.jsonl','events.jsonl') for line in files.get(name,b'').splitlines()]

server=http.server.ThreadingHTTPServer(('127.0.0.1',18081),Proxy);server.daemon_threads=True
threading.Thread(target=server.serve_forever,daemon=True).start()
summary={'batch':batch,'device':args.device,'syntheticOnly':True,'cases':[]}
fatal_saved=None
try:
 for apk in ('debug/app-debug.apk','androidTest/debug/app-debug-androidTest.apk'):
  adb('install','-r',str(ROOT/'android/app/build/outputs/apk'/apk))
 adb('reverse','tcp:18080','tcp:18081')
 for method in ('privacyRotationAndFirstFailure','fixedEntryReadFailureRecoveryAndClose','fatalProbe','restartAndNormalClose'):
  case=str(uuid.uuid4());email=f'diag-{case}@example.test'
  adb('shell','am','force-stop','dev.cardadvisor.android');assert not adb('shell','pidof','dev.cardadvisor.android',check=False).stdout.strip()
  try:
   result=adb('shell','am','instrument','-w','-r','-e','class',f'dev.cardadvisor.android.DebugDiagnosticsTest#{method}','-e','runId',case,'dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner',check=False)
   # Raw instrumentation output can include assertion values. Keep it in memory only.
   passed='OK (1 test)' in result.stdout
   saved=capture();files=latest(saved)
   if method=='fatalProbe':
    assert not passed and not adb('shell','pidof','dev.cardadvisor.android',check=False).stdout.strip(),'Fatal probe did not terminate normally'
    early=json.loads(files['first-exception.json']);fatal=json.loads(files['first-uncaught.json'])
    assert early['exceptions'][0]['type']=='org.json.JSONException'
    assert fatal['exceptions'][0]['type']=='java.lang.ArrayIndexOutOfBoundsException' and fatal['fatal']
    assert early['sequence']<fatal['sequence']
    fatal_saved={name:dict(values) for name,values in saved.items() if 'first-uncaught.json' in values}
   else:assert passed,'Instrumentation assertion failed; only redacted diagnostic records retained'
   if method=='privacyRotationAndFirstFailure':
    assert 'events.previous.jsonl' in files and 'first-exception.json' in files
   if method=='fixedEntryReadFailureRecoveryAndClose':
    assert injected
    events=records(files);names=[r['event'] for r in events]
    for required in ('ACTIVITY_CREATED','FIXED_ENTER','FIXED_LOAD_START','EXCEPTION','FIXED_LOAD_OK','FIXED_EXIT','ACTIVITY_DESTROYED'):assert required in names
    first=json.loads(files['first-exception.json']);assert first['site']=='FIXED_OPERATION' and first['exceptions'][0]['type']=='dev.cardadvisor.android.ApiException'
    snapshot=json.loads(sql(f"WITH u AS (SELECT id FROM app_users WHERE email='{email}') SELECT json_build_object('users',(SELECT count(*) FROM u),'budgets',(SELECT count(*) FROM budgets WHERE user_id IN (SELECT id FROM u)),'plans',(SELECT count(*) FROM fixed_cost_plans WHERE user_id IN (SELECT id FROM u)),'ledger',(SELECT count(*) FROM ledger_entries WHERE user_id IN (SELECT id FROM u)));"))
    assert snapshot=={'users':1,'budgets':1,'plans':0,'ledger':0}
    summary['database']=snapshot
    summary['flowEvents']=names
   if method=='restartAndNormalClose':
    assert fatal_saved
    for name,values in fatal_saved.items():
     for file in ('first-exception.json','first-uncaught.json'):assert saved[name][file]==values[file]
    summary['fatalRecordsSurvivedRestart']=True
   summary['cases'].append({'method':method,'passed':True,'expectedProcessDeath':method=='fatalProbe'})
   print(json.dumps(summary['cases'][-1]),flush=True)
  finally:
   if method=='fixedEntryReadFailureRecoveryAndClose':
    sql(f"DELETE FROM app_users WHERE email='{email}';")
    assert sql(f"SELECT count(*) FROM app_users WHERE email='{email}';")=='0'
    summary['syntheticAccountDeleted']=True
  (out/'verification.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
 release=ROOT/'android/app/build/outputs/apk/release/app-release-unsigned.apk'
 with zipfile.ZipFile(release) as archive:
  dex=b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
  for forbidden in (b'DebugDiagnosticsApplication',b'DiagnosticStore',b'debug-diagnostics'):
   assert forbidden not in dex,'Debug recorder leaked into release APK'
 summary.update(releaseRecorderAbsent=True,debugApkSha256=hashlib.sha256((ROOT/'android/app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest(),status='PASSED')
 (out/'verification.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
 print(json.dumps({'status':'PASSED','evidence':str(out),'releaseRecorderAbsent':True}),flush=True)
finally:
 adb('shell','am','force-stop','dev.cardadvisor.android',check=False)
 adb('reverse','tcp:18080','tcp:18080',check=False)
 server.shutdown();server.server_close()
