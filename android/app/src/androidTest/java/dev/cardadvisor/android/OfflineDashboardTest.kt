package dev.cardadvisor.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID
import java.io.File
@RunWith(AndroidJUnit4::class)
class OfflineDashboardTest{
 @Test fun encryptedAccountBoundOfflineReadAndRecovery()=runBlocking{
  val run=InstrumentationRegistry.getArguments().getString("runId")!!;val context=InstrumentationRegistry.getInstrumentation().targetContext
  val session=SecureSessionStore(context,"offline-session-$run");val cache=SecureDashboardStore(context,"offline-cache-$run");var api=ApiClient("http://127.0.0.1:18080",session,cache);val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
  try{
   api.setSession(JSONObject(api.request("/v1/auth/register","POST",JSONObject().put("email","offline-$run@example.test").put("password","Synthetic-password-$run"))));api.request("/v1/demo/setup","POST")
   // Simulate an installed version whose encrypted session predates owner metadata.
   session.save(SavedSession(api.token!!,api.refreshToken!!))
   api=ApiClient(api.baseUrl,session,cache);Assert.assertTrue(api.restoreSession())
   val first=api.dashboard(month);Assert.assertEquals(first.getString("owner"),session.load()!!.owner);Assert.assertFalse(api.offline.value)
   val owner=first.getString("owner");Assert.assertNull(cache.load(UUID.randomUUID().toString(),api.baseUrl,month));Assert.assertNull(cache.load(owner,"https://different.example.test",month));Assert.assertNull(cache.load(owner,api.baseUrl,YearMonth.parse(month).minusMonths(1).toString()))
   val blob=File(context.noBackupFilesDir,"offline-cache-$run.enc").readBytes();Assert.assertFalse(String(blob,Charsets.ISO_8859_1).contains(owner))
   val blocked=JSONObject(api.request("/v1/test/network","POST",JSONObject().put("offline",true)))
   val cached=api.dashboard(month);Assert.assertTrue(api.offline.value);Assert.assertEquals(first.getLong("fetchedAt"),cached.getLong("fetchedAt"))
   try{api.request("/v1/demo/setup","POST");Assert.fail("Offline mutation must be blocked")}catch(e:ApiException){Assert.assertEquals("OFFLINE_READ_ONLY",e.code)}
   // The local test switch has no production endpoint; use a separate transport to restore it.
   ApiClient(api.baseUrl).request("/v1/test/network","POST",JSONObject().put("offline",false))
   api.dashboard(month);Assert.assertFalse(api.offline.value)
   api.clearSession();Assert.assertNull(cache.load(owner,api.baseUrl,month));Assert.assertFalse(ApiClient(sessionStore=session).restoreSession())
   cache.save(first);Assert.assertNull(cache.load(owner,api.baseUrl,month,first.getLong("fetchedAt")+8*24*60*60*1000L))
  }finally{api.clearSession();cache.clear()}
 }
}
