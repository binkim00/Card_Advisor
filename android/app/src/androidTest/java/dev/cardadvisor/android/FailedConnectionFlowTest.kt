package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class FailedConnectionFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 fun wait(text:String){ui.waitUntil(20000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 fun click(text:String){ui.waitUntil(20000){ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val n=ui.onNode(hasText(text) and hasClickAction());if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performClick()}
 fun tag(id:String){ui.waitUntil(20000){ui.onAllNodes(hasTestTag(id) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val n=ui.onNodeWithTag(id);if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performClick()}
 fun input(id:String,value:String){val n=ui.onNodeWithTag(id);if(runCatching{n.assertIsDisplayed()}.isFailure)n.performScrollTo();n.performTextReplacement(value)}
 fun credentials(){input("financeLogin","synthetic-failed-user");input("financePassword","SYNTHETIC_ONLY_PASSWORD");tag("financeConsent")}
 fun create(org:String){tag("financeNew");input("financeOrg",org);credentials();tag("financeSubmit")}
 fun state(org:String,value:String){ui.waitUntil(20000){runCatching{ui.onNodeWithTag("financeState_$org").assertTextEquals(value);ui.onNodeWithTag("financeRefresh").assertIsEnabled()}.isSuccess}}
 @Test fun failedRetryAndLocalRemoval(){
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;UUID.fromString(run)
  ui.runOnUiThread{ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
  input("email","failed-ui-$run@example.test");input("password","Synthetic-"+UUID.randomUUID());click("가입");wait("가입했습니다. 내 카드를 등록해 시작해 보세요.")
  click("설정");click("금융 연결 관리");wait("연결 정보를 불러오지 못했습니다. 최신 연결 조회로 다시 확인해 주세요.");tag("financeRefresh");wait("저장된 금융 연결이 없습니다.")
  ui.onNodeWithTag("financeEnvironment").assertTextEquals("합성 응답 검증용 · 실제 금융정보가 아닙니다.")
  create("9999");state("9999","연결 실패");tag("financeRetry_9999");ui.onNodeWithTag("financeOrg").assertDoesNotExist();Assert.assertTrue("Credential retained",ui.onNodeWithTag("financePassword").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty())
  credentials();tag("financeSubmit");state("9999","연결됨");ui.onNodeWithTag("financeRemove_9999").assertDoesNotExist()
  tag("financeDisconnect_9999");tag("financeSubmit");state("9999","해제됨")
  create("9998");state("9998","연결 실패");tag("financeRemove_9998");tag("financeRemoveConfirm");wait("실패 기록을 삭제했습니다. 외부 해제 요청은 보내지 않았습니다.")
  ui.onNodeWithTag("financeState_9998").assertDoesNotExist();tag("financeRefresh");wait("최신 연결을 조회했습니다.");ui.onNodeWithTag("financeState_9998").assertDoesNotExist()
 }
}
