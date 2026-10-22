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
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class DeviceFreeBudgetFlowTest{
 @get:Rule val ui=createEmptyComposeRule()
 private fun click(s:String){ui.waitUntil(15000){ui.onAllNodes(hasText(s) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()};val node=ui.onNode(hasText(s) and hasClickAction());if(runCatching{node.assertIsDisplayed()}.isFailure)node.performScrollTo();node.performClick()}
 @Test fun splitAndCrossMonthAllocationThroughAndroidApiDb()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val api=ApiClient("http://127.0.0.1:18080");val month=YearMonth.now(ZoneId.of("Asia/Seoul"));var scenario:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{
   api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","budget-flow-$run@example.test").put("password","Synthetic-password-$run"))));api.request("/v1/demo/setup","POST")
   val card=JSONArray(api.request("/v1/cards")).getJSONObject(0).getString("id")
   val created=JSONObject(api.request("/v1/transactions/manual","POST",JSONObject().put("clientId",UUID.randomUUID()).put("amountWon",1000).put("cardChargeWon",1000).put("cardId",card).put("purchasedAt",month.minusMonths(1).atDay(1).toString()).put("category","SHOPPING").put("included",true)));val tx=created.getString("id")
   DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.entry=JSONObject(api.request("/v1/transactions/$tx"));DeviceFreeVerificationActivity.screen="CLASSIFY";scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java)
   ui.onNodeWithTag("splitBudget").performScrollTo().performClick();ui.onNodeWithTag("allocation_SHOPPING").performScrollTo().performTextReplacement("600");ui.onNodeWithTag("allocation_FOOD").performScrollTo().performTextReplacement("400");click("분류 저장")
   ui.waitUntil(15000){ui.onAllNodesWithText("저장 완료").fetchSemanticsNodes().isNotEmpty()};scenario.close();scenario=null
   Assert.assertEquals(400,JSONObject(api.request("/v1/transactions/$tx")).getJSONObject("allocations_won").getInt("FOOD"))
   val plan=UUID.randomUUID().toString();api.request("/v1/fixed-costs","POST",JSONObject().put("id",plan).put("name","합성 고정비").put("amountWon",600).put("category","HOUSING").put("dayOfMonth",1).put("effectiveMonth",month.toString()).put("active",true).put("version",0))
   DeviceFreeVerificationActivity.screen="FIXED";scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java);click("합성 고정비 거래 연결")
   ui.onNodeWithTag("fixedPaymentMonth").performScrollTo().performTextReplacement(month.minusMonths(1).toString());click("다른 달 결제 불러오기")
   ui.waitUntil(15000){ui.onAllNodesWithTag("fixedPart_$tx").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("fixedPart_$tx").performScrollTo().performTextReplacement("500");click("이 거래를 연결")
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("fixedBalance_$plan").assertTextContains("잔여 100원",substring=true)}.isSuccess}
   api.request("/v1/transactions/$tx/cancellations","POST",JSONObject().put("clientId",UUID.randomUUID()).put("amountWon",250).put("cardRefundWon",250));click("최신 고정비 불러오기 (입력 초기화)")
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("fixedBalance_$plan").assertTextContains("잔여 225원",substring=true)}.isSuccess}
  }finally{scenario?.close();DeviceFreeVerificationActivity.client=null;DeviceFreeVerificationActivity.entry=null;api.clearSession()}
 }
}
