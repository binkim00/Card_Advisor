package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.security.MessageDigest
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LedgerDeviceFlowTest {
 @get:Rule val ui=createEmptyComposeRule()
 private fun tag(id:String)=ui.onNodeWithTag(id,useUnmergedTree=true)
 private fun click(text:String){val n=ui.onNode(hasText(text) and hasClickAction());ui.waitUntil(15000){runCatching{n.assertIsEnabled()}.isSuccess};if(text=="취소 금액 반영")n.performClick()else n.performScrollTo().performClick()}
 private fun waitText(text:String){ui.waitUntil(15000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun step(value:String){InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("ledgerStep",value)})}
 @Test fun physicalLedgerAndCalculationsPreservePersonalSession()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;UUID.fromString(run)
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val slot="device-ledger-$run";val store=SecureSessionStore(context,slot);val api=ApiClient(sessionStore=store)
  fun personalDigest():ByteArray?{val file=File(context.noBackupFilesDir,"session-v1.enc");return if(file.exists())MessageDigest.getInstance("SHA-256").digest(file.readBytes())else null}
  val personal=personalDigest();var scenario:ActivityScenario<LedgerVerificationActivity>?=null
  val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString();val date=LocalDate.now(ZoneId.of("Asia/Seoul")).toString()
  suspend fun entries()=JSONArray(api.request("/v1/transactions?month=$month"))
  suspend fun snapshot(net:Long,budget:Long){
   val p=JSONArray(api.request("/v1/cards/performance?month=$month"));val b=(0 until p.length()).map{p.getJSONObject(it)}.first{it.getString("name")=="[가상] B 카드"}
   Assert.assertEquals(270000L+net,b.getLong("performanceWon"));Assert.assertTrue(b.getBoolean("currentQualified"));Assert.assertEquals(net/100,b.getLong("ledgerBenefitUsedWon"));Assert.assertEquals(10000-net/100,b.getLong("remainingCapWon"));Assert.assertEquals(budget,JSONObject(api.request("/v1/budgets/$month/summary")).getLong("spentWon"))
  }
  fun readyRow(id:String){ui.waitUntil(15000){runCatching{tag("transaction_$id").assertIsEnabled()}.isSuccess}}
  fun row(id:String,net:String,status:String){readyRow(id);tag("ledgerNet_$id").performScrollTo().assertIsDisplayed().assertTextEquals(net);tag("ledgerCancellation_$id").performScrollTo().assertIsDisplayed().assertTextEquals(status)}
  fun capture(name:String){val dir=File(context.getExternalFilesDir(null),"ledger-device-preview").apply{mkdirs()};ui.onRoot().captureToImage().asAndroidBitmap().let{b->File(dir,name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
  fun stats(net:String,spent:String){click("예산·실적 상세  +");ui.onNodeWithText("[가상] B 카드 · 다음 달용 실적 $net").performScrollTo().assertIsDisplayed();ui.onNodeWithText("이번 달 예산 지출 $spent").performScrollTo().assertIsDisplayed();click("예산·실적 상세  −")}
  try{
   LedgerVerificationActivity.client=api;scenario=ActivityScenario.launch(LedgerVerificationActivity::class.java)
   ui.waitUntil(15000){ui.onAllNodesWithTag("email").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
   tag("email").performTextReplacement("device-ledger-$run@example.test");tag("password").performTextReplacement("Synthetic-$run");click("가입");waitText("가입했습니다. 가상 데이터로 계산을 시작할 수 있습니다.")
   click("설정");click("동의·계정 관리");tag("emailVerificationOpen").performScrollTo().performClick();waitText("이메일 발송 설정이 아직 준비되지 않았습니다.");tag("recoveryRequest").assertIsNotEnabled();tag("recoveryBack").performScrollTo().performClick();click("홈");step("email_verification_unconfigured")
   click("가상 데이터로 둘러보기  +");click("가상 데이터 준비");waitText("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
   step("manual_purchase");click("가계부");click("+ 수동 구매 기록");click("[가상] B 카드 사용");click("구매 기록 추가");waitText("수동 구매 기록을 추가했습니다.")
   val manual=entries().getJSONObject(0).getString("id");row(manual,"50,000원","결제");tag("ledgerCard_$manual").assertTextEquals("[가상] B 카드");snapshot(50000,50000);stats("320,000원","50,000원")
   tag("transaction_$manual").performScrollTo().performClick();click("부분 취소 반영");tag("cancelAmount").performTextReplacement("20000");click("취소 금액 반영");waitText("부분 취소 20,000원을 반영했습니다.")
   step("manual_partial");row(manual,"30,000원","부분 취소");snapshot(30000,30000);stats("300,000원","30,000원")
   click("이 기록 전액 취소 반영");waitText("테스트 기록의 취소를 반영했습니다.");step("manual_full");row(manual,"0원","전체 취소");snapshot(0,0);stats("270,000원","0원")
   val c=JSONObject(api.request("/v1/finance/connections","POST",JSONObject().put("organization","0301").put("loginId","synthetic-device").put("password","SYNTHETIC_DEVICE_ONLY").put("consent",true).put("consentVersion","FINANCE_LINK_V1"),UUID.randomUUID().toString())).getJSONObject("connection")
   val cid=c.getString("id")
   suspend fun sync(){api.request("/v1/finance/connections/$cid/sync","POST",JSONObject().put("from",date).put("to",date).put("expectedVersion",c.getLong("version")),UUID.randomUUID().toString());click("새로고침");waitText("내역을 조회했습니다.")}
   sync()
   step("mapping_registration");click("설정");click("내 실제 카드 등록")
   val add=tag("productAdd_KB_EASY_ALL_AUTO");ui.waitUntil(15000){runCatching{add.assertIsEnabled()}.isSuccess};add.performScrollTo().performClick()
   waitText("상품을 등록했습니다. 아직 금융 카드에 연결된 것은 아닙니다. 금융 카드 연결하기에서 선택·저장해 주세요.")
   tag("productMapping_KB_EASY_ALL_AUTO").assertTextContains("미연결",substring=true)
   tag("productFinance_KB_EASY_ALL_AUTO").performScrollTo().performClick();waitText("금융 카드와 보유 상품을 직접 확인해 연결하세요.")
   tag("mappingChoose_0").performScrollTo().performClick();tag("mappingSave").assertIsNotEnabled();tag("mappingTarget_0").performScrollTo().performClick();tag("mappingSave").assertIsNotEnabled();tag("mappingConfirm").performScrollTo().performClick();tag("mappingSave").performClick()
   waitText("상품 연결을 저장하고 서버에서 다시 확인했습니다. 가계부 금액은 바꾸지 않았습니다.");step("mapping_saved_readback")
   ui.waitUntil(15000){runCatching{tag("mappingRefresh").assertIsEnabled()}.isSuccess};tag("mappingRefresh").performScrollTo().performClick();waitText("저장된 연결을 확인했습니다.")
   tag("mappingState_0").assertTextContains("연결한 상품:",substring=true);tag("mappingVersion_0").assertTextEquals("서버 저장 버전 1")
   scenario!!.recreate();waitText("결제 전 카드 비교");click("설정");click("내 실제 카드 등록")
   ui.waitUntil(15000){runCatching{tag("productMapping_KB_EASY_ALL_AUTO").assertTextContains("연결 저장됨",substring=true)}.isSuccess}
   tag("productFinance_KB_EASY_ALL_AUTO").performScrollTo().performClick();waitText("금융 카드와 보유 상품을 직접 확인해 연결하세요.");tag("mappingState_0").assertTextContains("연결한 상품:",substring=true);step("mapping_reopened")
   val cards=JSONArray(api.request("/v1/cards"));val card=(0 until cards.length()).map{cards.getJSONObject(it)}.first{it.getString("name").contains("Easy all")}
   click("가계부")
   val financial=(0 until entries().length()).map{entries().getJSONObject(it)}.first{it.getString("source")=="CODEF"}.getString("id")
   step("financial_display");row(financial,"90,000원","결제");tag("ledgerMerchant_$financial").performScrollTo().assertIsDisplayed().assertTextEquals("테스트 온라인스토어");tag("ledgerCard_$financial").performScrollTo().assertIsDisplayed().assertTextContains("KB국민 Easy all",substring=true);tag("ledgerCard_$financial").assertTextContains("끝자리 123",substring=true);snapshot(0,90000)

   val cardId=card.getString("id");api.request("/v1/cards/$cardId/months/$month","PUT",JSONObject().put("untrackedPerformanceWon",450000).put("currentQualified",true).put("remainingCapWon",10000).put("version",0))
   suspend fun actual(expected:Long?,benefit:Long?=null){val all=JSONArray(api.request("/v1/cards/performance?month=$month"));val a=(0 until all.length()).map{all.getJSONObject(it)}.first{it.getString("id")==cardId};if(expected==null)Assert.assertTrue(a.isNull("performanceWon"))else Assert.assertEquals(expected,a.getLong("performanceWon"));Assert.assertTrue(a.getBoolean("currentQualified"));if(benefit==null)Assert.assertTrue(a.isNull("ledgerBenefitUsedWon"))else Assert.assertEquals(benefit,a.getLong("ledgerBenefitUsedWon"))}
   fun openReview(){readyRow(financial);if(ui.onAllNodesWithTag("reviewPerformance_$financial").fetchSemanticsNodes().isEmpty())tag("transaction_$financial").performScrollTo().performClick();tag("reviewPerformance_$financial").performScrollTo().performClick();ui.waitUntil(15000){runCatching{tag("performanceDecision_INCLUDE").assertIsEnabled()}.isSuccess}}
   fun saveDecision(choice:String,total:String){ui.waitUntil(15000){runCatching{tag("performanceDecision_$choice").assertIsEnabled()}.isSuccess};step("performance_saving_"+choice.lowercase());tag("performanceDecision_$choice").performScrollTo().performClick();tag("performanceConfirm").performScrollTo().performClick();tag("performanceSave").performScrollTo().performClick();waitText("실적 확인을 저장하고 서버 계산을 다시 확인했습니다.");tag("performanceTotal").performScrollTo().assertTextEquals(total);tag("performanceCurrentQualification").performScrollTo().assertTextEquals("이번 자격: 충족")}
   fun closeReview(){tag("performanceBack").performScrollTo().performClick()}
   fun openBenefit(){readyRow(financial);if(ui.onAllNodesWithTag("reviewBenefit_$financial").fetchSemanticsNodes().isEmpty())tag("transaction_$financial").performScrollTo().performClick();tag("reviewBenefit_$financial").performScrollTo().performClick();ui.waitUntil(15000){runCatching{tag("benefitDecision_RULE").assertIsEnabled()}.isSuccess}}
   fun saveBenefit(){ui.waitUntil(15000){runCatching{tag("benefitDecision_RULE").assertIsEnabled()}.isSuccess};tag("benefitDecision_RULE").performScrollTo().performClick();tag("benefitArea_FOOD").performScrollTo().performClick();tag("benefitConfirm").performScrollTo().performClick();tag("benefitSave").performScrollTo().performClick();waitText("거래의 혜택 확인을 저장하고 다시 조회했습니다.")}
   fun benefitTotals(used:String,remaining:String){tag("benefitUsed").performScrollTo().assertTextEquals(used);tag("benefitRemaining").performScrollTo().assertTextEquals(remaining)}
   fun closeBenefit(){tag("benefitBack").performScrollTo().performClick()}
   openReview();saveDecision("INCLUDE","다음 달용 총실적: 540,000원");actual(540000);step("performance_included")
   saveDecision("EXCLUDE","다음 달용 총실적: 450,000원");actual(450000);step("performance_excluded")
   saveDecision("UNKNOWN","다음 달용 총실적: 미확인");actual(null);step("performance_unknown")
   saveDecision("INCLUDE","다음 달용 총실적: 540,000원");closeReview()
   openBenefit();saveBenefit();benefitTotals("예상 혜택 사용: 미확인","예상 잔여 한도: 미확인");tag("benefitTier_1").performScrollTo().performClick();tag("benefitCoverage").performScrollTo().performClick();tag("benefitMonthSave").performScrollTo().performClick();waitText("월별 혜택 기준을 저장하고 다시 계산했습니다.");benefitTotals("예상 혜택 사용: 2,700원","예상 잔여 한도: 13,300원");actual(540000,2700);step("benefit_month_saved");closeBenefit()
   sync();step("financial_partial");row(financial,"70,000원","부분 취소");snapshot(0,70000);actual(null);openReview()
   saveDecision("INCLUDE","다음 달용 총실적: 미확인");tag("performanceContribution").performScrollTo().assertTextEquals("금융 실적 전체: 미확인");step("performance_unknown_refund_date")
   tag("performanceRefundDate_0").performScrollTo().performTextReplacement(date);tag("performanceConfirm").performScrollTo().performClick();tag("performanceSave").performScrollTo().performClick();waitText("실적 확인을 저장하고 서버 계산을 다시 확인했습니다.");tag("performanceTotal").performScrollTo().assertTextEquals("다음 달용 총실적: 520,000원");actual(520000);step("performance_partial_receipt");closeReview();openBenefit();saveBenefit();benefitTotals("예상 혜택 사용: 2,100원","예상 잔여 한도: 13,900원");actual(520000,2100);step("benefit_partial_restored");closeBenefit()
   sync();step("financial_full");row(financial,"0원","전체 취소");snapshot(0,0);actual(null);openReview()
   for(i in 0..1)tag("performanceRefundDate_$i").performScrollTo().performTextReplacement(date)
   saveDecision("INCLUDE","다음 달용 총실적: 450,000원");actual(450000);step("performance_full_receipt")
   // Concurrent editor changes are rejected without overwriting the newer decision.
   val tid=entries().let{array->(0 until array.length()).map{array.getJSONObject(it)}.first{it.getString("id")==financial}.getString("financial_transaction_id")}
   val rv=JSONObject(api.request("/v1/finance/transactions/$tid/performance-review"));val other=JSONObject().put("expectedVersion",rv.getLong("version")).put("expectedTransactionVersion",rv.getLong("transactionVersion")).put("expectedMappingVersion",rv.getLong("mappingVersion")).put("decision","EXCLUDE").put("confirmed",true).put("refunds",JSONArray().apply{val source=rv.getJSONArray("refunds");for(i in 0 until source.length()){val f=source.getJSONObject(i);put(JSONObject().put("cancellationId",f.getString("cancellationId")).put("receiptDate",f.opt("receiptDate")))}})
   try{api.request("/v1/finance/transactions/$tid/performance-review","PUT",other)}catch(e:ApiException){step("api_failure_"+e.code.lowercase().replace(Regex("[^a-z_]"),""));throw e}
   tag("performanceConfirm").performScrollTo().performClick();tag("performanceSave").performScrollTo().performClick();waitText("거래·상품 연결 또는 확인 버전이 바뀌었습니다. 입력은 유지했어요. 최신 상태를 조회한 뒤 다시 확인해 주세요.");tag("performanceSave").assertIsNotEnabled();step("performance_version_conflict")
   tag("performanceRefresh").performScrollTo().performClick();waitText("저장 상태와 월별 계산을 다시 확인했습니다.");tag("performanceDecision_EXCLUDE").assertIsSelected();saveDecision("INCLUDE","다음 달용 총실적: 450,000원");closeReview()
   openBenefit();saveBenefit();benefitTotals("예상 혜택 사용: 0원","예상 잔여 한도: 16,000원");actual(450000,0);step("benefit_full_restored");closeBenefit()
   val checkout=JSONObject(api.request("/v1/checkout-sessions","POST",JSONObject().put("orderValueWon",50000).put("pointUsedWon",0).put("prepaidMoneyUsedWon",0).put("cardChargeWon",50000).put("category","SHOPPING").put("paymentMethod","NAVER_PAY_CARD").put("installments",1).put("splitApproval","NO").put("confirmedByUser",true).put("purchaseFacts",JSONObject().put("merchant","NAVER_PLUS_STORE").put("purchaseKind","GENERAL_GOODS").put("confirmed",true))))
   val rec=JSONObject(api.request("/v1/recommendations","POST",JSONObject().put("checkoutId",checkout.getString("id")).put("snapshotVersion",checkout.getLong("snapshotVersion")).put("mode","BENEFIT").put("maxOpportunityCostWon",0)));Assert.assertEquals(cardId,rec.getJSONObject("result").getString("recommendedCardId"));step("benefit_recommendation_verified")
   Assert.assertEquals(2,entries().length());step("calculations_verified")
   click("설정");click("나가기");waitText("로그아웃했습니다.");tag("passwordRecoveryOpen").performScrollTo().performClick();tag("recoveryConfirm").assertIsNotEnabled();tag("recoveryEmail").performScrollTo().performTextReplacement("device-ledger-$run@example.test");tag("recoveryRequest").performScrollTo().performClick();waitText("서버의 이메일 발송 설정이 아직 준비되지 않았습니다. 기존 로그인은 계속 사용할 수 있습니다.");tag("recoveryBack").performScrollTo().performClick();step("password_recovery_unconfigured")
  }finally{
   scenario?.close();LedgerVerificationActivity.client=null;store.clear();java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("dev.cardadvisor.$slot")}
   Assert.assertTrue("Personal session ciphertext changed; contents withheld",java.util.Arrays.equals(personal,personalDigest()));step("personal_session_preserved")
  }
 }
}
