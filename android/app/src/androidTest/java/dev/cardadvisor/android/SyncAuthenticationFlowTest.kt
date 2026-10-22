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
@RunWith(AndroidJUnit4::class)class SyncAuthenticationFlowTest{
 @get:Rule val ui=createEmptyComposeRule()
 @Test fun authenticationResumesTheOriginalSync()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val api=ApiClient("http://127.0.0.1:18080");var scenario:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","sync-auth-ui-$run@example.test").put("password","Synthetic-password-$run"))));val created=JSONObject(api.request("/v1/finance/connections","POST",JSONObject().put("organization","0301").put("loginId","synthetic-login").put("password","SYNTHETIC_PASSWORD_ONLY").put("consent",true).put("consentVersion","FINANCE_LINK_V1"),UUID.randomUUID().toString())).getJSONObject("connection")
   val today=LocalDate.now(ZoneId.of("Asia/Seoul"));api.request("/v1/finance/connections/${created.getString("id")}/sync","POST",JSONObject().put("from",today.withDayOfMonth(1)).put("to",today).put("expectedVersion",created.getLong("version")),UUID.randomUUID().toString())
   DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.screen="FINANCE";scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java)
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeTransactions_0301").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("financeTransactions_0301").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("syncAuthAnswer").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("syncAuthAnswer").performScrollTo().performTextInput("123456");ui.onNodeWithTag("syncAuthSubmit").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("syncAuthConfirmed").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("syncAuthConfirmed").performScrollTo().performClick();ui.onNodeWithTag("syncAuthSubmit").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("syncRun").assertTextContains("가계부 기록 완료",substring=true)}.isSuccess}
   val runs=JSONArray(api.request("/v1/finance/connections/${created.getString("id")}/sync-runs"));Assert.assertEquals(1,runs.length());Assert.assertEquals("IMPORTED",runs.getJSONObject(0).getString("status"));Assert.assertEquals(1,JSONArray(api.request("/v1/transactions?month=${YearMonth.from(today)}")).length())
  }finally{scenario?.close();DeviceFreeVerificationActivity.client=null;api.clearSession()}
 }
}
