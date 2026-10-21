package dev.cardadvisor.android
import org.json.*
import java.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
class ApiException(val status:Int,val code:String,message:String):IllegalStateException(message)
class ApiClient(var baseUrl: String = BuildConfig.API_BASE_URL,private val sessionStore:SecureSessionStore?=null) {
 companion object{ @Volatile private var persistentClient:ApiClient?=null; fun persistent(context:android.content.Context):ApiClient=persistentClient?:synchronized(this){persistentClient?:ApiClient(sessionStore=SecureSessionStore(context.applicationContext)).also{persistentClient=it}} }
 private val refreshLock=Mutex()
 val sessionActive=MutableStateFlow(false)
 private val traceIds=java.util.concurrent.atomic.AtomicLong()
 @Volatile var token: String?=null
 @Volatile var refreshToken:String?=null
 suspend fun restoreSession():Boolean=refreshLock.withLock{val saved=sessionStore?.load()?:return@withLock false;token=saved.access;refreshToken=saved.refresh;sessionActive.value=true;true}
 suspend fun setSession(value:JSONObject){val access=value.getString("accessToken");val refresh=value.optString("refreshToken").takeIf{it.isNotBlank()};require(access.length in 16..256);if(sessionStore!=null){require(refresh!=null);sessionStore.save(SavedSession(access,refresh))};token=access;refreshToken=refresh;sessionActive.value=true}
 suspend fun clearSession(){token=null;refreshToken=null;sessionActive.value=false;sessionStore?.clear()}
 private suspend fun rotate(){val refresh=refreshToken?:throw ApiException(401,"SESSION_EXPIRED","다시 로그인해 주세요.");try{setSession(JSONObject(sendOnce("/v1/auth/refresh","POST",JSONObject().put("refreshToken",refresh))))}catch(e:ApiException){if(e.status==401)clearSession();throw e}}
 suspend fun renewSession(){refreshLock.withLock{withContext(kotlinx.coroutines.NonCancellable){rotate()}}}
 suspend fun request(path:String,method:String="GET",body:JSONObject?=null,idempotencyKey:String?=null):String{
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
  } catch(e:java.io.IOException){Diagnostics.failure(DiagnosticSite.HTTP_IO,e);throw IllegalStateException("서버에 연결할 수 없습니다. 로컬 서버와 USB 연결을 확인해 주세요.",e)}
  finally{c.disconnect();trace(HttpTracePhase.IO_END)}
 }}finally{trace(HttpTracePhase.RESUMED)}
 }
}
