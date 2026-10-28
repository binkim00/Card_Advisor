package dev.cardadvisor.android



import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the actual Activity against the configured real API. No API or repository mocks. */
@RunWith(AndroidJUnit4::class)
class BlueDeviceFlowTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()
 private fun capture(dir: File, name: String) {
  // Only this app's synthetic authenticated composition, not a device screenshot or UI dump.
  compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
   File(dir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
  }
 }
 private fun waitFor(text: String) {
  try{compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }}catch(error:Throwable){recordFailure(error,text);throw error}
 }
 private fun click(text:String){
  try{
   compose.waitUntil(15000){compose.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
  }catch(error:Throwable){
   recordFailure(error,text)
   throw error
  }
 }
 private fun recordFailure(error:Throwable,action:String){
   // Compose test synchronization can surface a caught UI exception without the app's uncaught handler.
   // Persist only types, code locations and a known test action; never throwable messages or UI trees.
   val run=InstrumentationRegistry.getArguments().getString("runId")?:""
   if(Regex("[a-f0-9-]{36}").matches(run))runCatching{
    val chain=org.json.JSONArray();val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable,Boolean>())
    var current:Throwable?=error
    while(current!=null&&chain.length()<8&&seen.add(current)){
     val value=current
     val frames=org.json.JSONArray()
     value.stackTrace.take(45).forEach{frames.put(JSONObject().put("class",it.className).put("method",it.methodName).put("file",it.fileName).put("line",it.lineNumber))}
     chain.put(JSONObject().put("type",value.javaClass.name).put("frames",frames));current=value.cause
    }
    val file=File(compose.activity.getExternalFilesDir(null),"e2e/$run/first-ui-exception.json")
    val state=JSONObject()
    fun count(key:String,matcher:SemanticsMatcher){state.put(key,runCatching{compose.onAllNodes(matcher).fetchSemanticsNodes().size}.getOrDefault(-1))}
    count("demoEnabled",hasText("가상 데이터 준비") and isEnabled())
    count("logout",hasText("나가기"));count("home",hasText("카드 어드바이저"))
    val notices=JSONObject()
    listOf("signup" to "가입했습니다. 내 카드를 등록해 시작해 보세요.","demo" to "가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.","io" to "서버에 연결할 수 없습니다. 로컬 서버와 USB 연결을 확인해 주세요.","expired" to "로그인이 만료되었거나 계정 정보가 맞지 않습니다.","failed" to "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.").forEach{(code,value)->notices.put(code,runCatching{compose.onAllNodesWithText(value).fetchSemanticsNodes().size}.getOrDefault(-1))}
    state.put("notices",notices)
    count("target",hasText(action));count("clickable",hasText(action) and hasClickAction());count("enabled",hasText(action) and hasClickAction() and isEnabled())
    count("logoutEnabled",hasText("나가기") and isEnabled());count("compareEnabled",hasText("카드 비교하기") and isEnabled())
    java.io.File(file.parentFile,"request-trace.jsonl").writeText(Diagnostics.requestTraceSnapshot())
    if(!file.exists())file.writeText(JSONObject().put("action",action).put("causes",chain).put("state",state).toString(2))
   }
 }
 private fun details(){
  // Entering the ledger reloads its data; a disabled row must not be clicked while that request is running.
  val ready=hasTestTagPrefix("transaction_") and isEnabled()
  compose.waitUntil(15000){compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty()}
  compose.onAllNodes(ready).onFirst().performScrollTo().assertIsEnabled().performClick()
 }
 private fun hasTestTagPrefix(prefix:String)=SemanticsMatcher("transaction row"){it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}.startsWith(prefix)}
 private fun stats(open:Boolean){click("예산·실적 상세  "+if(open)"+" else "−")}
 private fun top(title:String){compose.onAllNodesWithText(title).filter(!hasClickAction()).onFirst().performScrollTo()}
 @Test fun signupDemoRecommendationPurchasePartialCancel() {
  val runId=InstrumentationRegistry.getArguments().getString("runId") ?: UUID.randomUUID().toString()
  require(Regex("[a-f0-9-]{36}").matches(runId))
  val email="ui-$runId@example.test"
  val password="UiTest-"+UUID.randomUUID().toString()
  val evidenceDir=File(compose.activity.getExternalFilesDir(null),"e2e/$runId").apply { mkdirs() }
  // Applies only to the current test Activity; no device setting or lock is changed.
  compose.runOnUiThread{compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
  compose.onNodeWithTag("email").performScrollTo().performTextInput(email)
  compose.onNodeWithTag("password").performScrollTo().performTextInput(password)
  click("가입")
  waitFor("가입했습니다. 내 카드를 등록해 시작해 보세요.")
  click("설정");click("가상 데이터로 기능 확인  +")
  click("가상 데이터 준비")
  waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
  if(InstrumentationRegistry.getArguments().getString("stress")=="true")repeat(12){
   click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 70,000원")
   click("가계부");click("새로고침");waitFor("내역을 조회했습니다.");click("홈")
  }
  click("홈");top("카드 어드바이저");capture(evidenceDir,"00-home.png")
  click("추천");click("가상 시연");click("금액 확인 후 카드 비교")
  waitFor("구매 후 전체 예산 70,000원")
  compose.onNodeWithText("쇼핑 예산 20,000원").assertExists()
  compose.onNodeWithText("고정비 고려 잔액 40,000원").assertExists()
  compose.onNodeWithText("[가상] A 카드 · 추천").assertExists()
  compose.onNodeWithText("이번 혜택 1,500원").assertExists()
  top("결제 전 카드 추천");capture(evidenceDir,"01-recommendation.png")
  click("가계부");click("+ 수동 구매 기록")
  compose.onNodeWithText("구매 기록 추가").assertIsNotEnabled()
  click("[가상] B 카드 사용");stats(true)
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 270,000원").assertExists()
  click("구매 기록 추가");waitFor("수동 구매 기록을 추가했습니다.")
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 320,000원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 이번 혜택 자격 충족").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 연결 거래 혜택 사용액 500원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,500원").assertExists()
  stats(false);top("가계부");capture(evidenceDir,"02-purchase.png")
  details();compose.onNodeWithText("남은 지출 50,000원").assertExists()
  click("추천");compose.onNodeWithText("구매 후 전체 예산 70,000원").assertDoesNotExist()
  click("가계부");details();click("부분 취소 반영")
  compose.onNodeWithTag("cancelAmount").performTextInput("50001")
  compose.onNodeWithText("취소 금액 반영").assertIsNotEnabled()
  compose.onNodeWithText("1원 이상, 남은 지출 이하로 입력해 주세요.").assertExists()
  compose.onNodeWithTag("cancelAmount").performTextReplacement("20000")
  compose.onNodeWithText("취소 금액 반영").assertIsEnabled().performClick()
  waitFor("부분 취소 20,000원을 반영했습니다.")
  compose.onNodeWithText("원래 금액 50,000원").assertExists()
  compose.onNodeWithText("남은 지출 30,000원").assertExists()
  details();top("가계부");capture(evidenceDir,"03-partial-cancel.png")
  stats(true)
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 300,000원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 이번 혜택 자격 충족").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 연결 거래 혜택 사용액 300원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,700원").assertExists()
  click("추천");click("금액 확인 후 카드 비교");waitFor("구매 후 전체 예산 40,000원")
  compose.onNodeWithText("쇼핑 예산 -10,000원").assertExists()
  compose.onNodeWithText("고정비 고려 잔액 10,000원").assertExists()
  compose.onNodeWithText("예산 또는 고정비를 고려한 여유 금액을 넘습니다.").assertExists()
  click("설정");click("월별 실적·자격 수정");waitFor("저장된 월별 값을 불러왔습니다.")
  click("[가상] B 카드 월별 수정");click("월별 값 저장");waitFor("월별 값을 저장하고 다시 조회했습니다.")
  click("가계부");stats(true)
  compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,700원").assertExists()
  click("설정");click("나가기");waitFor("로그아웃했습니다.")
  compose.onNodeWithTag("password").performScrollTo().performTextInput(password)
  click("로그인");waitFor("로그인했습니다.")
  click("가계부");click("새로고침");waitFor("내역을 조회했습니다.")
  stats(true);details()
  compose.onNodeWithText("[가상] B 카드 · 다음 달용 실적 300,000원").assertExists()
  compose.onNodeWithText("남은 지출 30,000원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 연결 거래 혜택 사용액 300원").assertExists()
  compose.onNodeWithText("[가상] B 카드 · 혜택 잔여 한도 9,700원").assertExists()
  val taskEvents=Diagnostics.requestTraceSnapshot().lineSequence().filter{it.isNotBlank()}.map{JSONObject(it)}.filter{it.optString("event")=="APP_TASK_END"}.toList()
  org.junit.Assert.assertTrue("UI completion resumed outside Android Main",taskEvents.isNotEmpty()&&taskEvents.all{it.getString("thread")=="MAIN"})
  val resumed=Diagnostics.requestTraceSnapshot().lineSequence().filter{it.isNotBlank()}.map{JSONObject(it)}.filter{it.optString("event")=="HTTP"&&it.optString("phase")=="RESUMED"}.toList()
  org.junit.Assert.assertTrue("UI API continuation resumed outside Android Main",resumed.isNotEmpty()&&resumed.all{it.getString("thread")=="MAIN"})
  File(evidenceDir,"request-trace.jsonl").writeText(Diagnostics.requestTraceSnapshot())
  File(evidenceDir,"evidence.json").writeText(JSONObject()
   .put("runId",runId).put("email",email).put("status","UI_PASSED_PENDING_DATABASE_CHECK")
   .put("purchaseWon",50000).put("cancelWon",20000).put("netWon",30000)
   .put("budgetAfterNextPurchaseWon",40000).put("syntheticOnly",true)
   .put("benefitAfterPurchaseWon",500).put("benefitAfterCancelWon",300).put("remainingCapWon",9700).put("baseRemainingCapWon",10000).put("monthlyEditPreservesBase",true)
   .put("card","B").put("performanceBeforeWon",270000).put("performanceAfterPurchaseWon",320000).put("performanceAfterCancelWon",300000)
   .put("checks","signup,demo,recommendation,purchase,invalidation,overCancelInput,partialCancel,budgetRecalculation,reloginPersistence")
   .toString(2))
 }
}

