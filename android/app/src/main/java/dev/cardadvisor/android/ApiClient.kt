package dev.cardadvisor.android
import org.json.*
import java.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
class ApiException(val status:Int,val code:String,message:String):IllegalStateException(message)
class ApiClient(var baseUrl: String = BuildConfig.API_BASE_URL,private val sessionStore:SecureSessionStore?=null,private val dashboardStore:SecureDashboardStore?=null) {
 companion object{ @Volatile private var persistentClient:ApiClient?=null; fun persistent(context:android.content.Context):ApiClient=persistentClient?:synchronized(this){persistentClient?:ApiClient(sessionStore=SecureSessionStore(context.applicationContext),dashboardStore=SecureDashboardStore(context.applicationContext)).also{persistentClient=it}} }
 private val refreshLock=Mutex()
 private val cacheLock=Mutex()
 @Volatile private var owner:String?=null
 val offline=MutableStateFlow(false)
 val lastUpdated=MutableStateFlow<Long?>(null)
 private val retryUntil=java.util.concurrent.atomic.AtomicLong()
 suspend fun dashboard(month:String):JSONObject{
  val expectedOwner=owner;val expectedBase=baseUrl;val expectedToken=token
  try{
   val response=JSONObject(request("/v1/dashboard?month=$month"))
   val ledger=response.getJSONArray("ledger");val performance=response.getJSONArray("performance");val budget=if(response.isNull("budget"))JSONObject.NULL else response.getJSONObject("budget")
   if(owner!=expectedOwner||baseUrl!=expectedBase||token==null)throw ApiException(401,"SESSION_EXPIRED","다시 로그인해 주세요.")
   val verifiedOwner=response.getString("userId")
   java.util.UUID.fromString(verifiedOwner)
   if(expectedOwner!=null&&verifiedOwner!=expectedOwner)throw ApiException(401,"SESSION_EXPIRED","다시 로그인해 주세요.")
   // Upgrade older encrypted sessions only after an authenticated, same-session response.
   if(expectedOwner==null)refreshLock.withLock{cacheLock.withLock{
    if(token!=expectedToken||baseUrl!=expectedBase||owner!=null)throw ApiException(401,"SESSION_EXPIRED","다시 로그인해 주세요.")
    val refresh=refreshToken?:throw ApiException(401,"SESSION_EXPIRED","다시 로그인해 주세요.")
    sessionStore?.save(SavedSession(expectedToken!!,refresh,verifiedOwner));owner=verifiedOwner
   }}
   val value=JSONObject().put("owner",verifiedOwner).put("base",expectedBase).put("month",month).put("fetchedAt",System.currentTimeMillis()).put("ledger",ledger).put("performance",performance).put("budget",budget)
   cacheLock.withLock{if(owner==verifiedOwner&&baseUrl==expectedBase&&token!=null){dashboardStore?.save(value);offline.value=false;lastUpdated.value=value.getLong("fetchedAt")}}
   return value
  }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){
   // Authentication/validation errors never fall back to another or expired account's data.
   val unavailable=e is ApiException&&(e.status>=500||e.status==429)||e.cause is java.io.IOException
   if(!unavailable)throw e
   val cached=cacheLock.withLock{if(expectedOwner!=null&&owner==expectedOwner&&token!=null&&baseUrl==expectedBase)dashboardStore?.load(expectedOwner,expectedBase,month) else null}
   offline.value=true
   if(cached==null){lastUpdated.value=null;throw e};lastUpdated.value=cached.getLong("fetchedAt");return cached
  }
 }

 val sessionActive=MutableStateFlow(false)
 private val traceIds=java.util.concurrent.atomic.AtomicLong()
 @Volatile var token: String?=null
 @Volatile var refreshToken:String?=null
 suspend fun restoreSession():Boolean=refreshLock.withLock{val saved=sessionStore?.load()?:return@withLock false;token=saved.access;refreshToken=saved.refresh;owner=saved.owner;sessionActive.value=true;true}
 suspend fun setSession(value:JSONObject){val nextOwner=value.optString("userId").takeIf{it.isNotBlank()};if(nextOwner!=owner){cacheLock.withLock{dashboardStore?.clear()};offline.value=false;lastUpdated.value=null};owner=nextOwner;val access=value.getString("accessToken");val refresh=value.optString("refreshToken").takeIf{it.isNotBlank()};require(access.length in 16..256);if(sessionStore!=null){require(refresh!=null);sessionStore.save(SavedSession(access,refresh,owner))};token=access;refreshToken=refresh;sessionActive.value=true}
 suspend fun clearSession(){token=null;refreshToken=null;owner=null;sessionActive.value=false;offline.value=false;lastUpdated.value=null;sessionStore?.clear();cacheLock.withLock{dashboardStore?.clear()}}
 private suspend fun rotate(){val refresh=refreshToken?:throw ApiException(401,"SESSION_EXPIRED","다시 로그인해 주세요.");try{setSession(JSONObject(sendOnce("/v1/auth/refresh","POST",JSONObject().put("refreshToken",refresh))))}catch(e:ApiException){if(e.status==401)clearSession();throw e}}
 suspend fun renewSession(){refreshLock.withLock{withContext(kotlinx.coroutines.NonCancellable){rotate()}}}
 suspend fun request(path:String,method:String="GET",body:JSONObject?=null,idempotencyKey:String?=null):String{
  if(offline.value&&method!="GET"&&!path.startsWith("/v1/auth/"))throw ApiException(503,"OFFLINE_READ_ONLY","오프라인에서는 최근 내역만 볼 수 있습니다. 새로고침으로 연결한 뒤 저장·추천을 진행해 주세요.")
  if(android.os.SystemClock.elapsedRealtime()<retryUntil.get())throw ApiException(429,"RATE_LIMITED","요청 제한 시간이 지나면 다시 시도해 주세요.")
  val used=token
  try{return sendOnce(path,method,body,idempotencyKey)}catch(e:ApiException){
   if(e.status!=401||e.code!="SESSION_EXPIRED"||path.startsWith("/v1/auth/"))throw e
   refreshLock.withLock{if(token==used){if(refreshToken==null){clearSession();throw e};withContext(kotlinx.coroutines.NonCancellable){rotate()}}}
   try{return sendOnce(path,method,body,idempotencyKey)}catch(last:ApiException){if(last.status==401&&last.code=="SESSION_EXPIRED")clearSession();throw last}
  }
 }
 private suspend fun sendOnce(path:String,method:String="GET",body:JSONObject?=null,idempotencyKey:String?=null):String {
  val id=traceIds.incrementAndGet();val started=android.os.SystemClock.elapsedRealtime()
  val route=when {path.startsWith("/v1/auth/")->HttpTraceRoute.AUTH;path=="/v1/demo/setup"->HttpTraceRoute.DEMO;path.startsWith("/v1/cards/performance")->HttpTraceRoute.PERFORMANCE;path.endsWith("/cancellations")->HttpTraceRoute.CANCEL;path.startsWith("/v1/transactions")->HttpTraceRoute.LEDGER;path.startsWith("/v1/budgets")->HttpTraceRoute.BUDGET;path.startsWith("/v1/finance/")->HttpTraceRoute.FINANCE;else->HttpTraceRoute.OTHER}
  fun trace(phase:HttpTracePhase,status:Int=0)=Diagnostics.requestTrace(id,route,phase,android.os.SystemClock.elapsedRealtime()-started,status)
  try{return withContext(Dispatchers.IO) {
  trace(HttpTracePhase.BEGIN)
  val c=(URL(baseUrl+path).openConnection() as HttpURLConnection)
  try {
   c.requestMethod=method;c.connectTimeout=5000;c.readTimeout=10000
   c.setRequestProperty("Content-Type","application/json")
   idempotencyKey?.let{c.setRequestProperty("Idempotency-Key",it)}
   token?.let { c.setRequestProperty("Authorization","Bearer "+it) }
   if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
   val status=c.responseCode;trace(HttpTracePhase.RESPONSE,status)
   if(status==429){val seconds=c.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1,3600)?:30;retryUntil.set(android.os.SystemClock.elapsedRealtime()+seconds*1000)}
   val result=(if(status in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   trace(HttpTracePhase.BODY_READ,status)
   if(status !in 200..299) throw ApiException(status,runCatching { JSONObject(result).getJSONObject("error").getString("code") }.getOrDefault("REQUEST_FAILED"),when(status){
    401->"로그인이 만료되었거나 계정 정보가 맞지 않습니다."
    404->"아직 저장된 데이터가 없습니다."
    409->when(runCatching { JSONObject(result).getJSONObject("error").getString("code") }.getOrNull()){
     "FINANCIAL_CARD_CALCULATION_REVIEW_REQUIRED"->"실제 카드에 연결한 금융 거래의 실적·혜택 조건을 확인해야 합니다. 상품 연결만으로 추천을 확정하지 않습니다."
     "FINANCIAL_REVIEW_REQUIRED"->"확인이 필요한 금융 거래가 있습니다. 금융 연결 관리에서 보류 거래를 확인해 주세요."
     "PROVIDER_TRANSACTION_MANAGED"->"금융 내역에 연결된 거래입니다. 취소·연결은 금융 거래 화면에서 확인해 주세요."
     else->"내용이 변경되었거나 이미 등록되었습니다. 다시 확인해 주세요."
    }
    429->"요청이 많습니다. 잠시 후 다시 시도해 주세요."
    422->"금액, 계정 형식 또는 입력 조건을 확인해 주세요."
    else->"요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
   })
   result
  } catch(e:java.io.IOException){Diagnostics.failure(DiagnosticSite.HTTP_IO,e);throw IllegalStateException("서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 새로고침해 주세요.",e)}
  finally{c.disconnect();trace(HttpTracePhase.IO_END)}
 }}finally{trace(HttpTracePhase.RESUMED)}
 }
}
