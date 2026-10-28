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
class MonthlyStateTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun fill(tag:String,value:String){compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
 private fun capture(dir:File,name:String){File(dir,name).outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun editReloadAndResolveConcurrentChange(){
  val runId=InstrumentationRegistry.getArguments().getString("runId")?:UUID.randomUUID().toString()
  require(Regex("[a-f0-9-]{36}").matches(runId))
  val email="ui-$runId@example.test";val password="UiTest-"+UUID.randomUUID()
  val dir=File(compose.activity.getExternalFilesDir(null),"e2e/$runId").apply{mkdirs()}
  val today=LocalDate.now(ZoneId.of("Asia/Seoul"));val month=YearMonth.from(today).toString()
  fill("email",email);fill("password",password);click("가입");waitFor("가입했습니다. 내 카드를 등록해 시작해 보세요.")
  click("가상 데이터 준비");waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
  click("월별 실적·자격 수정");waitFor("저장된 월별 값을 불러왔습니다.")
  click("[가상] B 카드 월별 수정")
  fill("stateBaseline","310000")
  fill("qualificationPeriod",month)
  compose.onNodeWithText("월별 값 저장").assertIsNotEnabled()
  fill("qualificationPeriod",YearMonth.from(today).minusMonths(1).toString())
  fill("baselineDate",today.plusDays(1).toString())
  compose.onNodeWithText("월별 값 저장").assertIsNotEnabled()
  fill("baselineDate",today.toString());click("자격 미충족");click("월별 값 저장")
  waitFor("월별 값을 저장하고 다시 조회했습니다.")
  compose.onNodeWithTag("stateBaseline").assertTextContains("310000")
  compose.onNodeWithText("자격 미충족").assertIsSelected()
  capture(dir,"01-saved.png")
  // A second real API session models another device editing the same saved version.
  val second=ApiClient()
  runBlocking{second.token=JSONObject(second.request("/v1/auth/login","POST",JSONObject().put("email",email).put("password",password))).getString("accessToken")}
  var cardId=""
  runBlocking {
   val rows=JSONArray(second.request("/v1/cards/performance?month=$month"))
   val c=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("name")=="[가상] B 카드"}
   cardId=c.getString("id")
   Assert.assertEquals("USER_INPUT",c.getString("untrackedSource"))
   Assert.assertEquals(today.toString(),c.getString("untrackedAsOf"))
   Assert.assertEquals(310000L,c.getLong("performanceWon"));Assert.assertFalse(c.getBoolean("currentQualified"))
   second.request("/v1/cards/$cardId/months/$month","PUT",JSONObject()
    .put("untrackedPerformanceWon",330000).put("currentQualified",true).put("remainingCapWon",10000).put("version",c.getLong("stateVersion"))
    .put("untrackedSource","USER_ESTIMATE").put("untrackedAsOf",today.toString()).put("qualificationSource","USER_INPUT").put("qualificationAsOf",today.toString()).put("qualificationPeriod",c.getString("qualificationPeriod")))
  }
  fill("stateBaseline","320000");click("월별 값 저장")
  waitFor("다른 곳에서 값이 변경되었습니다. 내 입력은 유지했습니다. 최신 값을 확인해 주세요.")
  compose.onNodeWithTag("stateBaseline").assertTextContains("320000")
  compose.onNodeWithText("월별 값 저장").assertIsNotEnabled()
  capture(dir,"02-conflict-draft-retained.png")
  click("최신 값 불러오기 (내 입력 초기화)");waitFor("저장된 월별 값을 불러왔습니다.")
  compose.onNodeWithTag("stateBaseline").assertTextContains("330000");compose.onNodeWithText("자격 충족").assertIsSelected()
  fill("stateBaseline","340000");click("자격 미충족");click("월별 값 저장");waitFor("월별 값을 저장하고 다시 조회했습니다.")
  // Explicit unknown remains null, then restore a known value using the newest version.
  click("실적 미확인");click("자격 미확인");click("월별 값 저장")
  waitFor("월별 값을 저장하고 다시 조회했습니다.")
  runBlocking{val rows=JSONArray(second.request("/v1/cards/performance?month=$month"));val c=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("id")==cardId};Assert.assertTrue(c.isNull("performanceWon"));Assert.assertTrue(c.isNull("currentQualified"));Assert.assertEquals("UNKNOWN",c.getString("untrackedSource"))}
  click("실적 금액 입력");fill("stateBaseline","340000");click("자격 미충족");click("월별 값 저장");waitFor("월별 값을 저장하고 다시 조회했습니다.")
  // A different month begins with unknown data, without carrying this month's values.
  fill("stateMonth",YearMonth.from(today).minusMonths(1).toString());click("저장된 값 다시 조회 (입력 초기화)");waitFor("저장된 월별 값을 불러왔습니다.")
  compose.onNodeWithText("실적 미확인").assertIsSelected();compose.onNodeWithText("자격 미확인").assertIsSelected()
  fill("stateMonth",month);click("저장된 값 다시 조회 (입력 초기화)")
  waitFor("저장된 월별 값을 불러왔습니다.")
  compose.onNodeWithTag("stateBaseline").assertTextContains("340000")
  capture(dir,"03-reloaded.png")
  click("나가기");waitFor("로그아웃했습니다.");fill("password",password);click("로그인");waitFor("로그인했습니다.")
  // The current tab is retained through login; it fetches persisted data again.
  waitFor("저장된 월별 값을 불러왔습니다.");click("[가상] B 카드 월별 수정")
  compose.onNodeWithTag("stateBaseline").assertTextContains("340000");compose.onNodeWithText("자격 미충족").assertIsSelected()
  File(dir,"evidence.json").writeText(JSONObject().put("runId",runId).put("email",email).put("month",month).put("cardId",cardId).put("syntheticOnly",true).put("status","UI_PASSED_PENDING_DATABASE_CHECK").put("checks","save,readback,metadata,invalidPeriod,futureDate,concurrentConflict,draftRetained,reloadThenSave,unknownNull,monthIsolation,relogin").toString(2))
 }
}
