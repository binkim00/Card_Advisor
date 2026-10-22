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
import java.util.UUID
import java.time.*
@RunWith(AndroidJUnit4::class)class ActualQualificationFlowTest{
 @get:Rule val ui=createEmptyComposeRule()
 @Test fun saveReadbackConflictAndGrace()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val api=ApiClient("http://127.0.0.1:18080");var scenario:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","qualification-ui-$run@example.test").put("password","Synthetic-password-$run"))));val catalog=JSONArray(api.request("/v1/card-products"));val product=(0 until catalog.length()).map{catalog.getJSONObject(it)}.first{it.getString("id")=="KB_EASY_ALL_AUTO"};val created=JSONObject(api.request("/v1/cards/from-product","POST",JSONObject().put("productId",product.getString("id")).put("expectedRuleVersion",product.getString("ruleVersion"))))
   val card=created.getString("id");val today=LocalDate.now(ZoneId.of("Asia/Seoul"));val month=YearMonth.from(today).toString();DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.screen="QUALIFICATION";DeviceFreeVerificationActivity.entry=JSONObject().put("id",card).put("month",month);scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java)
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("qualificationAmount").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("qualificationAmount").performScrollTo().performTextInput("0");ui.onNodeWithTag("qualificationFirstUse").performScrollTo().performTextInput(today.toString());ui.onNodeWithTag("qualificationConfirmed").performScrollTo().performClick();ui.onNodeWithTag("qualificationSave").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("actualQualificationNotice").assertTextContains("저장하고 재조회",substring=true)}.isSuccess};val path="/v1/cards/$card/qualification?month=$month";val saved=JSONObject(api.request(path));Assert.assertEquals(1,saved.getJSONObject("calculation").getInt("tier"));scenario.recreate();ui.waitUntil(15000){runCatching{ui.onNodeWithTag("qualificationAmount").assertTextContains("0")}.isSuccess}
   api.request(path,"PUT",JSONObject().put("expectedVersion",saved.getLong("version")).put("expectedStateVersion",saved.getLong("stateVersion")).put("previousPerformanceWon",1000000).put("previousSource","USER_INPUT").put("asOf",today.toString()).put("firstUseDate",today.toString()).put("confirmed",true));ui.onNodeWithTag("qualificationConfirmed").performScrollTo().performClick();ui.onNodeWithTag("qualificationSave").performScrollTo().performClick();ui.waitUntil(15000){runCatching{ui.onNodeWithTag("actualQualificationNotice").assertTextContains("입력은 보존",substring=true)}.isSuccess};ui.onNodeWithTag("qualificationAmount").assertTextContains("0");Assert.assertEquals(2,JSONObject(api.request(path)).getJSONObject("calculation").getInt("tier"))
  }finally{scenario?.close();DeviceFreeVerificationActivity.client=null;DeviceFreeVerificationActivity.entry=null;api.clearSession()}
 }
}
