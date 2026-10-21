package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
 @get:org.junit.Rule val ui=androidx.compose.ui.test.junit4.createEmptyComposeRule()
 @Test fun phase()=runBlocking{
  val args=InstrumentationRegistry.getArguments();val run=args.getString("runId")!!;UUID.fromString(run);val phase=args.getString("phase")!!.toInt();val context=InstrumentationRegistry.getInstrumentation().targetContext
  val slot="persist-test-$run";val store=SecureSessionStore(context,slot);val api=ApiClient(sessionStore=store);val password="Synthetic-Persist-$run"
  when(phase){
   1->{store.clear();api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","persist-$run@example.test").put("password",password))));val bytes=File(context.noBackupFilesDir,"$slot.enc").readBytes().toString(Charsets.UTF_8);assertFalse(bytes.contains(api.token!!));assertFalse(bytes.contains(api.refreshToken!!));assertFalse(bytes.contains(password))}
   2->{assertTrue(api.restoreSession());val old=api.token;coroutineScope{listOf(async{api.request("/v1/cards")},async{api.request("/v1/cards")},async{api.request("/v1/cards")}).awaitAll()};assertTrue(old!=api.token);val restored=ApiClient(sessionStore=store);assertTrue(restored.restoreSession());assertTrue(restored.token==api.token);val offline=ApiClient("http://127.0.0.1:1",store);assertTrue(offline.restoreSession());assertTrue(runCatching{offline.request("/v1/cards")}.isFailure);assertNotNull(store.load())}
   3->{assertTrue(api.restoreSession());api.request("/v1/auth/logout","POST");api.clearSession();assertFalse(ApiClient(sessionStore=store).restoreSession());store.save(SavedSession("A".repeat(32),"B".repeat(32)));val f=File(context.noBackupFilesDir,"$slot.enc");val bytes=f.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();f.writeBytes(bytes);assertNull(store.load());api.setSession(JSONObject(api.request("/v1/auth/login","POST",JSONObject().put("email","persist-$run@example.test").put("password",password))))}
   4->{assertTrue(api.restoreSession());val error=runCatching{api.request("/v1/cards")}.exceptionOrNull();assertTrue(error is ApiException && error.status==401);assertNull(store.load());assertFalse(api.sessionActive.value)}
   5->{
    require(android.os.Build.MODEL.contains("sdk"));
val production=SecureSessionStore(context);
production.clear();
api.setSession(JSONObject(api.request("/v1/auth/login","POST",JSONObject().put("email","persist-$run@example.test").put("password",password))));
production.save(SavedSession(api.token!!,api.refreshToken!!))
    fun mark(stage:String){InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("sessionStep",stage)})};
var scenario:androidx.test.core.app.ActivityScenario<MainActivity>?=null
    fun home(){ui.waitUntil(15000){ui.onAllNodesWithText("결제 전 카드 비교").fetchSemanticsNodes().isNotEmpty()}}
    mark("ui_start");
try{scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java);
home();
mark("ui_first_home");
ui.onNodeWithTag("email").assertDoesNotExist();
scenario!!.close();
scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java);
home();
mark("ui_second_home");
val settings=ui.onNodeWithText("설정");ui.waitUntil(15000){runCatching{settings.assertIsEnabled()}.isSuccess};settings.performClick();
val logout=ui.onNodeWithText("나가기");
ui.waitUntil(15000){runCatching{logout.assertIsEnabled()}.isSuccess};
logout.performClick();
mark("ui_logout_clicked");
ui.waitUntil(15000){ui.onAllNodesWithTag("email").fetchSemanticsNodes().isNotEmpty()};
mark("ui_login_form");
assertNull(production.load());
scenario!!.close();
scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java);
ui.waitUntil(15000){ui.onAllNodesWithTag("email").fetchSemanticsNodes().isNotEmpty()}}finally{scenario?.close();
production.clear()}
   }
   else->error("Invalid phase")
  }
 }
}
