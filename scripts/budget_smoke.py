"""Budget edits against the real local API/PostgreSQL using disposable synthetic users."""
import datetime,json,pathlib,uuid,urllib.request,urllib.error
base='http://127.0.0.1:18080';tokens=[];passed=[]
today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).date();month=today.strftime('%Y-%m')
prior=(today.replace(day=1)-datetime.timedelta(days=1)).strftime('%Y-%m')
def call(path,method='GET',data=None,token=None,status=200):
    req=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),method=method,headers={'Content-Type':'application/json','Authorization':'Bearer '+(token or ''),'X-Confirm-Delete':'DELETE_ACCOUNT'})
    try:
        with urllib.request.urlopen(req,timeout=10) as r:code,raw=r.status,r.read()
    except urllib.error.HTTPError as e:code,raw=e.code,e.read()
    obj=json.loads(raw) if raw else None
    assert code==status,(path,code,status,obj)
    return obj
def ok(name):passed.append(name)
try:
    for _ in range(2):tokens.append(call('/v1/auth/register','POST',dict(email=f'budget-{uuid.uuid4()}@example.test',password=uuid.uuid4().hex),status=201)['accessToken'])
    a,b=tokens;path='/v1/budgets/'+month
    call(path,token=a,status=404);call(path+'/summary',token=a,status=404);ok('missing_budget_not_zero')
    first=dict(totalWon=120000,categories=dict(SHOPPING=70000),fixedRemainingWon=30000,version=0)
    saved=call(path,'PUT',first,a);assert saved['version']==1 and call(path,token=a)==saved;ok('create_and_read_saved_values')
    call(path,'PUT',first,a,409);assert call(path,token=a)==saved;ok('concurrent_creation_conflict')
    demo=call('/v1/demo/setup','POST',token=a);assert call(path,token=a)==saved
    bid=demo['cards'][1]['id'];tx=call('/v1/transactions/manual','POST',dict(clientId=str(uuid.uuid4()),amountWon=50000,cardChargeWon=50000,cardId=bid,purchasedAt=str(today),category='SHOPPING',included=True),a)
    call('/v1/transactions/'+tx['id']+'/cancellations','POST',dict(clientId=str(uuid.uuid4()),amountWon=20000),a)
    states=call('/v1/cards/performance?month='+month,token=a);entries=call('/v1/transactions?month='+month,token=a)
    checkout=call('/v1/checkout-sessions','POST',dict(orderValueWon=50000,pointUsedWon=0,prepaidMoneyUsedWon=0,cardChargeWon=50000,category='SHOPPING',paymentMethod='NAVER_PAY_CARD',installments=1,splitApproval='NO',confirmedByUser=True),a)
    def recommend():return call('/v1/recommendations','POST',dict(checkoutId=checkout['id'],snapshotVersion=1,mode='BENEFIT',maxOpportunityCostWon=0),a)
    rec=recommend()
    saved=call(path,'PUT',dict(totalWon=200000,categories=dict(SHOPPING=90000,FOOD=60000,OTHER=0),fixedRemainingWon=20000,version=1),a)
    assert saved['version']==2 and call(path,token=a)==saved;ok('add_edit_multiple_categories')
    summary=call(path+'/summary',token=a);assert summary['spentWon']==30000 and summary['remainingWon']==170000 and summary['availableWon']==150000
    cats={c['category']:c for c in summary['categories']};assert cats['SHOPPING']['remainingWon']==60000 and cats['FOOD']['remainingWon']==60000 and cats['OTHER']['remainingWon']==0;ok('partial_cancel_net_spending_applies_to_updated_limits')
    assert call('/v1/cards/performance?month='+month,token=a)==states and call('/v1/transactions?month='+month,token=a)==entries;ok('budget_does_not_modify_card_or_ledger')
    assert call('/v1/recommendations/'+rec['id'],token=a)['validity']=='INVALIDATED'
    rec=recommend();assert rec['result']['overallAfterWon']==120000 and rec['result']['categoryAfterWon']==10000 and rec['result']['availableAfterWon']==100000;ok('recommendation_invalidated_and_recalculated')
    call(path,'PUT',saved|dict(totalWon=230000,version=1),a,409);assert call(path,token=a)==saved;ok('stale_version_cannot_overwrite')
    assert call(path,'PUT',saved,a)==saved and call('/v1/recommendations/'+rec['id'],token=a)['validity']=='CURRENT';ok('no_op_keeps_version_and_recommendation')
    for field in ['totalWon','fixedRemainingWon','version','categories']:
        missing=saved.copy();del missing[field];call(path,'PUT',missing,a,422);call(path,'PUT',saved|{field:None},a,422)
    ok('missing_or_null_values_rejected')
    for change in [dict(totalWon=200000.9),dict(fixedRemainingWon=20000.5),dict(version=2.1),dict(categories={'FOOD':1.9}),dict(totalWon=-1),dict(totalWon=100000001),dict(fixedRemainingWon=-1),dict(fixedRemainingWon=100000001),dict(version=-1),dict(categories={'INVALID':0}),dict(categories={'':0}),dict(categories={'FOOD':None}),dict(categories={'FOOD':-1}),dict(categories={'FOOD':9223372036854775807}),dict(categories={'FOOD':150000,'SHOPPING':100000})]:call(path,'PUT',saved|change,a,422)
    assert call(path,token=a)==saved;ok('invalid_keys_ranges_and_sum_rejected_atomically')
    call(path,token=b,status=404);other=call(path,'PUT',first|dict(totalWon=80000),b);assert other['version']==1 and call(path,token=a)==saved;ok('user_isolation')
    call('/v1/budgets/'+prior,'PUT',dict(totalWon=50000,categories={'FOOD':20000,'OTHER':0},fixedRemainingWon=0,version=0),a)
    assert call(path,token=a)==saved and call('/v1/budgets/'+prior+'/summary',token=a)['spentWon']==0;ok('month_isolation')
    saved=call(path,'PUT',saved|dict(categories={'FOOD':60000,'OTHER':0}),a)
    summary=call(path+'/summary',token=a);shopping=next(c for c in summary['categories'] if c['category']=='SHOPPING');assert shopping['remainingWon'] is None and shopping['spentWon']==30000
    assert recommend()['result']['categoryAfterWon'] is None;ok('removing_limit_restores_unknown_not_zero')
    call(path,'PUT',saved|dict(totalWon=0,categories={'OTHER':0},fixedRemainingWon=100000000),a)
    summary=call(path+'/summary',token=a);assert summary['remainingWon']==-30000 and summary['availableWon']==-100030000;ok('explicit_zero_and_negative_available_are_not_clamped')
finally:
    for token in tokens:call('/v1/me','DELETE',token=token)
result=dict(count=len(passed),passed=passed,database='PostgreSQL',syntheticOnly=True)
(pathlib.Path(__file__).resolve().parents[1]/'.run'/'budget-api.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
