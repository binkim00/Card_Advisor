"""Synthetic only: verify monthly/card isolation and current-vs-future qualification on PostgreSQL."""
import json, urllib.request, urllib.error, uuid, datetime, pathlib
BASE="http://127.0.0.1:18080"; tokens=[];passed=[]
def call(path, method="GET", data=None, token=None, expected=200):
    headers={"Content-Type":"application/json","X-Confirm-Delete":"DELETE_ACCOUNT"}
    if token: headers["Authorization"]="Bearer "+token
    req=urllib.request.Request(BASE+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=15) as r: status,raw=r.status,r.read()
    except urllib.error.HTTPError as e:status,raw=e.code,e.read()
    obj=json.loads(raw) if raw else None
    assert status==expected,(path,status,expected,obj.get("error") if isinstance(obj,dict) else None)
    return obj
def ok(name):passed.append(name)
month=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).strftime("%Y-%m")
today=month+"-01"
def shifted(delta):
    year,mon=map(int,month.split("-"));n=year*12+mon-1+delta;return f"{n//12:04d}-{n%12+1:02d}"
try:
    for _ in range(2):
        r=call("/v1/auth/register","POST",{"email":f"perf-{uuid.uuid4()}@example.test","password":uuid.uuid4().hex},expected=201);tokens.append(r["accessToken"])
    a,b=tokens
    demo=call("/v1/demo/setup","POST",token=a)
    aid=next(c["id"] for c in demo["cards"] if c["name"]=="[가상] A 카드")
    bid=next(c["id"] for c in demo["cards"] if c["name"]=="[가상] B 카드")
    def states(m=month):return {c["id"]:c for c in call("/v1/cards/performance?month="+m,token=a)}
    def state(id,m=month):return states(m)[id]
    def setstate(id,baseline,qualified,m=month):
        old=state(id,m)
        return call(f"/v1/cards/{id}/months/{m}","PUT",{"untrackedPerformanceWon":baseline,"currentQualified":qualified,"remainingCapWon":10000,"version":old["stateVersion"]},token=a)
    def buy(id,amount,charge=None,date=today,included=True):
        return call("/v1/transactions/manual","POST",{"clientId":str(uuid.uuid4()),"cardId":id,"cardChargeWon":amount if charge is None else charge,"amountWon":amount,"purchasedAt":date,"category":"SHOPPING","included":included},token=a)
    def cancel(id,amount,refund=None):
        data={"clientId":str(uuid.uuid4()),"amountWon":amount}
        if refund is not None:data["cardRefundWon"]=refund
        return call(f"/v1/transactions/{id}/cancellations","POST",data,token=a)
    setstate(bid,270000,False)
    order={"orderValueWon":50000,"pointUsedWon":0,"prepaidMoneyUsedWon":0,"cardChargeWon":50000,"category":"SHOPPING","paymentMethod":"NAVER_PAY_CARD","installments":1,"splitApproval":"NO","confirmedByUser":True}
    co=call("/v1/checkout-sessions","POST",order,token=a)
    rec=call("/v1/recommendations","POST",{"checkoutId":co["id"],"snapshotVersion":1,"mode":"PERFORMANCE","maxOpportunityCostWon":0},token=a)
    bc=next(c for c in rec["result"]["candidates"] if c["cardId"]==bid)
    assert bc["benefitWon"]==0 and bc["performanceAfterWon"]==320000 and state(bid)["performanceWon"]==270000
    ok("recommendation_does_not_write_performance_or_upgrade_current_eligibility")
    tx=buy(bid,50000,included=False)
    assert state(bid)["performanceWon"]==320000 and state(bid)["currentQualified"] is False and state(aid)["performanceWon"]==300000
    assert call("/v1/recommendations/"+rec["id"],token=a)["validity"]=="INVALIDATED"
    ok("card_isolation_budget_exclusion_independent_and_current_eligibility_fixed")
    cancel(tx["id"],20000)
    assert state(bid)["performanceWon"]==300000 and state(bid)["gapWon"]==0 and state(bid)["nextMonthThresholdMet"] is True
    cancel(tx["id"],1)
    assert state(bid)["performanceWon"]==299999 and state(bid)["gapWon"]==1
    cancel(tx["id"],29999)
    assert state(bid)["performanceWon"]==270000
    ok("partial_full_cancel_and_one_won_boundary")
    tx=buy(bid,30000);cancel(tx["id"],10000)
    call(f"/v1/transactions/{tx['id']}/card","PUT",{"cardId":aid,"cardChargeWon":30000},token=a)
    assert state(bid)["performanceWon"]==270000 and state(aid)["performanceWon"]==320000
    ok("relink_moves_net_performance_between_cards")
    call(f"/v1/transactions/{tx['id']}/card","PUT",{"cardId":aid,"cardChargeWon":20000},token=a,expected=409)
    ok("refund_allocation_not_silently_rewritten")
    prev=shifted(-1);setstate(bid,0,False,prev)
    prior=buy(bid,40000,date=prev+"-01");cancel(prior["id"],10000)
    assert state(bid,prev)["performanceWon"]==30000 and state(bid)["performanceWon"]==270000
    future=state(bid,shifted(1));assert future["performanceWon"] is None and future["currentQualified"] is None
    ok("purchase_month_cancellation_and_no_snapshot_carryover")
    mixed=buy(bid,55000,45000)
    assert state(bid)["performanceWon"]==315000
    call(f"/v1/transactions/{mixed['id']}/cancellations","POST",{"clientId":str(uuid.uuid4()),"amountWon":20000},token=a,expected=422)
    cancel(mixed["id"],20000,15000);assert state(bid)["performanceWon"]==300000
    cancel(mixed["id"],35000,30000);assert state(bid)["performanceWon"]==270000
    ok("mixed_tender_uses_card_approval_and_explicit_card_refund")
    for eligible in [False,None]:
        id=str(uuid.uuid4())
        call("/v1/cards","POST",{"id":id,"name":"[가상] 제외·미확인","rateBp":100,"qualified":False,"performanceWon":100,"targetWon":300000,"remainingCapWon":10000,"performanceEligible":eligible,"source":"SYNTHETIC"},token=a)
        tx=buy(id,50000)
        assert state(id)["performanceWon"]==(100 if eligible is False else None)
        cancel(tx["id"],50000);assert state(id)["performanceWon"]==100
    ok("excluded_and_unknown_performance_rules")
    setstate(bid,None,False)
    assert state(bid)["performanceWon"] is None
    ok("unknown_untracked_baseline_not_zero")
    call("/v1/transactions/manual","POST",{"clientId":str(uuid.uuid4()),"cardId":aid,"cardChargeWon":1,"amountWon":1,"purchasedAt":today,"category":"SHOPPING","included":True},token=b,expected=404)
    call(f"/v1/cards/{aid}/months/{month}","PUT",{"untrackedPerformanceWon":0,"currentQualified":True,"remainingCapWon":0,"version":1},token=b,expected=404)
    assert call("/v1/cards/performance?month="+month,token=b)==[]
    ok("cross_user_card_link_state_and_read_denied")
finally:
    for token in tokens:call("/v1/me","DELETE",token=token)
result={"count":len(passed),"passed":passed,"database":"PostgreSQL","syntheticOnly":True}
(pathlib.Path(__file__).resolve().parents[1]/".run"/"card-performance-api.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(result,ensure_ascii=False))
