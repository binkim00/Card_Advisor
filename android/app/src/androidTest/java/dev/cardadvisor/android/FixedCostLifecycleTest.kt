package dev.cardadvisor.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.net.*
import java.time.*
import java.util.UUID
import java.util.concurrent.*

@RunWith(AndroidJUnit4::class)
class FixedCostLifecycleTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun waitFor(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun click(text:String){compose.waitUntil(15000){compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun fill(tag:String,value:String){compose.waitUntil(15000){compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
 private fun dir():File{
  val runId=InstrumentationRegistry.getArguments().getString("runId")?:UUID.randomUUID().toString();require(Regex("[a-f0-9-]{36}").matches(runId))
  return File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"lifecycle/$runId").apply{mkdirs()}
 }
 private fun failure(dir:File,e:Throwable){File(dir,"failure.txt").appendText(e.stackTraceToString()+"\n")}

 @Test fun closingDuringInitialReadDoesNotPublishToRemovedScreen(){
  val evidence=dir();val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
  val received=CountDownLatch(1);val release=CountDownLatch(1);val sent=CountDownLatch(1)
  val executor=Executors.newSingleThreadExecutor()
  executor.submit {
   try{server.accept().use{socket->
    val input=socket.getInputStream().bufferedReader();while(!input.readLine().isNullOrEmpty()){}
    received.countDown();check(release.await(10,TimeUnit.SECONDS))
    val body="{\"month\":\"2026-10\",\"mode\":\"UNSET\",\"manualRemainingWon\":null,\"scheduledRemainingWon\":0,\"appliedRemainingWon\":null,\"items\":[]}".toByteArray()
    socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
   }}finally{sent.countDown()}
  }
  FixedCostLifecycleActivity.client=ApiClient("http://127.0.0.1:${server.localPort}")
  FixedCostLifecycleActivity.callbacksAfterRemoval.set(0);FixedCostLifecycleActivity.busyCallbacks.set(0)
  var scenario:ActivityScenario<FixedCostLifecycleActivity>?=null
  try{
   scenario=ActivityScenario.launch(FixedCostLifecycleActivity::class.java)
   Assert.assertTrue("Initial read never started",received.await(10,TimeUnit.SECONDS))
   scenario.onActivity{it.showEditor=false};waitFor("검사용 다른 화면")
   release.countDown();Assert.assertTrue(sent.await(10,TimeUnit.SECONDS))
   // Let the cancelled blocking HTTP request unwind through withContext and finally.
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   // Observe a bounded quiet window after releasing the loopback response.
   val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1)
   while(System.nanoTime()<deadline){
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    Assert.assertEquals("Removed screen published busy state from coroutine finally",0,FixedCostLifecycleActivity.callbacksAfterRemoval.get())
    Thread.sleep(20)
   }
   val late=FixedCostLifecycleActivity.callbacksAfterRemoval.get()
   File(evidence,"cancellation.json").writeText(JSONObject().put("lateCallbacks",late).put("controlledDelayedResponse",true).toString())
   Assert.assertEquals("Removed screen published busy state from coroutine finally",0,late)
   scenario.close();scenario=null
  }catch(e:Throwable){failure(evidence,e);throw e}
  finally{release.countDown();try{scenario?.close()}catch(e:Throwable){failure(evidence,e);throw e}finally{server.close();executor.shutdownNow();FixedCostLifecycleActivity.client=null}}
 }

 @Test fun realApiFirstEntryEditNavigateRecreateAndFinish(){
  val evidence=dir();val runId=evidence.name;val api=ApiClient();val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
  runBlocking{
   val auth=JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","lifecycle-$runId@example.test").put("password","UiTest-"+UUID.randomUUID())))
   api.token=auth.getString("accessToken");api.request("/v1/demo/setup","POST")
  }
  FixedCostLifecycleActivity.client=api;FixedCostLifecycleActivity.callbacksAfterRemoval.set(0)
  var scenario:ActivityScenario<FixedCostLifecycleActivity>?=null
  var finished=0
  try{
   repeat(12){iteration->
    File(evidence,"steps.txt").appendText("$iteration launch\n")
    scenario=ActivityScenario.launch(FixedCostLifecycleActivity::class.java)
    waitFor("$month 고정비를 불러왔습니다.");click("고정비 일정 추가")
    fill("fixedName","안정성 확인");fill("fixedAmount","30000");fill("fixedDay","31")
    if(iteration==0){click("고정비 일정 저장");waitFor("고정비 변경을 저장하고 다시 불러왔습니다.")}
    else {click("일정 편집 닫기");click("안정성 확인 일정 수정");fill("fixedAmount","31000");click("일정 편집 닫기")}
    scenario!!.onActivity{it.showEditor=false};waitFor("검사용 다른 화면")
    scenario!!.onActivity{it.showEditor=true};waitFor("$month 고정비를 불러왔습니다.")
    click("안정성 확인 거래 연결");waitFor("해당 월의 구매 기록이 없습니다. 가계부에서 실제 사용 내역을 먼저 기록해 주세요.");click("거래 선택 닫기")
    if(iteration%3==0){scenario!!.recreate();waitFor("$month 고정비를 불러왔습니다.")}
    click("안정성 확인 일정 수정");fill("fixedAmount","32000")
    File(evidence,"steps.txt").appendText("$iteration closing with form\n")
    scenario!!.close();scenario=null;finished++
   }
   runBlocking{val v=JSONObject(api.request("/v1/fixed-costs?month=$month"));Assert.assertEquals(1,v.getJSONArray("items").length());Assert.assertEquals(30000L,v.getLong("scheduledRemainingWon"))}
   File(evidence,"navigation.json").writeText(JSONObject().put("completedCycles",finished).put("recreations",4).put("realApi",true).toString())
  }catch(e:Throwable){failure(evidence,e);throw e}
  finally{try{scenario?.close()}catch(e:Throwable){failure(evidence,e);throw e}finally{FixedCostLifecycleActivity.client=null}}
 }
}
