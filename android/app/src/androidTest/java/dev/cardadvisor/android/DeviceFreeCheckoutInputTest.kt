package dev.cardadvisor.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.*
@RunWith(AndroidJUnit4::class) class DeviceFreeCheckoutInputTest{
 @get:Rule val ui=createEmptyComposeRule()
 private fun tag(s:String)=ui.onNodeWithTag(s)
 private fun click(s:String){ui.waitUntil(15000){runCatching{ui.onNodeWithText(s).assertIsEnabled()}.isSuccess};val n=ui.onNodeWithText(s);if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performClick()}
 @Test fun inputsReachRecommendationAndInvalidateOnChange()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val context=InstrumentationRegistry.getInstrumentation().targetContext;val api=ApiClient("http://127.0.0.1:18080",SecureSessionStore(context,"checkout-input-$run"));var scenario:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{
   api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","checkout-input-$run@example.test").put("password","Synthetic-password-$run"))));api.request("/v1/demo/setup","POST")
   val m=YearMonth.now(ZoneId.of("Asia/Seoul")).toString();val budget=JSONObject(api.request("/v1/budgets/$m"));budget.put("categories",JSONObject().put("FOOD",20000).put("EDUCATION",50000));api.request("/v1/budgets/$m","PUT",budget)
   DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.screen="ADVISOR";scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java);click("추천");click("결제 정보 수정")
   tag("paymentOrder").performScrollTo().performTextReplacement("60000");tag("paymentPoints").performScrollTo().performTextReplacement("5000");tag("paymentMoney").performScrollTo().performTextReplacement("10000")
   tag("checkoutCategory_FOOD").performScrollTo().performClick();tag("checkoutAllocate").performScrollTo().performClick();tag("checkoutAllocation_FOOD").performScrollTo().performTextReplacement("25000");tag("checkoutAllocation_EDUCATION").performScrollTo().performTextReplacement("30000");click("금액 확인 후 카드 비교")
   ui.waitUntil(15000){ui.onAllNodesWithTag("recommendationResult").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()};ui.onNodeWithText("식비 예산 -5,000원").performScrollTo().assertExists();ui.onNodeWithText("교육 예산 20,000원").performScrollTo().assertExists()
   click("결제 정보 수정");tag("checkoutInstallments").performScrollTo().performTextReplacement("3");Assert.assertTrue(ui.onAllNodesWithTag("recommendationResult").fetchSemanticsNodes().isEmpty());tag("checkoutSplit_YES").performScrollTo().performClick();click("금액 확인 후 카드 비교")
   ui.waitUntil(15000){ui.onAllNodesWithTag("recommendationResult").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()};Assert.assertEquals(0,JSONArray(api.request("/v1/transactions?month=$m")).length())
  }finally{scenario?.close();DeviceFreeVerificationActivity.client=null;api.clearSession()}
 }
}
