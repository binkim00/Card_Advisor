package dev.cardadvisor.android

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic credentials only. No screenshots, UI dumps, or raw assertion logs saved. */
@RunWith(AndroidJUnit4::class)
class FinanceFlowTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun waitText(text:String){compose.waitUntil(20000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun tag(id:String)=compose.onNodeWithTag(id)
 private fun clickTag(id:String){compose.waitUntil(20000){runCatching{tag(id).assertIsEnabled()}.isSuccess};tag(id).performScrollTo().performClick()}
 private fun click(text:String){compose.onNodeWithText(text).performScrollTo().performClick()}
 private fun waitState(org:String,state:String,version:Long?=null){
  compose.waitUntil(20000){runCatching{tag("financeState_$org").assertTextEquals(state);if(version!=null)tag("financeVersion_$org").assertTextEquals("저장 버전 $version");tag("financeRefresh").assertIsEnabled()}.isSuccess}
 }
 private fun credentials(){
  tag("financeLogin").performScrollTo().performTextReplacement("synthetic-login")
  tag("financePassword").performScrollTo().performTextReplacement("SYNTHETIC_PASSWORD_ONLY")
  clickTag("financeConsent")
 }
 private fun create(org:String){clickTag("financeNew");tag("financeOrg").performScrollTo().performTextReplacement(org);credentials();clickTag("financeSubmit")}
 @Test fun connectedLifecycleAndUnknown(){
  val id=requireNotNull(InstrumentationRegistry.getArguments().getString("runId"));UUID.fromString(id)
  val email="finance-ui-$id@example.test";val pw="UiTest-"+UUID.randomUUID();val second=ApiClient()
  var scenario:ActivityScenario<MainActivity>?=null
  try{
   scenario=ActivityScenario.launch(MainActivity::class.java).onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
   tag("email").performTextReplacement(email);tag("password").performTextReplacement(pw)
   click("가입");waitText("가입했습니다. 내 카드를 등록해 시작해 보세요.")
   click("금융 연결 관리");waitText("연결 정보를 불러오지 못했습니다. 최신 연결 조회로 다시 확인해 주세요.")
   tag("financeNew").assertIsNotEnabled();clickTag("financeRefresh");waitText("저장된 금융 연결이 없습니다.")
   tag("financeEnvironment").assertTextEquals("합성 응답 검증용 · 실제 금융정보가 아닙니다.")
   clickTag("financeNew");tag("financeOrg").performScrollTo().performTextReplacement("9999")
   tag("financeSubmit").assertIsNotEnabled();credentials()
   scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
   Assert.assertTrue("Background retained credentials",tag("financePassword").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty())
   tag("financeSubmit").assertIsNotEnabled();credentials();clickTag("financeSubmit")
   waitState("9999","연결됨",2);clickTag("financeInspect_9999");waitText(financeOutcome("SUCCEEDED"))
   clickTag("financeReauth_9999")
   Assert.assertTrue("Credential was retained",tag("financePassword").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty())
   credentials();clickTag("financeSubmit");waitState("9999","연결됨",4)
   // A genuine second session changes the stored version while the first screen holds its draft.
   clickTag("financeReauth_9999");credentials()
   runBlocking{
    second.token=JSONObject(second.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",pw))).getString("accessToken")
    val connection=JSONArray(second.request("/v1/finance/connections")).getJSONObject(0)
    second.request("/v1/finance/connections/${connection.getString("id")}/reauthorize","POST",JSONObject().put("expectedVersion",connection.getLong("version")).put("loginId","synthetic-login").put("password","SYNTHETIC_PASSWORD_ONLY").put("consent",true).put("consentVersion","FINANCE_LINK_V1"),UUID.randomUUID().toString())
   }
   clickTag("financeSubmit");waitText("연결 상태가 변경되었습니다. 최신 연결을 조회한 뒤 다시 선택해 주세요.")
   tag("financeNew").assertIsNotEnabled();clickTag("financeRefresh");waitState("9999","연결됨",6)
   clickTag("financeDisconnect_9999");clickTag("financeSubmit");waitState("9999","해제됨",8)
   create("9998");waitState("9998","결과 확인 필요 · 보류",2)
   tag("financeReauth_9998").assertIsNotEnabled();tag("financeDisconnect_9998").assertIsNotEnabled()
   clickTag("financeInspect_9998");waitText(financeOutcome("UNKNOWN"))
   clickTag("financeNew");tag("financeOrg").performScrollTo().performTextReplacement("9998");credentials();tag("financeSubmit").assertIsNotEnabled();click("입력 닫기")
   create("9997");waitState("9997","연결됨",2)
   clickTag("financeReauth_9997");credentials();clickTag("financeSubmit");waitState("9997","결과 확인 필요 · 보류",4)
   create("9996");waitState("9996","연결됨",2)
   clickTag("financeDisconnect_9996");clickTag("financeSubmit");waitState("9996","결과 확인 필요 · 보류",4)
   click("설정");click("금융 연결 관리");waitText("최신 연결을 조회했습니다.")
   for(org in listOf("9998","9997","9996")){waitState(org,"결과 확인 필요 · 보류");tag("financeReauth_$org").assertIsNotEnabled();tag("financeDisconnect_$org").assertIsNotEnabled()}
   scenario.close();scenario=null
   // A fresh Activity and login fetch UNKNOWN from the DB; UI memory is not the evidence.
   scenario=ActivityScenario.launch(MainActivity::class.java).onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
   tag("email").performTextReplacement(email);tag("password").performTextReplacement(pw);click("로그인");waitText("로그인했습니다.")
   click("금융 연결 관리");waitState("9998","결과 확인 필요 · 보류");waitState("9997","결과 확인 필요 · 보류");waitState("9996","결과 확인 필요 · 보류")
   runBlocking{second.request("/v1/fixture/expire","POST")}
   clickTag("financeRefresh");waitText("로그인이 만료되었습니다. 나가기 후 다시 로그인해 주세요.")
   click("나가기");waitText("개발용 로컬 계정")
  }finally{scenario?.close()}
 }
}
