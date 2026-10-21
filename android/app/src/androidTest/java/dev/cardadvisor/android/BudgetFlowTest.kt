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
class BudgetFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun fill(tag:String,value:String){compose.waitUntil(15000){compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
 private fun value(tag:String,value:String){compose.onNodeWithTag(tag).assertTextContains(value)}
 private fun enable(code:String){compose.onNodeWithTag("budgetEnabled_$code").performScrollTo().performClick()}
 private fun capture(dir:File,name:String){File(dir,name).outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}

 private fun openLedgerDetails(){
  compose.waitUntil(15000){compose.onAllNodes(hasText("새로고침") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
  if(compose.onAllNodesWithText("+ 수동 구매 기록").fetchSemanticsNodes().isNotEmpty())click("+ 수동 구매 기록")
  if(compose.onAllNodesWithText("예산·실적 상세  +").fetchSemanticsNodes().isNotEmpty())click("예산·실적 상세  +")
  val rows=compose.onAllNodes(hasTestTagPrefix("transaction_"))
  if(rows.fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("분류·예산 포함 수정").fetchSemanticsNodes().isEmpty())rows.onFirst().performScrollTo().performClick()
 }
 private fun hasTestTagPrefix(prefix:String)=SemanticsMatcher("transaction row"){it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}.startsWith(prefix)}
 @Test fun savedBudgetCategoriesConflictAndMonthIsolation(){
  val runId=InstrumentationRegistry.getArguments().getString("runId")?:UUID.randomUUID().toString();require(Regex("[a-f0-9-]{36}").matches(runId))
  val email="ui-$runId@example.test";val password="UiTest-"+UUID.randomUUID()
  val current=YearMonth.now(ZoneId.of("Asia/Seoul"));val month=current.toString();val next=current.plusMonths(1).toString()
  val dir=File(compose.activity.getExternalFilesDir(null),"e2e/$runId").apply{mkdirs()}
  fill("email",email);fill("password",password);click("가입");waitFor("가입했습니다. 가상 데이터로 계산을 시작할 수 있습니다.")
  click("가상 데이터 준비");waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
  click("가계부");openLedgerDetails();click("[가상] B 카드 사용");click("구매 기록 추가");waitFor("수동 구매 기록을 추가했습니다.");openLedgerDetails()
  click("부분 취소 반영");compose.onNodeWithTag("cancelAmount").performTextInput("20000");compose.onNodeWithText("취소 금액 반영").performClick();waitFor("부분 취소 20,000원을 반영했습니다.")
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 40,000원")
  click("설정");click("월별 예산 수정");waitFor("$month 저장된 예산을 불러왔습니다.")
  value("budgetTotal","120000");value("budgetFixed","30000");value("budgetLimit_SHOPPING","70000")
  fill("budgetTotal","200000");fill("budgetFixed","20000");fill("budgetLimit_SHOPPING","90000");enable("FOOD");fill("budgetLimit_FOOD","60000")
  fill("budgetTotal","100000");click("월 예산 저장");waitFor("카테고리 한도의 합이 전체 예산보다 큽니다.")
  fill("budgetTotal","200000");fill("budgetLimit_FOOD","");click("월 예산 저장");waitFor("금액은 빈칸 없이 정수 원으로 입력해 주세요. 미반영 고정비가 없으면 0을 입력해 주세요.")
  fill("budgetLimit_FOOD","60000")
  click("월 예산 저장");waitFor("$month 예산을 저장하고 다시 불러왔습니다.")
  value("budgetTotal","200000");value("budgetLimit_FOOD","60000");compose.onNodeWithTag("budgetTotal").performScrollTo();capture(dir,"01-saved-budget.png")
  click("가계부");openLedgerDetails();compose.onNodeWithText("이번 달 남은 예산 170,000원").assertExists();compose.onNodeWithText("쇼핑 지출 30,000원 · 남은 예산 60,000원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 300,000원").assertExists();compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,700원").assertExists()
  click("추천");compose.onNodeWithText("구매 후 전체 예산 40,000원").assertDoesNotExist();click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 120,000원");compose.onNodeWithText("쇼핑 예산 10,000원").assertExists();compose.onNodeWithText("고정비 고려 잔액 100,000원").assertExists()
  click("설정");click("월별 예산 수정");waitFor("$month 저장된 예산을 불러왔습니다.");fill("budgetTotal","230000")
  val second=ApiClient()
  runBlocking{
   second.token=JSONObject(second.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",password))).getString("accessToken")
   val latest=JSONObject(second.request("/v1/budgets/$month"));latest.put("totalWon",250000).put("fixedRemainingWon",25000).put("categories",JSONObject().put("SHOPPING",100000).put("FOOD",80000))
   second.request("/v1/budgets/$month","PUT",latest)
  }
  click("월 예산 저장");waitFor("다른 곳에서 예산이 변경되었습니다. 내 입력은 유지했습니다. 최신 값을 확인해 주세요.")
  value("budgetTotal","230000");value("budgetLimit_FOOD","60000");compose.onNodeWithText("월 예산 저장").assertIsNotEnabled()
  compose.onNodeWithTag("budgetNotice").performScrollTo();capture(dir,"02-conflict-draft.png")
  click("저장된 예산 불러오기 (입력 초기화)");waitFor("$month 저장된 예산을 불러왔습니다.")
  value("budgetTotal","250000");value("budgetFixed","25000");value("budgetLimit_SHOPPING","100000");value("budgetLimit_FOOD","80000")
  fill("budgetTotal","240000");fill("budgetFixed","20000");click("월 예산 저장");waitFor("$month 예산을 저장하고 다시 불러왔습니다.")
  fill("budgetMonth",next);compose.onNodeWithText("월 예산 저장").assertDoesNotExist();click("저장된 예산 불러오기 (입력 초기화)")
  waitFor("$next 저장된 예산이 없습니다. 금액을 입력해 새로 저장해 주세요.")
  compose.onNodeWithTag("budgetEnabled_SHOPPING").assertIsOff();value("budgetTotal","")
  fill("budgetTotal","50000");fill("budgetFixed","0");enable("FOOD");fill("budgetLimit_FOOD","20000");enable("OTHER");fill("budgetLimit_OTHER","0")
  click("월 예산 저장");waitFor("$next 예산을 저장하고 다시 불러왔습니다.");value("budgetLimit_OTHER","0")
  compose.onNodeWithTag("budgetTotal").performScrollTo();capture(dir,"03-next-month.png")
  click("나가기");waitFor("로그아웃했습니다.");fill("password",password);click("로그인");waitFor("로그인했습니다.")
  click("설정");click("월별 예산 수정");waitFor("$month 저장된 예산을 불러왔습니다.")
  value("budgetTotal","240000");value("budgetFixed","20000");value("budgetLimit_SHOPPING","100000");value("budgetLimit_FOOD","80000")
  compose.onNodeWithTag("budgetTotal").performScrollTo();capture(dir,"04-persisted.png")
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 160,000원");compose.onNodeWithText("쇼핑 예산 20,000원").assertExists();compose.onNodeWithText("고정비 고려 잔액 140,000원").assertExists()
  runBlocking{val b=JSONObject(second.request("/v1/budgets/$month"));Assert.assertEquals(4L,b.getLong("version"));val n=JSONObject(second.request("/v1/budgets/$next"));Assert.assertEquals(1L,n.getLong("version"));Assert.assertFalse(n.getJSONObject("categories").has("SHOPPING"))}
  File(dir,"evidence.json").writeText(JSONObject().put("runId",runId).put("email",email).put("status","UI_PASSED_PENDING_DATABASE_CHECK").put("syntheticOnly",true).put("checks","savedValues,categoryAddEdit,netSpending,unchangedCardCalculation,invalidatedRecommendation,conflictDraft,reload,nextMonth,unsetVsZero,relogin").toString(2))
 }
}
