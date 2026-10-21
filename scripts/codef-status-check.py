"""Local readiness only: no provider call, key read, credential output, or real finance access."""
import json, uuid, urllib.request, urllib.error, sys, re
from pathlib import Path
base='http://127.0.0.1:18080/v1'
session=None
password='Readiness-'+str(uuid.uuid4())
def request(path,method='GET',body=None):
    headers={'Content-Type':'application/json'}
    if session: headers['Authorization']='Bearer '+session['accessToken']
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(base+path,data=data,headers=headers,method=method)
    with urllib.request.urlopen(req,timeout=10) as response:
        return json.load(response)
try:
    session=request('/auth/register','POST',{'email':'codef-readiness-'+str(uuid.uuid4())+'@example.test','password':password})
    value=request('/finance/codef/status')
    allowed=['provider','enabled','environment','storageConfigured','userConnectionApiReady','syntheticProvider','automaticSyncEnabled']
    report={key:value.get(key) for key in allowed}
    report['externalCallsMade']=False
    report['actualConnectionVerified']=False
    if '--authenticate' in sys.argv:
        try:
            auth=request('/finance/codef/authentication-check','POST')
            report['oauthVerified']=auth.get('oauthVerified') is True
            report['financialInstitutionAuthenticated']=False
        except urllib.error.HTTPError as error:
            report['oauthVerified']=False
            try:
                detail=json.load(error).get('error',{});code=detail.get('code','UNCONFIRMED')
                status=detail.get('providerHttpStatus')
                if type(status) is int and 100<=status<=599: report['oauthHttpStatus']=status
            except Exception: code='UNCONFIRMED'
            report['oauthErrorCode']=code if isinstance(code,str) and re.fullmatch(r'[A-Z0-9_]{1,80}',code) else 'UNCONFIRMED'
        except Exception:
            report['oauthVerified']=False
            report['oauthErrorCode']='UNCONFIRMED'
        report['externalCallsMade']=True

    if not(report.get('enabled') and report.get('storageConfigured') and report.get('userConnectionApiReady') and report.get('environment')=='DEMO' and report.get('syntheticProvider') is False):
        raise RuntimeError('NOT_READY')
    request('/me/deletion','POST',{'password':password,'confirmation':'계정 삭제'})
    session=None
    report['syntheticProbeAccountDeleted']=True
    target=Path('.run/codef-demo-readiness.json');target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(report,ensure_ascii=False),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
except Exception:
    print('{"status":"LOCAL_READINESS_CHECK_FAILED","secretsPrinted":false}')
    raise SystemExit(1)
finally:
    if session:
        try: request('/me/deletion','POST',{'password':password,'confirmation':'계정 삭제'})
        except Exception: pass
