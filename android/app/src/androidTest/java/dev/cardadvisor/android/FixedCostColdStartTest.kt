package dev.cardadvisor.android

import android.os.Process
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FixedCostColdStartTest {
 @get:Rule val compose=createEmptyComposeRule()
 private lateinit var dir:File
 private fun step(s:String){File(dir,"steps.txt").appendText("${System.currentTimeMillis()} $s\n")}
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){step("click $text");compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun fill(tag:String,value:String){step("fill $tag");compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
 private fun saveFailure(name:String,t:Throwable){File(dir,name).writeText(t.stackTraceToString())}

 @Test fun freshProcessFirstEntryFailureRecoveryAndFinish(){
  val args=InstrumentationRegistry.getArguments();val id=requireNotNull(args.getString("runId"))
  require(Regex("[a-f0-9-]{36}").matches(id));val mode=requireNotNull(args.getString("fault"))
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  dir=File(instrumentation.targetContext.getExternalFilesDir(null),"coldstart/$id").apply{mkdirs()}
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler{thread,error->saveFailure("uncaught.txt",error);previous?.uncaughtException(thread,error)}
  var activity:ActivityScenario<MainActivity>?=null;var first:Throwable?=null;var authenticated=false
  val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
  try{
   step("launch pid=${Process.myPid()} fault=$mode")
   activity=ActivityScenario.launch(MainActivity::class.java)
   fill("email","cold-$id@example.test");fill("password","UiTest-"+UUID.randomUUID());click("가입")
   waitFor("가입했습니다. 가상 데이터로 계산을 시작할 수 있습니다.")
   authenticated=true
   click("가상 데이터 준비");waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
   click("반복 고정비 관리")
   val probe=ApiClient()
   if(mode=="delay_close"){
    compose.waitUntil(5000){runBlocking{JSONObject(probe.request("/__test/state")).getBoolean("injected")}}
    step("finish during initial response");activity.close();activity=null
    runBlocking{probe.request("/__test/release","POST")}
    Thread.sleep(500);instrumentation.waitForIdleSync()
   }else{
    if(mode!="healthy"){
     val error=when(mode){"http500"->"요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";"disconnect"->"서버에 연결할 수 없습니다. 로컬 서버와 USB 연결을 확인해 주세요.";else->"서버 응답을 확인할 수 없습니다. 최신 고정비를 다시 불러와 주세요."}
     waitFor(error);step("error shown without termination")
     compose.onNodeWithText("고정비 일정 추가").assertDoesNotExist()
     runBlocking{probe.request("/__test/recover","POST")}
     click("설정");click("반복 고정비 관리")
    }
    waitFor("$month 고정비를 불러왔습니다.")
    click("고정비 일정 추가");fill("fixedName","저장하지 않은 초안");fill("fixedAmount","32000");fill("fixedDay","31")
    click("일정 편집 닫기");click("추천");click("금액 확인 후 카드 비교")
    waitFor("구매 후 전체 예산 70,000원")
    click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.")
    step("finish after recovery");activity.close();activity=null
   }
   File(dir,"result.json").writeText(JSONObject().put("runId",id).put("fault",mode).put("pid",Process.myPid()).put("status","UI_PASSED").put("finished",true).toString())
  }catch(t:Throwable){
   first=t;saveFailure("test-observation.txt",t)
   if(authenticated)runCatching{instrumentation.uiAutomation.takeScreenshot().let{bitmap->File(dir,"failure-screen.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
   throw t
  }
  finally{
   try{activity?.close()}catch(t:Throwable){saveFailure("teardown.txt",t);if(first==null)throw t else first.addSuppressed(t)}
   finally{Thread.setDefaultUncaughtExceptionHandler(previous)}
  }
 }
}
