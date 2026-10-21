"""Classification edits against the real local API/PostgreSQL using disposable synthetic users."""
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
    for _ in range(2):tokens.append(call('/v1/auth/register','POST',dict(email=f'classification-{uuid.uuid4()}@example.test',password=uuid.uuid4().hex),status=201)['accessToken'])
    a,b=tokens;demo=call('/v1/demo/setup','POST',token=a);bid=demo['cards'][1]['id']
    budget=call('/v1/budgets/'+month,token=a)
    call('/v1/budgets/'+month,'PUT',dict(totalWon=120000,categories=dict(SHOPPING=70000,FOOD=40000),fixedRemainingWon=30000,version=budget['version']),a)
    purchase=dict(clientId=str(uuid.uuid4()),amountWon=50000,cardChargeWon=50000,cardId=bid,purchasedAt=str(today),category='SHOPPING',included=True)
    tx=call('/v1/transactions/manual','POST',purchase,a);id=tx['id'];path=f'/v1/transactions/{id}'
    call(path+'/cancellations','POST',dict(clientId=str(uuid.uuid4()),amountWon=20000),a)
    def detail():return call(path,token=a)
    def states():return call('/v1/cards/performance?month='+month,token=a)
    def summary(m=month):return call('/v1/budgets/'+m+'/summary',token=a)
    def category(code):return next(c for c in summary()['categories'] if c['category']==code)
    def edit(category,included,version=None,method='PUT'):
        return call(path+'/classification',method,dict(category=category,included=included,version=detail()['classification_version'] if version is None else version),a)
    order=dict(orderValueWon=50000,pointUsedWon=0,prepaidMoneyUsedWon=0,cardChargeWon=50000,category='SHOPPING',paymentMethod='NAVER_PAY_CARD',installments=1,splitApproval='NO',confirmedByUser=True)
    checkout=call('/v1/checkout-sessions','POST',order,a)
    def recommend():return call('/v1/recommendations','POST',dict(checkoutId=checkout['id'],snapshotVersion=1,mode='BENEFIT',maxOpportunityCostWon=0),a)
    before=states();original=detail();rec=recommend()
    assert rec['result']['overallAfterWon']==40000 and rec['result']['categoryAfterWon']==-10000
    assert summary()['spentWon']==30000 and summary()['remainingWon']==90000;ok('net_expense_after_partial_cancel')
    changed=edit('FOOD',True);assert changed['classification_version']==2 and changed['net_won']==30000
    assert states()==before and summary()['remainingWon']==90000
    assert category('SHOPPING')['remainingWon']==70000 and category('FOOD')['remainingWon']==10000
    assert call('/v1/recommendations/'+rec['id'],token=a)['validity']=='INVALIDATED'
    rec=recommend();assert rec['result']['overallAfterWon']==40000 and rec['result']['categoryAfterWon']==20000;ok('category_moves_net_expense_preserves_card_calculation_invalidates_recommendation')
    edit('FOOD',False);assert summary()['spentWon']==0 and summary()['remainingWon']==120000 and category('FOOD')['spentWon']==0
    assert states()==before and recommend()['result']['overallAfterWon']==70000;ok('budget_exclusion_preserves_ledger_performance_and_benefit')
    replay=call('/v1/transactions/manual','POST',purchase,a)
    assert replay['id']==id and replay['category']=='FOOD' and replay['included'] is False
    assert len(call('/v1/transactions?month='+month,token=a))==1;ok('original_purchase_replay_does_not_duplicate_or_reset_classification')
    saved=detail();assert call(path+'/classification','PUT',dict(category='OTHER',included=True,version=1),a,409)['error']['code']=='VERSION_CONFLICT'
    assert detail()==saved;ok('stale_version_preserves_latest_fields')
    for changes in [dict(category=''),dict(category='INVALID'),dict(included=None),dict(version=None),dict(version=0)]:
        call(path+'/classification','PUT',dict(category='FOOD',included=False,version=saved['classification_version'])|changes,a,422)
    assert detail()==saved;ok('invalid_input_atomic_rejection')
    rec=recommend();assert edit('FOOD',False)['classification_version']==saved['classification_version']
    assert call('/v1/recommendations/'+rec['id'],token=a)['validity']=='CURRENT';ok('no_op_does_not_invalidate_recommendation')
    edit('OTHER',True,method='PATCH');assert category('OTHER')['remainingWon'] is None and summary()['spentWon']==30000;assert states()==before;ok('patch_alias_and_unbudgeted_category_stays_unknown')
    edit('FOOD',False);call(path,token=b,status=404);call(path+'/classification','PUT',dict(category='SHOPPING',included=True,version=detail()['classification_version']),b,404)
    call('/v1/budgets/'+month+'/summary',token=b,status=404);assert call('/v1/transactions?month='+month,token=b)==[];ok('tenant_isolation')
    after=detail()
    for key in ['amount_won','card_charge_won','card_net_won','net_won','card_id','purchased_at','benefit_rate_bp','benefit_rule_version','performance_eligible','original_category','original_included']:assert after[key]==original[key]
    ok('immutable_money_card_rule_and_original_fields_preserved')
    call('/v1/budgets/'+prior,'PUT',dict(totalWon=60000,categories=dict(SHOPPING=60000),fixedRemainingWon=0,version=0),a)
    old=call('/v1/transactions/manual','POST',purchase|dict(clientId=str(uuid.uuid4()),purchasedAt=prior+'-01'),a)
    call(f'/v1/transactions/{old["id"]}/classification','PUT',dict(category='FOOD',included=False,version=1),a)
    assert summary(prior)['spentWon']==0 and summary()['spentWon']==0 and states()==before;ok('month_isolation')
    call(path+'/cancellations','POST',dict(clientId=str(uuid.uuid4()),amountWon=30000),a);edit('FOOD',True)
    assert detail()['net_won']==0 and summary()['spentWon']==0;ok('fully_cancelled_transaction_does_not_restore_budget_spending')
finally:
    for token in tokens:call('/v1/me','DELETE',token=token)
result=dict(count=len(passed),passed=passed,database='PostgreSQL',syntheticOnly=True)
(pathlib.Path(__file__).resolve().parents[1]/'.run'/'classification-api.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
