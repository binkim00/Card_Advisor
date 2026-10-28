package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AuthInputFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun wait(text:String){ui.waitUntil(15000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){ui.waitUntil(15000){ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val n=ui.onNode(hasText(text) and hasClickAction());if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performClick()}
 private fun input(tag:String,value:String){val n=ui.onNodeWithTag(tag);if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performTextReplacement(value)}
 @Test fun invalidInputDuplicateWrongPasswordThenSuccessfulLogin(){
  ui.runOnUiThread{ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
  val run=InstrumentationRegistry.getArguments().getString("runId")!!.also{require(Regex("[a-f0-9-]{36}").matches(it))}
  val email="auth-ui-$run@example.test";val password="Synthetic-"+UUID.randomUUID().toString()
  val before=Diagnostics.requestTraceSnapshot()
  click("가입");wait("이메일 형식을 확인해 주세요.");ui.onNodeWithTag("authError").assertIsDisplayed()
  input("email",email);input("password","short");click("가입");wait("비밀번호는 12~128자로 입력해 주세요.");ui.onNodeWithTag("authError").assertIsDisplayed()
  Assert.assertEquals(before,Diagnostics.requestTraceSnapshot())
  try{
   input("password",password);click("가입");wait("가입했습니다. 내 카드를 등록해 시작해 보세요.")
   click("설정");click("나가기");wait("로그아웃했습니다.")
   input("password",password);click("가입");wait("이미 등록된 이메일일 수 있습니다. 로그인으로 확인해 주세요.");ui.onNodeWithTag("authError").assertIsDisplayed()
   input("password","Wrong-password-123");click("로그인");wait("이메일 또는 비밀번호를 확인해 주세요.");ui.onNodeWithTag("authError").assertIsDisplayed()
   input("password",password);click("로그인");wait("로그인했습니다.")
  }finally{
   // Only this test's exact account, no provider endpoints. Do not persist auth tokens/passwords.
   runBlocking{val api=ApiClient();api.setSession(JSONObject(api.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",password))));api.request("/v1/me/deletion","POST",JSONObject().put("password",password).put("confirmation","계정 삭제"));api.token=null;api.refreshToken=null}
  }
 }
}
