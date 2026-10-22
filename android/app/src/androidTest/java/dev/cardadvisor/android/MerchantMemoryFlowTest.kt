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
@RunWith(AndroidJUnit4::class)class MerchantMemoryFlowTest{
 @get:Rule val ui=createEmptyComposeRule()
 @Test fun rememberReadbackForgetWithoutChangingLedger()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val api=ApiClient("http://127.0.0.1:18080");var screen:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","merchant-ui-$run@example.test").put("password","Synthetic-password-$run"))))
   val connection=JSONObject(api.request("/v1/finance/connections","POST",JSONObject().put("organization","0301").put("loginId","synthetic-login").put("password","SYNTHETIC_PASSWORD_ONLY").put("consent",true).put("consentVersion","FINANCE_LINK_V1"),UUID.randomUUID().toString())).getJSONObject("connection")
   val today=LocalDate.now(ZoneId.of("Asia/Seoul"));api.request("/v1/finance/connections/${connection.getString("id")}/sync","POST",JSONObject().put("from",today.withDayOfMonth(1).toString()).put("to",today.toString()).put("expectedVersion",connection.getLong("version")),UUID.randomUUID().toString())
   val before=JSONArray(api.request("/v1/transactions?month=${YearMonth.from(today)}")).getJSONObject(0);val id=before.getString("id")
   DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.entry=before;DeviceFreeVerificationActivity.screen="CLASSIFY";screen=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java)
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("rememberMerchant").assertExists()}.isSuccess};ui.onNodeWithTag("classify_FOOD").performScrollTo().performClick();ui.onNodeWithTag("rememberMerchant").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("forgetMerchant").assertIsEnabled()}.isSuccess};Assert.assertEquals("FOOD",JSONObject(api.request("/v1/transactions/$id/classification-rule")).getString("category"));screen.recreate()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("forgetMerchant").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("forgetMerchant").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("forgetMerchant").assertDoesNotExist()}.isSuccess};Assert.assertTrue(JSONObject(api.request("/v1/transactions/$id/classification-rule")).isNull("category"));val after=JSONObject(api.request("/v1/transactions/$id"));Assert.assertEquals(before.getString("category"),after.getString("category"));Assert.assertEquals(before.getLong("net_won"),after.getLong("net_won"))
  }finally{screen?.close();DeviceFreeVerificationActivity.client=null;DeviceFreeVerificationActivity.entry=null;api.clearSession()}
 }
}
