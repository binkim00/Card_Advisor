package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Activity, local HTTP API and PostgreSQL. Only generated financial inputs. */
@RunWith(AndroidJUnit4::class)
class ServiceCompletionFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val run=InstrumentationRegistry.getArguments().getString("runId")!!.also{require(Regex("[a-f0-9-]{36}").matches(it))}
 private val email="service-ui-$run@example.test"
 private val password="Synthetic-"+UUID.nameUUIDFromBytes(run.toByteArray()).toString()
 private fun wait(text:String){ui.waitUntil(20000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){ui.waitUntil(20000){ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val node=ui.onNode(hasText(text) and hasClickAction());if(runCatching{node.assertIsDisplayed()}.isFailure)node.performScrollTo();node.performClick()}
 private fun tag(tag:String){ui.waitUntil(20000){ui.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val node=ui.onNodeWithTag(tag);if(runCatching{node.assertIsDisplayed()}.isFailure)node.performScrollTo();node.performClick()}
 private fun input(tag:String,value:String){val node=ui.onNodeWithTag(tag);if(runCatching{node.assertIsDisplayed()}.isFailure)node.performScrollTo();node.performTextReplacement(value)}
 private fun detail(){val m=SemanticsMatcher("ledger row"){it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}.startsWith("transaction_")} and isEnabled();ui.waitUntil(20000){ui.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()};ui.onAllNodes(m).onFirst().performScrollTo().performClick()}
 @After fun record(){val dir=File(ui.activity.getExternalFilesDir(null),"e2e/$run").apply{mkdirs()};File(dir,"request-trace.jsonl").writeText(Diagnostics.requestTraceSnapshot())}
 private fun authenticate(signup:Boolean){ui.runOnUiThread{ui.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)};input("email",email);input("password",password);click(if(signup)"가입" else "로그인");wait(if(signup)"가입했습니다. 내 카드를 등록해 시작해 보세요." else "로그인했습니다.")}
 @Test fun actualCardsConsentPurchaseRefundPersistence(){
  authenticate(true)
  click("내 실제 카드 등록");wait("검수 범위와 약관 버전을 확인한 뒤 보유 카드를 추가해 주세요.")
  for(id in listOf("KB_EASY_ALL_AUTO","HANA_TRAVELOG","HANA_TRAVELGO_BANK")){tag("productAdd_$id");wait("내 실제 카드에 추가했습니다. 실적이 필요한 카드는 월별 기초값을 입력해 주세요.")}
  click("설정");click("월별 예산 수정");wait(java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString()+" 저장된 예산이 없습니다. 금액을 입력해 새로 저장해 주세요.")
  input("budgetTotal","120000");input("budgetFixed","30000");tag("budgetEnabled_SHOPPING");input("budgetLimit_SHOPPING","70000");click("월 예산 저장");wait(java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString()+" 예산을 저장하고 다시 불러왔습니다.")
  click("설정");click("동의·계정 관리");wait("저장된 설정을 조회했습니다.")
  for(purpose in listOf("AI","CHECKOUT_DETECTION")){tag("consentAction_$purpose");tag("consentAccept");ui.waitUntil(20000){ui.onAllNodes(hasTestTag("consentState_$purpose") and hasText("동의함")).fetchSemanticsNodes().isNotEmpty()}}
  tag("accountRenew");wait("세션을 갱신했습니다.")
  click("추천");tag("actualFactsConfirmed");click("금액 확인 후 카드 비교");wait("하나카드 트래블로그 체크카드 · 추천")
  ui.onNodeWithText("이번 혜택 150원").assertExists();ui.onNodeWithText("구매 후 전체 예산 70,000원").assertExists()
  tag("recommendationExplain");wait("계산 결과에 따른 기본 설명")
  click("가계부");click("+ 수동 구매 기록");click("하나카드 트래블로그 체크카드 사용");click("구매 기록 추가");wait("수동 구매 기록을 추가했습니다.")
  detail();click("부분 취소 반영");input("cancelReceivedDate","2000-01-01");input("cancelAmount","20000");ui.onNodeWithText("취소 금액 반영").assertIsNotEnabled()
  input("cancelReceivedDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());click("취소 금액 반영");wait("부분 취소 20,000원을 반영했습니다.")
  ui.onNodeWithText("남은 지출 30,000원").assertExists();click("예산·실적 상세  +");wait("연결 구매 순예상적립: 90원")
  click("추천");click("금액 확인 후 카드 비교");wait("구매 후 전체 예산 40,000원")
  click("설정");click("소비 분석·예산 제안");wait("저장된 원장과 예산으로 분석했습니다.");ui.onNodeWithText("계산 결과에 따른 기본 설명").assertExists()
  click("설정");click("동의·계정 관리");wait("저장된 설정을 조회했습니다.");tag("consentAction_AI");wait("동의를 철회했습니다. 해당 목적의 새 처리를 중단합니다.")
  click("나가기");wait("로그아웃했습니다.");input("password",password);click("로그인");wait("로그인했습니다.")
  click("가계부");detail();wait("남은 지출 30,000원")
  File(ui.activity.getExternalFilesDir(null),"e2e/$run/evidence.json").apply{parentFile!!.mkdirs()}.writeText(JSONObject().put("runId",run).put("status","UI_PASSED_PENDING_DATABASE_CHECK").put("purchaseWon",50000).put("cancelWon",20000).put("netWon",30000).put("estimatedPoints",90).put("actualProviderCalls",false).toString())
 }
 @Test fun deleteAccount(){
  authenticate(false);click("설정");click("동의·계정 관리");wait("저장된 설정을 조회했습니다.");tag("accountDeleteOpen");input("accountDeletePassword",password);input("accountDeleteConfirmation","계정 삭제");tag("accountDeleteConfirm");wait("계정을 삭제했습니다.");ui.onNodeWithTag("email").assertExists()
 }
}
