package dev.cardadvisor.android

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

/** Only synthetic data; never save screenshots or raw semantics/runner output. */
@RunWith(AndroidJUnit4::class)
class FinanceSyncFlowTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun tag(id:String)=compose.onNodeWithTag(id)
 private fun clickTag(id:String){if(id in listOf("syncStart","syncThisMonth","syncPreviousMonth")&&compose.onAllNodesWithTag(id).fetchSemanticsNodes().isEmpty())compose.onNodeWithTag("syncPeriodToggle").performScrollTo().performClick();compose.waitUntil(20000){runCatching{tag(id).assertIsEnabled()}.isSuccess};if(id=="mappingSave")tag(id).performClick()else tag(id).performScrollTo().performClick()}
 private fun text(value:String){compose.waitUntil(20000){compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(value:String){val node=compose.onNodeWithText(value);compose.waitUntil(20000){runCatching{node.assertIsEnabled()}.isSuccess};if(value in listOf("설정","나가기"))node.performClick() else node.performScrollTo().performClick()}
 private fun awaitLoginForm(){compose.waitUntil(15000){compose.onAllNodesWithTag("email").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}}
 private fun step(value:String){InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("syncStep",value)})}
 private fun notice(value:String){try{compose.waitUntil(20000){runCatching{tag("syncNotice").assertTextEquals(value);tag("syncRefresh").assertIsEnabled()}.isSuccess}}
  catch(e:Throwable){
   val state=runCatching{tag("syncNotice").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}}.getOrDefault("")
   val code=when{state=="거래 확인을 저장했습니다."->"saved";state.startsWith("거래 또는 연결 버전")->"conflict";state.startsWith("확인 요청 후에도")->"held";state.startsWith("요청 결과를 확인하지")->"unconfirmed";state.startsWith("저장된 거래를 불러오지")->"read_failure";state.startsWith("로그인이 만료")->"expired";state=="처리 중입니다."->"processing";state=="저장된 결과를 조회했습니다."->"loaded";state=="저장된 거래를 불러옵니다."->"initial";state.isEmpty()->"missing";else->"unknown"}
   step("notice_"+code)
   for(id in listOf("syncNotice","syncRefresh","syncConfirm","syncBack")){
    val nodes=runCatching{compose.onAllNodesWithTag(id).fetchSemanticsNodes().size}.getOrDefault(-1)
    val enabled=runCatching{tag(id).assertIsEnabled()}.isSuccess
    step("probe_"+id.lowercase()+"_"+(if(nodes==1)"present" else "missing")+"_"+enabled)
   }
   throw e
  }
 }
 @Test fun syncReconcileAndRecover(){
  val run=requireNotNull(InstrumentationRegistry.getArguments().getString("runId"));UUID.fromString(run)
  val email="sync-ui-$run@example.test";val pw="UiTest-"+UUID.randomUUID();val client=ApiClient()
  val date=LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1).toString();val month=date.take(7)
  var scenario:ActivityScenario<MainActivity>?=null
  fun fixture(mode:String)=runBlocking{client.request("/v1/fixture/sync/$mode","POST")}
  fun rows():List<JSONObject> = runBlocking{val c=JSONArray(client.request("/v1/finance/connections")).getJSONObject(0);val a=JSONArray(client.request("/v1/finance/connections/${c.getString("id")}/transactions"));(0 until a.length()).map{a.getJSONObject(it)}}
  fun refresh(){clickTag("syncRefresh");notice("저장된 결과를 조회했습니다.")}
  fun sync(mode:String,status:String){fixture(mode);clickTag("syncStart");notice(syncOutcome(status))}
  try{
   scenario=ActivityScenario.launch(MainActivity::class.java)
   awaitLoginForm();tag("email").performTextReplacement(email);tag("password").performTextReplacement(pw);click("가입");text("가입했습니다. 내 카드를 등록해 시작해 보세요.")
   val manuals=runBlocking{
    client.token=JSONObject(client.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",pw))).getString("accessToken")
    client.request("/v1/demo/setup","POST")
    val card=JSONArray(client.request("/v1/cards")).getJSONObject(0).getString("id")
    listOf(50000L,70000L).map{n->JSONObject(client.request("/v1/transactions/manual","POST",JSONObject().put("clientId",UUID.randomUUID().toString()).put("amountWon",n).put("cardId",card).put("cardChargeWon",n).put("purchasedAt",date).put("category","FOOD").put("included",n==50000L))).getString("id")}
   }
   step("connection");click("설정");click("금융 연결 관리");text("저장된 금융 연결이 없습니다.");clickTag("financeNew")
   tag("financeOrg").performScrollTo().performTextReplacement("0301");tag("financeLogin").performScrollTo().performTextReplacement("synthetic-login");tag("financePassword").performScrollTo().performTextReplacement("SYNTHETIC_PASSWORD_ONLY");clickTag("financeConsent");clickTag("financeSubmit")
   text("연결됨");clickTag("financeTransactions_0301")
   notice("저장된 거래를 불러오지 못했습니다. 연결을 확인하고 다시 조회해 주세요.");clickTag("syncPeriodToggle");tag("syncStart").assertIsNotEnabled();refresh();tag("syncCounts").assertTextEquals("총 0건 · 가계부에 기록 0건 · 확인 필요 0건")
   clickTag("syncPreviousMonth");val prev=java.time.YearMonth.parse(month).minusMonths(1);tag("syncFrom").assertTextContains(prev.atDay(1).toString(),substring=true);tag("syncTo").assertTextContains(prev.atEndOfMonth().toString(),substring=true);clickTag("syncThisMonth")
   tag("syncFrom").performScrollTo().performTextReplacement("2099-01-01");tag("syncStart").assertIsNotEnabled();tag("syncFrom").performTextReplacement(date)
   step("first_sync");sync("initial","PARTIAL");tag("syncCounts").assertTextEquals("총 5건 · 가계부에 기록 1건 · 확인 필요 4건")
   val initial=rows();val duplicate=initial.first{it.optLong("amount_won")==50000L};val separate=initial.first{it.optLong("amount_won")==70000L};val cancellation=initial.first{it.getString("kind")=="CANCEL_EVENT"};val unknown=initial.first{it.optLong("amount_won")==30000L}
   // A missing refund must remain unknown, not render as zero.
   Assert.assertTrue(unknown.isNull("cancelled_won"));text("취소 금액 확인 필요")
   step("reconcile");clickTag("syncManual_${duplicate.getString("id")}");clickTag("syncCandidate_${manuals[0]}");fixture("conflict");clickTag("syncConfirm")
   notice("거래 또는 연결 버전이 바뀌었습니다. 선택은 보존했지만 저장은 중단했습니다. 저장된 결과를 다시 조회해 주세요.")
   tag("syncConfirm").assertIsNotEnabled();tag("syncSelection").assertTextEquals("선택: 기존 수동 구매와 연결");refresh()
   clickTag("syncManual_${duplicate.getString("id")}");clickTag("syncCandidate_${manuals[0]}");clickTag("syncConfirm");notice("거래 확인을 저장했습니다.")
   clickTag("syncManual_${separate.getString("id")}");clickTag("syncSeparate");clickTag("syncConfirm");notice("거래 확인을 저장했습니다.")
   clickTag("syncCancel_${cancellation.getString("id")}");tag("syncConfirm").assertIsNotEnabled();tag("cancelSummary").assertExists();tag("cancelPreviousMonth").assertExists();clickTag("syncOriginal_${duplicate.getString("id")}");tag("cancelChosen").assertExists();clickTag("syncConfirm");notice("거래 확인을 저장했습니다.")
   clickTag("syncIgnore_${unknown.getString("id")}");clickTag("syncConfirm");notice("거래 확인을 저장했습니다.");tag("syncCounts").assertTextEquals("총 5건 · 가계부에 기록 4건 · 확인 필요 0건")
   step("card_mapping");clickTag("syncBack");text("최신 연결을 조회했습니다.");clickTag("financeCardMapping_0301")
   fun mappingNotice(value:String){compose.waitUntil(20000){runCatching{tag("mappingNotice").assertTextEquals(value);tag("mappingRefresh").assertIsEnabled()}.isSuccess}}
   mappingNotice("금융 카드와 보유 상품을 직접 확인해 연결하세요.");clickTag("mappingChoose_0");tag("mappingSave").assertIsNotEnabled();clickTag("mappingAdd_KB_EASY_ALL_AUTO")
   mappingNotice("상품을 등록했습니다. 연결할 상품을 선택한 뒤 저장해 주세요.");clickTag("mappingTarget_0");tag("mappingSave").assertIsNotEnabled();clickTag("mappingConfirm");clickTag("mappingSave");mappingNotice("상품 연결을 저장하고 서버에서 다시 확인했습니다. 가계부 금액은 바꾸지 않았습니다.")
   clickTag("mappingRefresh");mappingNotice("저장된 연결을 확인했습니다.");tag("mappingState_0").assertTextContains("연결한 상품:",substring=true)
   clickTag("mappingChoose_0");clickTag("mappingTarget_0");clickTag("mappingConfirm");fixture("mappingconflict");clickTag("mappingSave");mappingNotice("연결 상태나 저장 내용이 바뀌었습니다. 새로고침 후 다시 확인해 주세요.");tag("mappingSave").assertIsNotEnabled()
   clickTag("mappingRecover");mappingNotice("저장된 연결을 확인했습니다.");clickTag("mappingUnlink_0");clickTag("mappingConfirm");clickTag("mappingSave");mappingNotice("상품 연결을 저장하고 서버에서 다시 확인했습니다. 가계부 금액은 바꾸지 않았습니다.");tag("mappingState_0").assertTextEquals("아직 상품에 연결하지 않았습니다.")
   clickTag("mappingChoose_0");clickTag("mappingTarget_0");clickTag("mappingConfirm");clickTag("mappingSave");mappingNotice("상품 연결을 저장하고 서버에서 다시 확인했습니다. 가계부 금액은 바꾸지 않았습니다.")
   val mapState=runBlocking{val c=JSONArray(client.request("/v1/finance/connections")).getJSONObject(0);JSONObject(client.request("/v1/finance/connections/${c.getString("id")}/card-mappings"))};Assert.assertEquals(4L,mapState.getJSONArray("groups").getJSONObject(0).getLong("version"));Assert.assertEquals("RULE_REVIEW_REQUIRED",mapState.getJSONArray("groups").getJSONObject(0).getString("calculationStatus"))
   clickTag("mappingBack");text("최신 연결을 조회했습니다.");clickTag("financeTransactions_0301");notice("저장된 결과를 조회했습니다.")
   step("refund");sync("initial","IMPORTED");sync("partial","IMPORTED")
   tag("syncHeldOnly").assertIsOff();text("할부 3개월 · 월 청구액 아님")
   val summary=runBlocking{JSONObject(client.request("/v1/budgets/$month/summary"))};Assert.assertEquals(170000L,summary.getLong("spentWon"));Assert.assertEquals(0,summary.getLong("pendingFinancialTransactions"))
   step("ledger_display")
   val ledgerRows=runBlocking{JSONArray(client.request("/v1/transactions?month=$month"))}
   val performanceBefore=runBlocking{client.request("/v1/cards/performance?month=$month")}
   val financialRows=rows();val original=financialRows.first{it.optLong("amount_won")==50000L};val installment=financialRows.first{it.optLong("amount_won")==90000L}
   click("가계부")
   val originalLedger=original.getString("ledger_id");val installmentLedger=installment.getString("ledger_id")
   compose.waitUntil(15000){runCatching{tag("transaction_$originalLedger").assertIsEnabled()}.isSuccess}
   compose.onNodeWithTag("ledgerMerchant_$originalLedger",useUnmergedTree=true).performScrollTo().assertTextEquals("테스트 가맹점")
   compose.onNodeWithTag("ledgerCard_$originalLedger",useUnmergedTree=true).assertTextContains("KB국민 Easy all",substring=true)
   compose.onNodeWithTag("ledgerNet_$originalLedger",useUnmergedTree=true).assertTextEquals("30,000원")
   compose.onNodeWithTag("ledgerCancellation_$originalLedger",useUnmergedTree=true).assertTextEquals("부분 취소")
   compose.onNodeWithTag("ledgerNet_$installmentLedger",useUnmergedTree=true).performScrollTo().assertTextEquals("70,000원")
   Assert.assertEquals(4,ledgerRows.length());Assert.assertEquals(4,(0 until ledgerRows.length()).map{ledgerRows.getJSONObject(it).getString("id")}.distinct().size)
   Assert.assertEquals(performanceBefore,runBlocking{client.request("/v1/cards/performance?month=$month")})
   Assert.assertEquals(170000L,runBlocking{JSONObject(client.request("/v1/budgets/$month/summary"))}.getLong("spentWon"))
   click("설정");click("금융 연결 관리");text("최신 연결을 조회했습니다.");clickTag("financeTransactions_0301");notice("저장된 결과를 조회했습니다.")
   sync("failed","FAILED")
   // Persisted success with a lost HTTP response: recovery must read stored runs, never repeat POST.
   step("recovery");fixture("lost");clickTag("syncStart");notice("요청 결과를 확인하지 못했습니다. 재전송하지 말고 저장된 결과를 조회해 주세요.");tag("syncStart").assertIsNotEnabled();refresh();tag("syncRun").assertTextContains("가계부 기록 완료",substring=true)
   fixture("malformed");clickTag("syncRefresh");notice("저장된 거래를 불러오지 못했습니다. 연결을 확인하고 다시 조회해 주세요.");tag("syncStart").assertIsNotEnabled();refresh()
   fixture("running");refresh();tag("syncStart").assertIsNotEnabled();fixture("finish");refresh()
   fixture("unknown");refresh();tag("syncStart").assertIsNotEnabled();fixture("active");refresh()
   // Navigation and fresh Activity/login re-read DB, not the previous composition's state.
   clickTag("syncBack");text("최신 연결을 조회했습니다.");clickTag("financeTransactions_0301");notice("저장된 결과를 조회했습니다.")
   step("relogin");scenario.close();scenario=null;scenario=ActivityScenario.launch(MainActivity::class.java)
   text("결제 전 카드 비교");tag("email").assertDoesNotExist()
   click("설정")
   click("금융 연결 관리");text("최신 연결을 조회했습니다.")
   clickTag("financeTransactions_0301");notice("저장된 결과를 조회했습니다.");tag("syncCounts").assertTextEquals("총 5건 · 가계부에 기록 4건 · 확인 필요 0건")
   fixture("expire");clickTag("syncRefresh");text("내 계정으로 시작하기");tag("email").assertIsEnabled();compose.onNodeWithText("로그인").assertIsEnabled()
   val taskEvents=Diagnostics.requestTraceSnapshot().lineSequence().filter{it.isNotBlank()}.map{JSONObject(it)}.filter{it.optString("event")=="SYNC_TASK_END"}.toList()
   Assert.assertTrue("Financial UI completion resumed outside Android Main",taskEvents.isNotEmpty()&&taskEvents.all{it.getString("thread")=="MAIN"})
   val traceFile=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"e2e/$run/request-trace.jsonl")
   traceFile.parentFile?.mkdirs();traceFile.writeText(Diagnostics.requestTraceSnapshot())
  }catch(error:Throwable){
   val chain=JSONArray();var current:Throwable?=error
   repeat(8){val value=current;if(value!=null){
    val frames=JSONArray();value.stackTrace.take(40).forEach{frames.put(JSONObject().put("class",it.className).put("method",it.methodName).put("file",it.fileName).put("line",it.lineNumber))}
    chain.put(JSONObject().put("type",value.javaClass.name).put("frames",frames));current=value.cause
   }}
   val context=InstrumentationRegistry.getInstrumentation().targetContext
   val file=java.io.File(context.getExternalFilesDir(null),"e2e/$run/first-sync-exception.json");file.parentFile?.mkdirs();file.writeText(JSONObject().put("causes",chain).toString(2))
   java.io.File(file.parentFile,"request-trace.jsonl").writeText(Diagnostics.requestTraceSnapshot())
   throw error
  }finally{scenario?.close()}
 }
}
