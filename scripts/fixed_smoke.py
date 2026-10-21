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
    for _ in range(2):tokens.append(call('/v1/auth/register','POST',dict(email=f'fixed-{uuid.uuid4()}@example.test',password=uuid.uuid4().hex),status=201)['accessToken'])
    a,b=tokens;demo=call('/v1/demo/setup','POST',token=a);card=demo['cards'][1]['id'];path='/v1/budgets/'+month
    next_month=(today.replace(day=28)+datetime.timedelta(days=4)).strftime('%Y-%m')
    fixed='/v1/fixed-costs';plan=dict(id=str(uuid.uuid4()),name='Test rent',amountWon=50000,category='HOUSING',dayOfMonth=31,effectiveMonth=month,active=True,version=0)
    def view(m=month):return call(fixed+'?month='+m,token=a)
    def item(id=plan['id'],m=month):return next(i for i in view(m)['items'] if i['id']==id)
    def summary():return call(path+'/summary',token=a)
    def save(p):return call(fixed,'POST',p,a)
    def link(tx,linked=True,id=plan['id'],m=month,version=None,token=a,status=200):return call(fixed+'/'+id+'/months/'+m+'/links','PUT',dict(transactionId=tx,linked=linked,version=item(id,m)['version'] if version is None else version),token,status)
    def mode(value):
        budget=call(path,token=a);return call(path,'PUT',budget|dict(fixedCostMode=value),a)
    assert view()['scheduledRemainingWon']==0;save(plan)
    assert view()['manualRemainingWon']==30000 and view()['scheduledRemainingWon']==50000 and view()['appliedRemainingWon']==30000;ok('manual_and_schedule_not_added_together')
    mode('SCHEDULED');assert summary()['fixedRemainingWon']==50000 and summary()['availableWon']==70000;ok('explicit_monthly_source_switch')
    checkout=call('/v1/checkout-sessions','POST',dict(orderValueWon=10000,pointUsedWon=0,prepaidMoneyUsedWon=0,cardChargeWon=10000,category='SHOPPING',paymentMethod='NAVER_PAY_CARD',installments=1,splitApproval='NO',confirmedByUser=True),a)
    def recommend():return call('/v1/recommendations','POST',dict(checkoutId=checkout['id'],snapshotVersion=1,mode='BENEFIT',maxOpportunityCostWon=0),a)
    assert recommend()['result']['availableAfterWon']==60000;assert item()['payments']==[];ok('recommendation_is_not_a_payment')
    purchase=dict(clientId=str(uuid.uuid4()),amountWon=50000,cardChargeWon=50000,cardId=card,purchasedAt=str(today),category='SHOPPING',included=True)
    tx=call('/v1/transactions/manual','POST',purchase,a)['id'];states=call('/v1/cards/performance?month='+month,token=a);rec=recommend()
    assert summary()['availableWon']==20000
    link(tx);assert item()['remainingWon']==0 and summary()['spentWon']==50000 and summary()['availableWon']==70000
    assert call('/v1/cards/performance?month='+month,token=a)==states;ok('linked_payment_removes_double_reservation_without_changing_card')
    assert call('/v1/recommendations/'+rec['id'],token=a)['validity']=='INVALIDATED' and recommend()['result']['availableAfterWon']==60000;ok('matching_invalidates_and_updates_recommendation')
    v=item()['version'];link(tx);assert item()['version']==v;ok('same_link_is_idempotent')
    link(tx,False);assert item()['remainingWon']==50000;link(tx);ok('unlink_restores_reservation')
    call('/v1/transactions/'+tx+'/cancellations','POST',dict(clientId=str(uuid.uuid4()),amountWon=20000),a)
    assert item()['remainingWon']==20000 and item()['includedNetWon']==30000 and summary()['availableWon']==70000;ok('partial_refund_restores_only_cancelled_amount')
    def included(value):
        entry=call('/v1/transactions/'+tx,token=a);call('/v1/transactions/'+tx+'/classification','PUT',dict(category='HOUSING',included=value,version=entry['classification_version']),a)
    included(False);assert item()['remainingWon']==50000 and item()['linkedNetWon']==30000 and summary()['spentWon']==0 and summary()['availableWon']==70000
    included(True);assert item()['remainingWon']==20000;ok('budget_exclusion_does_not_hide_unpaid_reservation')
    tx2=call('/v1/transactions/manual','POST',purchase|dict(clientId=str(uuid.uuid4()),amountWon=20000,cardChargeWon=20000),a)['id'];link(tx2);assert item()['remainingWon']==0 and len(item()['payments'])==2;ok('multiple_partial_payments')
    other=plan|dict(id=str(uuid.uuid4()),name='Test utility',amountWon=10000);save(other)
    link(tx,id=other['id'],status=409);assert item(other['id'])['payments']==[];ok('one_transaction_cannot_pay_two_plans')
    save(other|dict(version=item(other['id'])['version'],active=False));assert item(other['id'])['remainingWon']==0;ok('stop_keeps_history_without_reservation')
    before=view();save(plan|dict(version=item()['version'],effectiveMonth=next_month,amountWon=60000));assert item()['amountWon']==50000 and item(m=next_month)['amountWon']==60000 and item(m=next_month)['payments']==[];ok('effective_month_revision_preserves_prior_month_and_links')
    assert view(prior)['items']==[];assert view(next_month)['appliedRemainingWon'] is None;ok('unconfigured_month_is_unknown_not_zero')
    future=f'{today.year+1}-02';due=item(m=future)['dueDate'];assert due.endswith('-28') or due.endswith('-29');ok('short_month_clamps_due_date')
    stale=item()['version']-1;call(fixed,'POST',plan|dict(version=stale,amountWon=90000),a,409);link(tx,version=stale,status=409);assert item()['amountWon']==50000;ok('schedule_and_link_version_conflicts_preserve_data')
    call(fixed+'?month='+month,token=b);assert call(fixed+'?month='+month,token=b)['items']==[]
    call(fixed,'POST',plan|dict(version=item()['version']),b,404);link(tx,token=b,status=404)
    bcard=call('/v1/demo/setup','POST',token=b)['cards'][0]['id'];btx=call('/v1/transactions/manual','POST',purchase|dict(cardId=bcard,clientId=str(uuid.uuid4())),b)['id'];link(btx,status=404);ok('cross_user_plan_and_transaction_isolation')
    priorTx=call('/v1/transactions/manual','POST',purchase|dict(clientId=str(uuid.uuid4()),purchasedAt=prior+'-01'),a)['id'];link(priorTx,status=422);ok('wrong_month_link_rejected')
    for changes in [dict(version=None),dict(name=''),dict(amountWon=0),dict(amountWon=1.5),dict(amountWon=100000001),dict(dayOfMonth=0),dict(dayOfMonth=32),dict(active=None),dict(category='INVALID'),dict(effectiveMonth='bad')]:call(fixed,'POST',plan|dict(version=item()['version'])|changes,a,422)
    call(path,'PUT',call(path,token=a)|dict(fixedCostMode='BOTH'),a,422);ok('required_fields_integer_limits_and_mode_validation')
    call('/v1/transactions/'+tx+'/cancellations','POST',dict(clientId=str(uuid.uuid4()),amountWon=30000),a);assert item()['remainingWon']==30000;ok('full_refund_reopens_unpaid_balance')
    budget=call(path,token=a);del budget['fixedCostMode'];call(path,'PUT',budget|dict(totalWon=140000),a);assert view()['mode']=='SCHEDULED';ok('legacy_budget_edit_preserves_source_mode')
    mode('MANUAL');assert view()['appliedRemainingWon']==30000 and view()['manualRemainingWon']==30000;ok('switch_back_preserves_original_manual_amount')
finally:
    for token in tokens:call('/v1/me','DELETE',token=token)
result=dict(count=len(passed),passed=passed,database='PostgreSQL',syntheticOnly=True)
(pathlib.Path(__file__).resolve().parents[1]/'.run'/'fixed-api.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
