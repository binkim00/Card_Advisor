"""Real local API/PostgreSQL validation of manual monthly evidence; synthetic accounts only."""
import datetime,json,uuid,urllib.request,urllib.error,pathlib
BASE='http://127.0.0.1:18080';tokens=[];passed=[]
today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).date();month=today.strftime('%Y-%m')
period=(today.replace(day=1)-datetime.timedelta(days=1)).strftime('%Y-%m')
def call(path,method='GET',data=None,token=None,status=200):
    req=urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data).encode(),method=method,headers={'Content-Type':'application/json','Authorization':'Bearer '+(token or ''),'X-Confirm-Delete':'DELETE_ACCOUNT'})
    try:
        with urllib.request.urlopen(req,timeout=10) as r:code,raw=r.status,r.read()
    except urllib.error.HTTPError as e:code,raw=e.code,e.read()
    obj=json.loads(raw) if raw else None
    assert code==status,(path,code,status)
    return obj
try:
    for _ in range(2):tokens.append(call('/v1/auth/register','POST',{'email':f'month-{uuid.uuid4()}@example.test','password':uuid.uuid4().hex},status=201)['accessToken'])
    a,b=tokens;call('/v1/demo/setup','POST',token=a)
    c=call('/v1/cards/performance?month='+month,token=a)[1];id=c['id'];path=f'/v1/cards/{id}/months/{month}'
    def state():return next(c for c in call('/v1/cards/performance?month='+month,token=a) if c['id']==id)
    payload=dict(untrackedPerformanceWon=310000,currentQualified=False,remainingCapWon=10000,version=c['stateVersion'],untrackedSource='USER_INPUT',untrackedAsOf=str(today),qualificationSource='USER_ESTIMATE',qualificationAsOf=str(today),qualificationPeriod=period)
    call(path,'PUT',payload,a);saved=state();assert saved['performanceWon']==310000 and saved['currentQualified'] is False and saved['untrackedAsOf']==str(today) and saved['currentQualificationSource']=='USER_ESTIMATE';passed.append('save_readback_provenance_and_current_future_separation')
    assert call(path,'PUT',payload,a,409)['error']['code']=='VERSION_CONFLICT';assert state()==saved;passed.append('conflict_preserves_latest_state')
    payload['version']=saved['stateVersion']
    for changes in [dict(untrackedSource='OFFICIAL'),dict(untrackedAsOf=str(today+datetime.timedelta(days=1))),dict(qualificationPeriod=month),dict(untrackedPerformanceWon=-1),dict(untrackedPerformanceWon=None),dict(qualificationAsOf=None),dict(version=None)]:
        call(path,'PUT',payload|changes,a,422)
    assert state()==saved;passed.append('invalid_source_date_period_amount_null_version_atomic_rejection')
    call(path,'PUT',payload,b,404);assert call('/v1/cards/performance?month='+month,token=b)==[];passed.append('tenant_isolation')
    order=dict(orderValueWon=50000,pointUsedWon=0,prepaidMoneyUsedWon=0,cardChargeWon=50000,category='SHOPPING',paymentMethod='NAVER_PAY_CARD',installments=1,splitApproval='NO',confirmedByUser=True)
    co=call('/v1/checkout-sessions','POST',order,a);rec=call('/v1/recommendations','POST',dict(checkoutId=co['id'],snapshotVersion=1,mode='BENEFIT',maxOpportunityCostWon=0),a)
    call(path,'PUT',payload|dict(untrackedPerformanceWon=0),a)
    assert call('/v1/recommendations/'+rec['id'],token=a)['validity']=='INVALIDATED';passed.append('monthly_edit_invalidates_recommendations')
    payload.update(version=state()['stateVersion'],untrackedPerformanceWon=None,currentQualified=None,untrackedSource='UNKNOWN',untrackedAsOf=None,qualificationSource='UNKNOWN',qualificationAsOf=None)
    call(path,'PUT',payload,a);saved=state();assert saved['performanceWon'] is None and saved['currentQualified'] is None and saved['currentQualificationSource']=='UNKNOWN';passed.append('unknown_is_not_zero_or_synthetic_snapshot')
    assert all(c['stateVersion']==0 for c in call('/v1/cards/performance?month='+period,token=a));passed.append('month_isolation')
finally:
    for token in tokens:call('/v1/me','DELETE',token=token)
result=dict(count=len(passed),passed=passed,syntheticOnly=True)
(pathlib.Path(__file__).resolve().parents[1]/'.run'/'month-state-api.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
