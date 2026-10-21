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
class ClassificationFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun fill(tag:String,value:String){compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
 private fun save(){compose.onNodeWithText("분류 저장").assertIsEnabled().performClick();compose.waitUntil(15000){compose.onAllNodesWithText("거래 분류 수정").fetchSemanticsNodes().isEmpty()}}
 private fun category(code:String){compose.onNodeWithTag("classify_$code").performScrollTo().performClick()}
 private fun toggleBudget(){compose.onNodeWithTag("budgetIncluded").performScrollTo().performClick()}
 private fun unchanged(){
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 300,000원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 연결 거래 혜택 사용액 300원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,700원").assertExists()
 }
 private fun capture(dir:File,name:String,dialog:Boolean=false){File(dir,name).outputStream().use{(if(dialog)compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}

 private fun openLedgerDetails(){
  compose.waitUntil(15000){compose.onAllNodes(hasText("새로고침") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
  if(compose.onAllNodesWithText("+ 수동 구매 기록").fetchSemanticsNodes().isNotEmpty())click("+ 수동 구매 기록")
  if(compose.onAllNodesWithText("예산·실적 상세  +").fetchSemanticsNodes().isNotEmpty())click("예산·실적 상세  +")
  val rows=compose.onAllNodes(hasTestTagPrefix("transaction_"))
  if(rows.fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("분류·예산 포함 수정").fetchSemanticsNodes().isEmpty())rows.onFirst().performScrollTo().performClick()
 }
 private fun hasTestTagPrefix(prefix:String)=SemanticsMatcher("transaction row"){it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}.startsWith(prefix)}
 @Test fun editCategoryInclusionAndResolveConflict(){
  val runId=InstrumentationRegistry.getArguments().getString("runId")?:UUID.randomUUID().toString();require(Regex("[a-f0-9-]{36}").matches(runId))
  val email="ui-$runId@example.test";val password="UiTest-"+UUID.randomUUID()
  val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
  val dir=File(compose.activity.getExternalFilesDir(null),"e2e/$runId").apply{mkdirs()}
  fill("email",email);fill("password",password);click("가입");waitFor("가입했습니다. 가상 데이터로 계산을 시작할 수 있습니다.")
  click("가상 데이터 준비");waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
  click("가계부");openLedgerDetails();click("[가상] B 카드 사용");click("구매 기록 추가");waitFor("수동 구매 기록을 추가했습니다.");openLedgerDetails()
  click("부분 취소 반영");compose.onNodeWithTag("cancelAmount").performTextInput("20000");compose.onNodeWithText("취소 금액 반영").performClick();waitFor("부분 취소 20,000원을 반영했습니다.")
  unchanged();compose.onNodeWithText("이번 달 예산 지출 30,000원").assertExists()
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 40,000원");compose.onNodeWithText("쇼핑 예산 -10,000원").assertExists()
  click("가계부");openLedgerDetails();click("분류·예산 포함 수정");category("FOOD");save()
  waitFor("분류: 식비 · 예산 포함");unchanged();compose.onNodeWithText("이번 달 남은 예산 90,000원").assertExists()
  compose.onNodeWithText("쇼핑 지출 0원 · 남은 예산 70,000원").assertExists()
  compose.onNodeWithText("식비 지출 30,000원 · 예산 미설정").performScrollTo().assertIsDisplayed();capture(dir,"01-category-moved.png")
  click("추천");compose.onNodeWithText("쇼핑 예산 -10,000원").assertDoesNotExist();click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 40,000원");compose.onNodeWithText("쇼핑 예산 20,000원").assertExists()
  click("가계부");openLedgerDetails();click("분류·예산 포함 수정");toggleBudget();save();waitFor("분류: 식비 · 예산 제외");unchanged()
  compose.onNodeWithText("이번 달 예산 지출 0원").assertExists();compose.onNodeWithText("이번 달 남은 예산 120,000원").performScrollTo().assertIsDisplayed();capture(dir,"02-excluded-budget.png")
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 70,000원");compose.onNodeWithText("쇼핑 예산 20,000원").assertExists()
  click("가계부");openLedgerDetails();click("분류·예산 포함 수정");category("OTHER")
  val second=ApiClient();var id=""
  runBlocking{
   second.token=JSONObject(second.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",password))).getString("accessToken")
   val entry=JSONArray(second.request("/v1/transactions?month=$month")).getJSONObject(0);id=entry.getString("id")
   second.request("/v1/transactions/$id/classification","PUT",JSONObject().put("category","SHOPPING").put("included",true).put("version",entry.getLong("classification_version")))
  }
  compose.onNodeWithText("분류 저장").performClick();waitFor("다른 곳에서 분류가 변경되었습니다. 내 입력은 유지했습니다. 최신 값을 확인해 주세요.")
  compose.onNodeWithTag("classify_OTHER").assertIsSelected();compose.onNodeWithTag("budgetIncluded").assertIsOff();compose.onNodeWithText("분류 저장").assertIsNotEnabled()
  capture(dir,"03-conflict-draft.png",true)
  click("최신 분류 불러오기 (입력 초기화)");waitFor("최신 분류를 불러왔습니다. 다시 수정할 수 있습니다.")
  compose.onNodeWithTag("classify_SHOPPING").assertIsSelected();compose.onNodeWithTag("budgetIncluded").assertIsOn()
  category("FOOD");toggleBudget();save();waitFor("분류: 식비 · 예산 제외");unchanged()
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 70,000원")
  click("나가기");waitFor("로그아웃했습니다.");fill("password",password);click("로그인");waitFor("로그인했습니다.")
  click("가계부");openLedgerDetails();click("새로고침");waitFor("내역을 조회했습니다.");click("[가상] B 카드 사용");waitFor("분류: 식비 · 예산 제외");unchanged()
  compose.onNodeWithText("이번 달 남은 예산 120,000원").performScrollTo().assertIsDisplayed();capture(dir,"04-persisted.png")
  runBlocking{val e=JSONObject(second.request("/v1/transactions/$id"));Assert.assertEquals(5L,e.getLong("classification_version"));Assert.assertFalse(e.getBoolean("included"));Assert.assertEquals(30000L,e.getLong("net_won"))}
  File(dir,"evidence.json").writeText(JSONObject().put("runId",runId).put("email",email).put("status","UI_PASSED_PENDING_DATABASE_CHECK").put("syntheticOnly",true).put("checks","partialCancel,categoryBudget,excludeBudget,cardCalculationUnchanged,recommendationInvalidation,conflictDraft,reload,relogin").toString(2))
 }
}
