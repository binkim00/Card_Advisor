package dev.cardadvisor.android
import org.json.*
import java.util.UUID

internal enum class FinanceState(val label:String){
 CONNECTING("연결 처리 중"),ACTIVE("연결됨"),REAUTHENTICATING("재인증 처리 중"),DISCONNECTING("해제 처리 중"),
 AUTH_REQUIRED("추가 인증 필요"),UNKNOWN("결과 확인 필요 · 보류"),FAILED("연결 실패"),DISCONNECTED("해제됨")
}
internal data class FinanceConnection(val id:String,val organization:String,val state:FinanceState,val version:Long,val hasId:Boolean,val operationId:String?,val environment:String,val error:String?=null,val providerCode:String?=null){
 val canRetry get()=!hasId&&state==FinanceState.FAILED
 val canChange get()=hasId&&(state==FinanceState.ACTIVE||state==FinanceState.AUTH_REQUIRED)
 val held get()=state in setOf(FinanceState.CONNECTING,FinanceState.REAUTHENTICATING,FinanceState.DISCONNECTING,FinanceState.UNKNOWN)||state==FinanceState.AUTH_REQUIRED&&!hasId
 companion object{fun parse(j:JSONObject):FinanceConnection{
  val id=j.getString("id");UUID.fromString(id)
  val org=j.getString("organization");require(org.matches(Regex("[0-9]{4}")))
  val v=j.getLong("version");require(v>=0)
  val op=if(j.isNull("last_operation_id"))null else j.getString("last_operation_id").also{UUID.fromString(it)}
  val env=j.getString("environment");require(env in listOf("SANDBOX","DEMO","PRODUCTION"))
  return FinanceConnection(id,org,FinanceState.valueOf(j.getString("state")),v,j.getBoolean("hasConnectedId"),op,env,if(j.isNull("last_error"))null else j.getString("last_error"),j.optString("provider_code").takeIf{it.matches(Regex("CF-[0-9]{5}"))})
 }}
}
internal fun financeConnections(raw:String):List<FinanceConnection>{val a=JSONArray(raw);return (0 until a.length()).map{FinanceConnection.parse(a.getJSONObject(it))}}
internal data class FinanceReadiness(val ready:Boolean,val synthetic:Boolean,val environment:String){
 companion object{fun parse(raw:String):FinanceReadiness{val j=JSONObject(raw);return FinanceReadiness(j.getBoolean("userConnectionApiReady"),j.optBoolean("syntheticProvider",false),j.getString("environment"))}}
}
internal fun financeOperation(raw:JSONObject):String{
 val status=raw.getString("status");require(status in listOf("PENDING","SUCCEEDED","FAILED","UNKNOWN","AUTH_REQUIRED"));UUID.fromString(raw.getString("id"));return status
}
internal fun financeOutcome(status:String)=when(status){
 "SUCCEEDED"->"요청 처리가 확인되었습니다. 최신 연결 상태를 확인해 주세요."
 "FAILED"->"요청이 실패했습니다. 연결 상태를 확인한 뒤 다시 입력해 주세요."
 "AUTH_REQUIRED"->"추가 인증이 필요합니다. 현재 화면은 아이디·비밀번호 갱신만 지원합니다. 인증 이어하기는 아직 지원하지 않습니다."
 else->"처리 결과가 확정되지 않아 보류했습니다. 재전송하지 말고 연결 상태를 조회해 주세요."
}
