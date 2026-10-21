"""Synthetic benefit rules against the real local API/PostgreSQL; no real card claims."""
import datetime,json,pathlib,uuid,urllib.request,urllib.error
base='http://127.0.0.1:18080';tokens=[];passed=[]
today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).date();month=today.strftime('%Y-%m');prior=(today.replace(day=1)-datetime.timedelta(days=1)).strftime('%Y-%m')
def call(path,method='GET',data=None,token=None,status=200):
    req=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),method=method,headers={'Content-Type':'application/json','Authorization':'Bearer '+(token or ''),'X-Confirm-Delete':'DELETE_ACCOUNT'})
    try:
        with urllib.request.urlopen(req,timeout=10) as r:code,raw=r.status,r.read()
    except urllib.error.HTTPError as e:code,raw=e.code,e.read()
    assert code==status,(path,code,status)
    return json.loads(raw) if raw else None
def ok(name):passed.append(name)
try:
    for _ in range(2):tokens.append(call('/v1/auth/register','POST',dict(email=f'benefit-{uuid.uuid4()}@example.test',password=uuid.uuid4().hex),status=201)['accessToken'])
    a,b=tokens;demo=call('/v1/demo/setup','POST',token=a);aid,bid=[c['id'] for c in demo['cards']]
    def state(id=bid,m=month):return next(c for c in call('/v1/cards/performance?month='+m,token=a) if c['id']==id)
    def setup(cap=1000,qualified=True,m=month,id=bid):
        s=state(id,m);return call(f'/v1/cards/{id}/months/{m}','PUT',dict(untrackedPerformanceWon=270000,currentQualified=qualified,remainingCapWon=cap,version=s['stateVersion']),a)
    def buy(amount,charge=None,included=True,m=month,id=bid):
        data=dict(clientId=str(uuid.uuid4()),amountWon=amount,cardChargeWon=amount if charge is None else charge,cardId=id,purchasedAt=m+'-01',category='SHOPPING',included=included)
        tx=call('/v1/transactions/manual','POST',data,a);return tx,data
    def cancel(tx,amount,refund=None):
        data=dict(clientId=str(uuid.uuid4()),amountWon=amount)
        if refund is not None:data['cardRefundWon']=refund
        result=call(f'/v1/transactions/{tx["id"]}/cancellations','POST',data,a);return result,data
    def check(used,remaining,id=bid,m=month):
        s=state(id,m);assert (s['ledgerBenefitUsedWon'],s['remainingCapWon'])==(used,remaining),s
    setup();first,data=buy(50000);check(500,500)
    assert first['benefit_rate_bp']==100 and first['benefit_rule_version']=='SYNTHETIC_BENEFIT_V1'
    call('/v1/transactions/manual','POST',data,a);check(500,500);ok('purchase_snapshot_and_duplicate_not_double_charged')
    second,_=buy(80000,included=False);check(1000,0);cancel(first,20000);check(1000,0);ok('monthly_cap_and_partial_cancel_no_excess_restoration_budget_independent')
    _,refund=cancel(second,20000);check(900,100);call(f'/v1/transactions/{second["id"]}/cancellations','POST',refund,a);check(900,100);ok('partial_cancel_and_duplicate_refund')
    cancel(first,30000);check(600,400)
    order=dict(orderValueWon=50000,pointUsedWon=0,prepaidMoneyUsedWon=0,cardChargeWon=50000,category='SHOPPING',paymentMethod='NAVER_PAY_CARD',installments=1,splitApproval='NO',confirmedByUser=True)
    checkout=call('/v1/checkout-sessions','POST',order,a)
    rec=call('/v1/recommendations','POST',dict(checkoutId=checkout['id'],snapshotVersion=1,mode='BENEFIT',maxOpportunityCostWon=0),a)
    candidate=next(c for c in rec['result']['candidates'] if c['cardId']==bid);assert candidate['benefitWon']==400;check(600,400);ok('recommendation_uses_recalculated_cap_without_consuming_it')
    cancel(second,60000);check(0,1000);assert call('/v1/recommendations/'+rec['id'],token=a)['validity']=='INVALIDATED';ok('full_cancel_restores_cap_and_invalidates_recommendation')
    mixed,_=buy(55000,45000);check(450,550);cancel(mixed,20000,15000);check(300,700);ok('mixed_tender_uses_only_card_approval_and_card_refund')
    # Preserve the raw baseline through a monthly update, never feed the calculated 700 back as baseline.
    s=state();call(f'/v1/cards/{bid}/months/{month}','PUT',dict(untrackedPerformanceWon=s['untrackedPerformanceWon'],currentQualified=s['currentQualified'],remainingCapWon=s['baseRemainingCapWon'],version=s['stateVersion']),a);check(300,700);assert state()['baseRemainingCapWon']==1000;ok('monthly_edit_preserves_base_no_double_subtraction')
    call(f'/v1/transactions/{mixed["id"]}/card','PUT',dict(cardId=aid,cardChargeWon=45000),a);check(0,1000);check(900,9100,aid);ok('relink_moves_net_benefit_and_snapshots_new_rate')
    setup(m=prior);past,_=buy(20000,m=prior);check(200,800,m=prior);cancel(past,5000);check(150,850,m=prior);check(0,1000);ok('refund_attributed_to_purchase_month_card_and_month_isolation')
    current,_=buy(50000);setup(qualified=False);check(0,1000);assert state()['performanceWon']>=300000
    setup(qualified=None);check(None,None);setup(cap=None);check(None,None);ok('eligibility_and_cap_unknown_are_not_fabricated_current_future_separated')
    setup();call(f'/v1/transactions/{current["id"]}/cancellations','POST',dict(clientId=str(uuid.uuid4()),amountWon=1),b,status=404)
    assert call('/v1/cards/performance?month='+month,token=b)==[];check(500,500);ok('cross_user_read_and_refund_denied')
    cancel(current,1);check(499,501);cancel(current,49999);check(0,1000);ok('per_transaction_floor_one_won_and_full_refund')
finally:
    for token in tokens:call('/v1/me','DELETE',token=token)
result=dict(count=len(passed),passed=passed,database='PostgreSQL',syntheticOnly=True)
(pathlib.Path(__file__).resolve().parents[1]/'.run'/'benefit-api.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
