"""Only changes the fresh synthetic user's recommendation fixture to emulate a month boundary."""
import subprocess, json, urllib.request, uuid, pathlib
root=pathlib.Path(__file__).resolve().parents[1]
def call(path,method="GET",data=None,token=None):
    h={"Content-Type":"application/json","X-Confirm-Delete":"DELETE_ACCOUNT"}
    if token:h["Authorization"]="Bearer "+token
    with urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:18080"+path,data=json.dumps(data).encode() if data is not None else None,headers=h,method=method),timeout=15) as r:return json.load(r)
auth=call("/v1/auth/register","POST",{"email":f"month-{uuid.uuid4()}@example.test","password":uuid.uuid4().hex})
token=auth["accessToken"]
try:
    call("/v1/demo/setup","POST",token=token)
    checkout=call("/v1/checkout-sessions","POST",{"orderValueWon":1,"pointUsedWon":0,"prepaidMoneyUsedWon":0,"cardChargeWon":1,"category":"SHOPPING","paymentMethod":"DIRECT_CARD","installments":1,"splitApproval":"NO","confirmedByUser":True},token)
    rec=call("/v1/recommendations","POST",{"checkoutId":checkout["id"],"snapshotVersion":1,"mode":"BENEFIT","maxOpportunityCostWon":0},token)
    assert call("/v1/recommendations/"+rec["id"],token=token)["validity"]=="CURRENT"
    rid=str(uuid.UUID(rec["id"]));uid=str(uuid.UUID(auth["userId"]))
    sql=f"UPDATE recommendations SET calculation_month=(calculation_month-interval '1 month')::date WHERE id='{rid}'::uuid AND user_id='{uid}'::uuid;"
    subprocess.run([r"C:\Program Files\Docker\Docker\resources\bin\docker.exe","--host","npipe:////./pipe/dockerDesktopLinuxEngine","compose","exec","-T","db","psql","-U","card_advisor","-d","card_advisor","-v","ON_ERROR_STOP=1","-c",sql],cwd=root,check=True,capture_output=True,timeout=15)
    assert call("/v1/recommendations/"+rec["id"],token=token)["validity"]=="INVALIDATED"
    result={"monthBoundaryFixture":"PASSED","syntheticOnly":True}
    (root/".run"/"recommendation-month.json").write_text(json.dumps(result),encoding="utf-8")
    print(json.dumps(result))
finally:call("/v1/me","DELETE",token=token)
