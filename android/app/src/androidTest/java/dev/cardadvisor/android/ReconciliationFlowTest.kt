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
@RunWith(AndroidJUnit4::class)class ReconciliationFlowTest{
 @get:Rule val ui=createEmptyComposeRule()
 @Test fun explicitAccountListCheckUnlocksOnlyReauthentication()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val api=ApiClient("http://127.0.0.1:18080");var scenario:ActivityScenario<DeviceFreeVerificationActivity>?=null
  try{api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","reconcile-ui-$run@example.test").put("password","Synthetic-password-$run"))));api.request("/v1/finance/connections","POST",JSONObject().put("organization","0301").put("loginId","synthetic-login").put("password","SYNTHETIC_PASSWORD_ONLY").put("consent",true).put("consentVersion","FINANCE_LINK_V1"),UUID.randomUUID().toString())
   DeviceFreeVerificationActivity.client=api;DeviceFreeVerificationActivity.screen="FINANCE";scenario=ActivityScenario.launch(DeviceFreeVerificationActivity::class.java)
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeReconcile_0301").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("financeReconcile_0301").performScrollTo().performClick()
   ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeReauth_0301").assertIsEnabled()}.isSuccess};val saved=JSONArray(api.request("/v1/finance/connections")).getJSONObject(0);Assert.assertEquals("AUTH_REQUIRED",saved.getString("state"));scenario.recreate();ui.waitUntil(15000){runCatching{ui.onNodeWithTag("financeReauth_0301").assertIsEnabled()}.isSuccess}
  }finally{scenario?.close();DeviceFreeVerificationActivity.client=null;api.clearSession()}
 }
}
