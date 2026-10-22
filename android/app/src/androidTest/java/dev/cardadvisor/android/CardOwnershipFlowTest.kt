package dev.cardadvisor.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)class CardOwnershipFlowTest{
 @get:Rule val ui=createEmptyComposeRule()
 private fun click(tag:String){ui.waitUntil(15000){runCatching{ui.onNodeWithTag(tag).assertIsEnabled()}.isSuccess};ui.onNodeWithTag(tag).performScrollTo().performClick()}
 private fun input(tag:String,text:String){ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)}
 private fun credentials(){input("financeLogin","synthetic-login");input("financePassword","SYNTHETIC_PASSWORD_ONLY");click("financeConsent")}
 private fun ownership(){click("financeCardOwnership");input("financeCardNo","9".repeat(16));input("financeCardPassword","12")}
 @Test fun explicitOwnershipIsMaskedClearedAndSavedOnlyAsConnection()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val api=ApiClient("http://127.0.0.1:18080");var scenario:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","ownership-ui-$run@example.test").put("password","Synthetic-password-$run"))));DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.screen="FINANCE";scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java)
   click("financeNew");input("financeOrg","0313");ui.onNodeWithTag("financeCardOwnership").assertDoesNotExist();input("financeOrg","0301");credentials();ownership()
   Assert.assertTrue(ui.onNodeWithTag("financeCardNo").fetchSemanticsNode().config.contains(SemanticsProperties.Password));Assert.assertTrue(ui.onNodeWithTag("financeCardPassword").fetchSemanticsNode().config.contains(SemanticsProperties.Password))
   scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);ui.onNodeWithTag("financeCardNo").assertDoesNotExist();Assert.assertTrue(ui.onNodeWithTag("financePassword").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty());ui.onNodeWithTag("financeSubmit").assertIsNotEnabled()
   credentials();ownership();click("financeSubmit");ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeState_0301").assertTextEquals("연결됨")}.isSuccess};ui.onNodeWithTag("financeCardNo").assertDoesNotExist()
   click("financeReauth_0301");ui.onNodeWithTag("financeCardNo").assertDoesNotExist();credentials();ownership();click("financeSubmit");ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeVersion_0301").assertTextEquals("저장 버전 4")}.isSuccess}
   scenario.recreate();ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeState_0301").assertTextEquals("연결됨")}.isSuccess};val rows=JSONArray(api.request("/v1/finance/connections"));Assert.assertEquals(1,rows.length());Assert.assertEquals("ACTIVE",rows.getJSONObject(0).getString("state"));Assert.assertFalse(rows.toString().contains("9".repeat(16)))
  }finally{scenario?.close();DeviceFreeVerificationActivity.client=null;api.clearSession()}
 }
}
