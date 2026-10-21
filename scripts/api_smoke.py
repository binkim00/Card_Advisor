"""Contract smoke tests against the running local Spring API and real PostgreSQL.
Creates disposable synthetic users and removes them through DELETE /me.
Does not use financial credentials, real card data, or the external network.
"""
import json, urllib.request, urllib.error, uuid, datetime, pathlib, sys

BASE="http://127.0.0.1:18080"
passed=[]
tokens=[]
def call(path, method="GET", data=None, token=None, expected=200, extra=None):
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]="Bearer "+token
    if extra: headers.update(extra)
    req=urllib.request.Request(BASE+path, data=json.dumps(data).encode() if data is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r: status,body=r.status,r.read()
    except urllib.error.HTTPError as e: status,body=e.code,e.read()
    if status!=expected: raise AssertionError(f"{method} {path}: expected {expected}, got {status}; code="+str(json.loads(body).get("error",{}).get("code","unknown")))
    return json.loads(body) if body else None
def ok(name): passed.append(name)
try:
    users=[]
    for _ in range(2):
        auth=call("/v1/auth/register","POST",{"email":f"test-{uuid.uuid4()}@example.test","password":uuid.uuid4().hex},expected=201)
        tokens.append(auth["accessToken"]);users.append(auth["userId"])
    a,b=tokens
    call("/v1/cards",expected=401);ok("authentication_required")
    demo=call("/v1/demo/setup","POST",token=a)
    call("/v1/demo/setup","POST",token=a)
    assert len(call("/v1/cards",token=a))==2;ok("demo_idempotent")
    assert call("/v1/cards",token=b)==[];ok("card_list_user_isolation")
    card=demo["cards"][0]
    call("/v1/cards","POST",card,token=b,expected=404);ok("cross_user_card_write_denied")
    month=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).strftime("%Y-%m")
    order={"orderValueWon":50000,"pointUsedWon":0,"prepaidMoneyUsedWon":0,"cardChargeWon":50000,
           "category":"SHOPPING","paymentMethod":"NAVER_PAY_CARD","installments":1,"splitApproval":"NO","confirmedByUser":True}
    checkout=call("/v1/checkout-sessions","POST",order,token=a)
    request={"checkoutId":checkout["id"],"snapshotVersion":1,"mode":"BALANCED","maxOpportunityCostWon":1000}
    rec=call("/v1/recommendations","POST",request,token=a)
    result=rec["result"]
    assert result["overallAfterWon"]==70000 and result["categoryAfterWon"]==20000 and result["availableAfterWon"]==40000
    selected=next(c for c in result["candidates"] if c["cardId"]==result["recommendedCardId"])
    assert selected["name"]=="[가상] B 카드" and selected["benefitWon"]==500
    ok("recommendation_and_budget_golden")
    call("/v1/recommendations","POST",request,token=b,expected=404)
    call("/v1/recommendations/"+rec["id"],token=b,expected=404);ok("checkout_and_recommendation_isolation")
    entry={"clientId":str(uuid.uuid4()),"amountWon":50000,"cardId":card["id"],"cardChargeWon":50000,"purchasedAt":month+"-01","category":"SHOPPING","included":True}
    tx=call("/v1/transactions/manual","POST",entry,token=a)
    tx2=call("/v1/transactions/manual","POST",entry,token=a)
    assert tx["id"]==tx2["id"] and len(call("/v1/transactions?month="+month,token=a))==1;ok("ledger_idempotence")
    call("/v1/transactions/manual","POST",{**entry,"amountWon":50001},token=a,expected=409);ok("idempotency_body_conflict")
    assert call("/v1/recommendations/"+rec["id"],token=a)["validity"]=="INVALIDATED";ok("ledger_change_invalidates")
    cancel={"clientId":str(uuid.uuid4()),"amountWon":20000}
    call("/v1/transactions/"+tx["id"]+"/cancellations","POST",cancel,token=b,expected=404)
    call("/v1/transactions/"+tx["id"]+"/cancellations","POST",cancel,token=a)
    call("/v1/transactions/"+tx["id"]+"/cancellations","POST",cancel,token=a)
    assert call("/v1/transactions?month="+month,token=a)[0]["net_won"]==30000;ok("partial_cancel_idempotent_and_isolated")
    call("/v1/transactions/"+tx["id"]+"/cancellations","POST",{"clientId":str(uuid.uuid4()),"amountWon":30001},token=a,expected=422);ok("over_cancel_rejected")
    rec2=call("/v1/recommendations","POST",request,token=a)
    assert rec2["result"]["overallAfterWon"]==40000;ok("net_ledger_used_for_budget")
    call("/v1/checkout-sessions/"+checkout["id"],"PUT",{"expectedVersion":1,"context":{**order,"orderValueWon":51000,"cardChargeWon":51000}},token=a)
    call("/v1/recommendations","POST",request,token=a,expected=409)
    assert call("/v1/recommendations/"+rec2["id"],token=a)["validity"]=="INVALIDATED";ok("checkout_change_invalidates")
    budget=demo["budget"]
    call("/v1/budgets/"+month,"PUT",{**budget,"version":0},token=a,expected=409);ok("budget_optimistic_lock")
    call("/v1/checkout-sessions","POST",{**order,"cardChargeWon":1},token=a,expected=422);ok("amount_mismatch_rejected")
finally:
    for token in tokens:
        call("/v1/me","DELETE",token=token,extra={"X-Confirm-Delete":"DELETE_ACCOUNT"})
        call("/v1/cards",token=token,expected=401)
    if tokens:ok("account_delete_revokes_sessions")
report={"passed":passed,"count":len(passed),"database":"PostgreSQL","financialData":"SYNTHETIC_ONLY"}
out=pathlib.Path(__file__).resolve().parents[1]/".run"/"api-smoke.json"
out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(report,ensure_ascii=False))

