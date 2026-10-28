package dev.cardadvisor.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FixedCostFlowTest {
 private var evidenceDir:File?=null
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){evidenceDir?.resolve("steps.txt")?.appendText("click: $text\n");compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun fill(tag:String,value:String){evidenceDir?.resolve("steps.txt")?.appendText("fill: $tag\n");compose.waitUntil(15000){compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
 private fun tap(tag:String){compose.waitUntil(15000){compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag(tag).performScrollTo().performClick()}
 private fun saved(){waitFor("고정비 변경을 저장하고 다시 불러왔습니다.")}
 private fun capture(dir:File,name:String){File(dir,name).outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}

 private fun openLedgerDetails(){
  compose.waitUntil(15000){compose.onAllNodes(hasText("새로고침") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
  if(compose.onAllNodesWithText("+ 수동 구매 기록").fetchSemanticsNodes().isNotEmpty())click("+ 수동 구매 기록")
  if(compose.onAllNodesWithText("예산·실적 상세  +").fetchSemanticsNodes().isNotEmpty())click("예산·실적 상세  +")
  val rows=compose.onAllNodes(hasTestTagPrefix("transaction_"))
  if(rows.fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("분류·예산 포함 수정").fetchSemanticsNodes().isEmpty())rows.onFirst().performScrollTo().performClick()
 }
 private fun hasTestTagPrefix(prefix:String)=SemanticsMatcher("transaction row"){it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}.startsWith(prefix)}
 @Test fun recurringPaymentMatchingRefundConflictAndMonthIsolation(){
  val runId=InstrumentationRegistry.getArguments().getString("runId")?:UUID.randomUUID().toString();require(Regex("[a-f0-9-]{36}").matches(runId))
  val email="ui-$runId@example.test";val password="UiTest-"+UUID.randomUUID()
  val current=YearMonth.now(ZoneId.of("Asia/Seoul"));val month=current.toString();val next=current.plusMonths(1).toString()
  val dir=File(compose.activity.getExternalFilesDir(null),"e2e/$runId").apply{mkdirs()}
  evidenceDir=dir
  try{
  fill("email",email);fill("password",password);click("가입");waitFor("가입했습니다. 내 카드를 등록해 시작해 보세요.")
  click("가상 데이터 준비");waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
  click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.");click("고정비 일정 추가")
  fill("fixedName","테스트 월세");fill("fixedAmount","50000");fill("fixedDay","31");tap("fixedCategory_HOUSING");click("고정비 일정 저장");saved()
  compose.onNodeWithText("현재 적용 미반영 고정비 30,000원").assertExists();compose.onNodeWithText("$month 반복 일정 잔여 50,000원").assertExists()
  click("반복 일정 기준으로 전환");saved();compose.onNodeWithText("현재 적용 미반영 고정비 50,000원").assertExists()
  click("가계부");openLedgerDetails();click("[가상] B 카드 사용");click("구매 기록 추가");waitFor("수동 구매 기록을 추가했습니다.");openLedgerDetails()
  click("추천");click("금액 확인 후 카드 비교");waitFor("고정비 고려 잔액 -30,000원")
  click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.")
  click("테스트 월세 거래 연결");click("이 거래를 연결");saved();compose.onNodeWithText("현재 적용 미반영 고정비 0원").assertExists()
  compose.onNodeWithTag("fixedApplied").performScrollTo();capture(dir,"01-linked-no-double-count.png")
  val second=ApiClient();var id="";var tx=""
  runBlocking{
   second.token=JSONObject(second.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",password))).getString("accessToken")
   id=JSONObject(second.request("/v1/fixed-costs?month=$month")).getJSONArray("items").getJSONObject(0).getString("id")
   tx=JSONArray(second.request("/v1/transactions?month=$month")).getJSONObject(0).getString("id")
  }
  click("추천");compose.onNodeWithText("고정비 고려 잔액 -30,000원").assertDoesNotExist();click("금액 확인 후 카드 비교");waitFor("고정비 고려 잔액 20,000원")
  click("가계부");openLedgerDetails();compose.onNodeWithText("미반영 고정비 제외 여유 70,000원").assertExists()
  click("부분 취소 반영");compose.onNodeWithTag("cancelAmount").performTextInput("20000");compose.onNodeWithText("취소 금액 반영").performClick();waitFor("부분 취소 20,000원을 반영했습니다.")
  compose.onNodeWithText("이번 달 남은 예산 90,000원").assertExists();compose.onNodeWithText("미반영 고정비 제외 여유 70,000원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 300,000원").assertExists();compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,700원").assertExists()
  click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.");compose.onNodeWithText("현재 적용 미반영 고정비 20,000원").assertExists()
  tap("fixedBalance_$id");capture(dir,"02-partial-cancel-reservation.png")
  click("테스트 월세 일정 수정");fill("fixedAmount","60000");click("고정비 일정 저장");saved();compose.onNodeWithText("현재 적용 미반영 고정비 30,000원").assertExists()
  tap("fixedUnlink_$tx");saved();compose.onNodeWithText("현재 적용 미반영 고정비 60,000원").assertExists()
  click("테스트 월세 거래 연결");click("이 거래를 연결");saved();compose.onNodeWithText("현재 적용 미반영 고정비 30,000원").assertExists()
  click("테스트 월세 일정 수정");fill("fixedAmount","65000")
  runBlocking{
   val latest=JSONObject(second.request("/v1/fixed-costs?month=$month")).getJSONArray("items").getJSONObject(0)
   second.request("/v1/fixed-costs","POST",JSONObject().put("id",id).put("version",latest.getLong("version")).put("name","테스트 월세").put("amountWon",70000).put("category","HOUSING").put("dayOfMonth",31).put("effectiveMonth",month).put("active",true))
  }
  click("고정비 일정 저장");waitFor("다른 곳에서 고정비 또는 예산이 변경되었습니다. 입력은 유지했습니다. 최신 값을 불러와 주세요.")
  compose.onNodeWithTag("fixedAmount").assertTextContains("65000");compose.onNodeWithText("고정비 일정 저장").assertIsNotEnabled();compose.onNodeWithTag("fixedNotice").performScrollTo();capture(dir,"03-conflict.png")
  click("최신 고정비 불러오기 (입력 초기화)");waitFor("$month 고정비를 불러왔습니다.");compose.onNodeWithText("현재 적용 미반영 고정비 40,000원").assertExists()
  click("테스트 월세 일정 수정");fill("fixedAmount","60000");click("고정비 일정 저장");saved()
  fill("fixedMonth",next);click("최신 고정비 불러오기 (입력 초기화)");waitFor("$next 고정비를 불러왔습니다.");compose.onNodeWithText("$next 반복 일정 잔여 60,000원").assertExists()
  click("테스트 월세 일정 수정");fill("fixedAmount","80000");click("고정비 일정 저장");saved()
  click("테스트 월세 일정 수정");tap("fixedActive");click("고정비 일정 저장");saved();compose.onNodeWithText("$next 반복 일정 잔여 0원").assertExists()
  click("테스트 월세 일정 수정");tap("fixedActive");click("고정비 일정 저장");saved();compose.onNodeWithText("$next 반복 일정 잔여 80,000원").assertExists()
  click("월별 예산 설정 열기");waitFor("$next 저장된 예산이 없습니다. 금액을 입력해 새로 저장해 주세요.")
  fill("budgetTotal","200000");fill("budgetFixed","0");click("월 예산 저장");waitFor("$next 예산을 저장하고 다시 불러왔습니다.")
  click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.");compose.onNodeWithText("현재 적용 미반영 고정비 30,000원").assertExists()
  fill("fixedMonth",next);click("최신 고정비 불러오기 (입력 초기화)");waitFor("$next 고정비를 불러왔습니다.");click("반복 일정 기준으로 전환");saved();compose.onNodeWithText("현재 적용 미반영 고정비 80,000원").assertExists()
  click("나가기");waitFor("로그아웃했습니다.");fill("password",password);click("로그인");waitFor("로그인했습니다.");click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.")
  compose.onNodeWithText("현재 적용 미반영 고정비 30,000원").assertExists();compose.onNodeWithText("예상 60,000원 · 반영 30,000원 · 잔여 30,000원").performScrollTo();capture(dir,"04-persisted-current-month.png")
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 40,000원");compose.onNodeWithText("고정비 고려 잔액 10,000원").assertExists()
  runBlocking{val v=JSONObject(second.request("/v1/fixed-costs?month=$month"));Assert.assertEquals(30000L,v.getLong("appliedRemainingWon"));Assert.assertEquals(10L,v.getJSONArray("items").getJSONObject(0).getLong("version"))}
  File(dir,"evidence.json").writeText(JSONObject().put("runId",runId).put("status","UI_PASSED_PENDING_DATABASE_CHECK").put("syntheticOnly",true).put("checks","createSchedule,sourceSwitch,matching,partialCancel,unlink,relink,edit,conflict,nextMonth,stop,resume,budgetMonthHandoff,relogin,recommendation").toString(2))
  }catch(e:Throwable){File(dir,"failure.txt").writeText(e.stackTraceToString());throw e}
 }
}
