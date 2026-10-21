package dev.cardadvisor.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONException
import org.json.JSONObject
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DebugDiagnosticsTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){compose.onNodeWithText(text).performScrollTo().performClick()}
 private fun directory()=requireNotNull(Diagnostics.sessionDirectory())
 private fun eventNames()=File(directory(),"events.jsonl").readLines().map{JSONObject(it).getString("event")}

 @Test fun privacyRotationAndFirstFailure(){
  val marker="PRIVATE_CANARY_"+UUID.randomUUID().toString().replace("-","")
  val first=JSONException(marker).apply{
   initCause(IllegalArgumentException(marker));addSuppressed(IllegalStateException(marker))
   stackTrace=arrayOf(StackTraceElement("java.lang.String",marker,marker,42),StackTraceElement(marker,"fake",marker,42))
  }
  val oldName=Thread.currentThread().name
  try{
   Thread.currentThread().name=marker
   Diagnostics.failure(DiagnosticSite.TEST_OBSERVATION,first)
   val pinned=File(directory(),"first-exception.json").readBytes()
   repeat(1000){Diagnostics.event(DiagnosticEvent.FIXED_LOAD_START)}
   Diagnostics.failure(DiagnosticSite.TEST_OBSERVATION,IllegalStateException(marker))
   Assert.assertTrue("Earliest exception was overwritten",pinned.contentEquals(File(directory(),"first-exception.json").readBytes()))
   val files=directory().listFiles()!!.filter{it.isFile}
   Assert.assertTrue("Expected bounded rotation",File(directory(),"events.previous.jsonl").exists())
   Assert.assertTrue("Diagnostic output exceeded bound",files.all{it.length()<=DiagnosticStore.MAX_EVENT_BYTES})
   Assert.assertTrue("Private canary leaked",files.none{it.readText().contains(marker)})
   Diagnostics.requestTrace(1,HttpTraceRoute.FINANCE,HttpTracePhase.BODY_READ,12,200)
   val trace=Diagnostics.requestTraceSnapshot()
   Assert.assertFalse("Private canary leaked to request trace",trace.contains(marker))
   val http=trace.lineSequence().filter{it.isNotBlank()}.map{JSONObject(it)}.last{it.optString("event")=="HTTP"}
   Assert.assertEquals("FINANCE",http.getString("route"));Assert.assertEquals("BODY_READ",http.getString("phase"))
   Assert.assertFalse("Raw HTTP fields present",http.has("url")||http.has("body")||http.has("headers"))
   Assert.assertTrue("Request trace is not bounded",trace.lines().filter{it.isNotBlank()}.size<=1024)
   val record=JSONObject(String(pinned));val causes=record.getJSONArray("exceptions")
   Assert.assertEquals("org.json.JSONException",causes.getJSONObject(0).getString("type"))
   Assert.assertEquals(3,causes.length())
   Assert.assertFalse("Caught error marked fatal",File(directory(),"first-uncaught.json").exists())
   val retentionRoot=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"diagnostics-test-${UUID.randomUUID()}")
   val terminal=DiagnosticStore(retentionRoot)
   terminal.failure(DiagnosticSite.TEST_OBSERVATION,IllegalStateException("synthetic"),true)
   repeat(9){Thread.sleep(2);DiagnosticStore(retentionRoot).event(DiagnosticEvent.PROCESS_START)}
   Assert.assertTrue("Normal restarts evicted terminal failure",File(terminal.directory,"first-uncaught.json").exists())
   Assert.assertTrue(retentionRoot.listFiles()!!.size<=DiagnosticStore.MAX_SESSIONS+DiagnosticStore.MAX_FATAL_SESSIONS)
   // Only this test's newly created cache directory contains these synthetic files.
   retentionRoot.deleteRecursively()
  }finally{Thread.currentThread().name=oldName}
 }

 @Test fun fixedEntryReadFailureRecoveryAndClose(){
  val id=requireNotNull(InstrumentationRegistry.getArguments().getString("runId"));require(Regex("[a-f0-9-]{36}").matches(id))
  val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
  var scenario:ActivityScenario<MainActivity>?=null;var primary:Throwable?=null
  try{
   scenario=ActivityScenario.launch(MainActivity::class.java)
   compose.onNodeWithTag("email").performTextReplacement("diag-$id@example.test")
   compose.onNodeWithTag("password").performTextReplacement("UiTest-"+UUID.randomUUID())
   click("가입");waitFor("가입했습니다. 가상 데이터로 계산을 시작할 수 있습니다.")
   click("가상 데이터 준비");waitFor("가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다.")
   click("반복 고정비 관리");waitFor("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.")
   val pinned=File(directory(),"first-exception.json").readBytes()
   click("최신 고정비 불러오기 (입력 초기화)");waitFor("$month 고정비를 불러왔습니다.")
   click("설정");click("반복 고정비 관리");waitFor("$month 고정비를 불러왔습니다.")
   scenario.close();scenario=null
   val names=eventNames()
   for(name in listOf("ACTIVITY_CREATED","ACTIVITY_RESUMED","FIXED_ENTER","FIXED_LOAD_START","EXCEPTION","FIXED_LOAD_OK","FIXED_EXIT","ACTIVITY_DESTROYED"))Assert.assertTrue("Missing lifecycle event",names.contains(name))
   Assert.assertTrue("Recovery/close replaced first exception",pinned.contentEquals(File(directory(),"first-exception.json").readBytes()))
   Assert.assertFalse(File(directory(),"first-uncaught.json").exists())
  }catch(t:Throwable){primary=t;Diagnostics.failure(DiagnosticSite.TEST_OBSERVATION,t);throw t}
  finally{try{scenario?.close()}catch(t:Throwable){Diagnostics.failure(DiagnosticSite.TEST_OBSERVATION,t);if(primary==null)throw t else primary.addSuppressed(t)}}
 }

 @Test fun fatalProbe(){
  // A controlled terminal exception tests persistence/handler chaining, not SlotTable reproduction.
  Diagnostics.failure(DiagnosticSite.TEST_OBSERVATION,JSONException("synthetic predecessor"))
  Thread{throw ArrayIndexOutOfBoundsException("synthetic terminal probe")}.apply{start();join(10000)}
  Assert.fail("Expected the normal Android fatal handler to terminate this process")
 }

 @Test fun restartAndNormalClose(){
  ActivityScenario.launch(MainActivity::class.java).use{it.close()}
  Assert.assertTrue(eventNames().contains("ACTIVITY_DESTROYED"))
 }
}
