package dev.cardadvisor.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CheckoutRecommendationFlowTest {
 @get:Rule val ui=createEmptyComposeRule()
 private fun tag(s:String)=ui.onNodeWithTag(s)
 private fun click(s:String){ui.waitUntil(15000){runCatching{ui.onNodeWithText(s).assertIsEnabled()}.isSuccess};ui.onNodeWithText(s).performScrollTo().performClick()}
 private fun resultVisible()=ui.onAllNodesWithTag("recommendationResult").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()
 @Test fun detectedCheckoutConfirmationCallsRealApiAndInvalidatesChangedOrClearedSource()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;UUID.fromString(run)
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val api=ApiClient(sessionStore=SecureSessionStore(context,"checkout-flow-$run"))
  var scenario:ActivityScenario<CheckoutFlowVerificationActivity>?=null
  try{
   api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","checkout-flow-$run@example.test").put("password","Synthetic-checkout-$run"))))
   api.request("/v1/demo/setup","POST")
   val products=JSONArray(api.request("/v1/card-products"));val product=(0 until products.length()).map{products.getJSONObject(it)}.first{it.getString("id")=="HANA_TRAVELOG"}
   api.request("/v1/cards/from-product","POST",JSONObject().put("productId",product.getString("id")).put("expectedRuleVersion",product.getString("ruleVersion")))
   val prefs=JSONObject(api.request("/v1/me/preferences"));api.request("/v1/me/consents/CHECKOUT_DETECTION","PUT",JSONObject().put("accepted",true).put("policyVersion",prefs.getString("policyVersion")).put("expectedVersion",prefs.getLong("version")))
   CheckoutFlowVerificationActivity.client=api;scenario=ActivityScenario.launch(CheckoutFlowVerificationActivity::class.java)
   ui.waitUntil(15000){ui.onAllNodesWithTag("detectionNotice").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
   ui.waitUntil(15000){runCatching{tag("detectionNotice").assertTextContains("동의가 확인",substring=true)}.isSuccess}
   fun observe(points:Long?,total:Long?=62000,pkg:String="com.navercorp.navershopping")=ui.runOnIdle{
    CheckoutDetection.authorize(true)
    val values=mutableMapOf(DetectionAmount.FINAL_TOTAL to setOf(50000L));total?.let{values[DetectionAmount.ORDER]=setOf(it)};points?.let{values[DetectionAmount.POINTS]=setOf(it)}
    CheckoutDetection.accept(CheckoutObservation(pkg,"synthetic-route",true,false,values))
   }
   observe(null,null)
   for(field in listOf("paymentOrder","paymentPoints","paymentMoney"))Assert.assertEquals("",tag(field).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
   tag("detectionUse").performScrollTo().assertIsNotEnabled()
   tag("paymentOrder").performScrollTo().performTextReplacement("62000");tag("paymentPoints").performScrollTo().performTextReplacement("2000");tag("paymentMoney").performScrollTo().performTextReplacement("10000")
   tag("detectionMethod_DIRECT_CARD").performScrollTo().performClick();tag("detectionConfirmFacts").performScrollTo().performClick();tag("detectionUse").performScrollTo().performClick()
   ui.waitUntil(15000){resultVisible()};ui.onNodeWithText("이번 혜택 150원").performScrollTo().assertIsDisplayed()
   observe(3000)
   ui.waitUntil(5000){!resultVisible()}
   click("설정");click("쇼핑 주문서 감지")
   Assert.assertEquals("",tag("paymentMoney").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
   // Money was not observed: the second confirmation must not silently assume zero.
   tag("detectionUse").performScrollTo().assertIsNotEnabled();tag("paymentMoney").performScrollTo().performTextReplacement("10000")
   tag("detectionMethod_NAVER_PAY_CARD").performScrollTo().performClick();tag("detectionConfirmFacts").performScrollTo().performClick();tag("detectionUse").performScrollTo().performClick()
   ui.waitUntil(15000){resultVisible()};ui.onNodeWithText("이번 혜택 147원").performScrollTo().assertIsDisplayed()
   observe(4000,pkg="com.musinsa.store");ui.waitUntil(5000){!resultVisible()}
   click("설정");click("쇼핑 주문서 감지")
   tag("paymentMoney").performScrollTo().performTextReplacement("10000");tag("detectionMethod_DIRECT_CARD").performScrollTo().performClick();tag("detectionConfirmFacts").performScrollTo().performClick();tag("detectionUse").performScrollTo().performClick()
   // The isolated backend deliberately delays its third recommendation response.
   ui.runOnIdle{CheckoutDetection.clear()}
   ui.waitUntil(15000){runCatching{tag("compareCards").assertIsEnabled()}.isSuccess}
   Assert.assertFalse("A stale recommendation reappeared after source invalidation",resultVisible())
   val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
   Assert.assertEquals(0,JSONArray(api.request("/v1/transactions?month=$month")).length())
   Assert.assertEquals(0L,JSONObject(api.request("/v1/budgets/$month/summary")).getLong("spentWon"))
   // An actual-card manual purchase has its own explicit confirmation, independent of an expired recommendation.
   click("가계부");click("+ 수동 구매 기록")
   tag("paymentOrder").performScrollTo().performTextReplacement("20000");tag("paymentPoints").performScrollTo().performTextReplacement("0");tag("paymentMoney").performScrollTo().performTextReplacement("0")
   click(product.getString("name")+" 사용")
   ui.onNodeWithText("구매 기록 추가").performScrollTo().assertIsNotEnabled();tag("manualFactsConfirmed").performScrollTo().performClick();click("구매 기록 추가")
   ui.waitUntil(15000){runCatching{ui.onNodeWithText("수동 구매 기록을 추가했습니다.").assertExists()}.isSuccess}
   Assert.assertEquals(1,JSONArray(api.request("/v1/transactions?month=$month")).length())
   Assert.assertEquals(20000L,JSONObject(api.request("/v1/budgets/$month/summary")).getLong("spentWon"))
  }finally{scenario?.close();CheckoutFlowVerificationActivity.client=null;CheckoutDetection.authorize(false);api.clearSession()}
 }
}
