package dev.cardadvisor.android

import org.json.*
import java.time.LocalDate
import java.util.UUID

private fun JSONObject.uuid(name:String)=getString(name).also{UUID.fromString(it)}
private fun JSONObject.nullString(name:String):String? { require(has(name));return if(isNull(name))null else getString(name) }
private fun JSONObject.exactLong(name:String):Long { val v=get(name);require(v is Number && v.toString().matches(Regex("-?[0-9]+")));return v.toString().toLong() }
private fun JSONObject.nullLong(name:String):Long? { require(has(name));return if(isNull(name))null else exactLong(name) }
internal fun <T> syncList(raw:String,parse:(JSONObject)->T):List<T>{val a=JSONArray(raw);return (0 until a.length()).map{parse(a.getJSONObject(it))}}
internal data class SyncRun(val id:String,val connection:String,val from:String,val to:String,val status:String,val received:Long,val applied:Long,val held:Long){
 companion object{fun parse(j:JSONObject):SyncRun {
  val s=j.getString("status");require(s in listOf("RUNNING","IMPORTED","PARTIAL","FAILED"))
  val f=j.getString("from_date");val t=j.getString("to_date");require(!LocalDate.parse(f).isAfter(LocalDate.parse(t)))
  val received=j.exactLong("received");val applied=j.exactLong("applied");val held=j.exactLong("held");require(received>=0&&applied>=0&&held>=0&&applied+held<=received)
  return SyncRun(j.uuid("id"),j.uuid("connection_id"),f,t,s,received,applied,held)
 }}
}
internal data class FinancialRow(val id:String,val connection:String,val date:String?,val card:String?,val kind:String,val amount:Long?,val refund:Long?,val currency:String?,val payment:String?,val installments:Long?,val status:String,val issue:String?,val version:Long,val ledger:String?,val original:String?,val source:String,val merchant:String?=null,val cardLabel:String?=null,val mappedCardName:String?=null){
 val held get()=status=="HELD"
 val manualReview get()=held&&issue=="MANUAL_DUPLICATE_REVIEW"
 val cancelReview get()=held&&kind=="CANCEL_EVENT"&&issue=="CANCELLATION_MATCH_REQUIRED"
 val canIgnore get()=held&&ledger==null&&original==null
 companion object{fun parse(j:JSONObject):FinancialRow {
  val d=j.nullString("used_date")?.also{LocalDate.parse(it)}
  val c=j.nullString("card_mask")?.also{require(it.matches(Regex("(?:[0-9]{4}\\*{8}[0-9]{4}|\\*{13}[0-9]{3})")))}
  val kind=j.getString("kind");require(kind in listOf("APPROVAL","CANCEL_EVENT","REJECTED"))
  val status=j.getString("status");require(status in listOf("HELD","APPLIED","IGNORED","RECEIVED"))
  val source=j.getString("source");require(source in listOf("SYNTHETIC","PROVIDER_REPORTED"))
  val v=j.exactLong("version");require(v>0)
  val a=j.nullLong("amount_won");val r=j.nullLong("cancelled_won");val n=j.nullLong("installments")
  require(a==null||a in 0..100_000_000L);require(r==null||r in 0..100_000_000L);require(n==null||n>0)
  return FinancialRow(j.uuid("id"),j.uuid("connection_id"),d,c,kind,a,r,j.nullString("currency"),j.nullString("payment_type"),n,status,j.nullString("issue"),v,j.nullString("ledger_id")?.also{UUID.fromString(it)},j.nullString("linked_transaction_id")?.also{UUID.fromString(it)},source,j.optString("merchant_name").takeIf{it!="null"&&it.isNotBlank()&&it.length<=120},j.optString("card_label").takeIf{it!="null"&&it.isNotBlank()&&it.length<=120},j.optString("mapped_card_name").takeIf{it!="null"&&it.isNotBlank()&&it.length<=120})
 }}
}
internal data class ManualCandidate(val id:String,val date:String,val amount:Long,val net:Long,val category:String,val included:Boolean,val cardName:String?){
 companion object{fun from(raw:String,row:FinancialRow,rows:List<FinancialRow>):List<ManualCandidate> = syncList(raw){j->
  val id=j.uuid("id");val d=j.getString("purchased_at");LocalDate.parse(d)
  val amount=j.exactLong("amount_won");val net=j.exactLong("net_won");require(amount>=0&&net in 0..amount)
  val charge=j.nullLong("card_charge_won")
  if(j.getString("source")!="MANUAL"||d!=row.date||amount!=row.amount||charge!=null&&charge!=amount||rows.any{it.ledger==id})null
  else ManualCandidate(id,d,amount,net,j.getString("category"),j.getBoolean("included"),j.nullString("card_name"))
 }.filterNotNull()}
}
internal fun syncMoney(value:Long?)=value?.let{"%,d원".format(it)}?:"미확인"
internal fun syncIssue(code:String?)=when(code){
 "MANUAL_DUPLICATE_REVIEW"->"같은 날짜·금액의 수동 구매가 있습니다. 같은 거래인지 확인해 주세요."
 "CANCELLATION_MATCH_REQUIRED"->"어떤 결제를 취소한 것인지 선택해 주세요. 확인 전에는 환불을 가계부에 적용하지 않습니다."
 "REFUND_REGRESSION"->"취소 누적액이 이전보다 줄어 대조가 필요합니다."
 "REFUND_CONFLICT"->"취소 금액 또는 취소 방식이 기존 기록과 충돌합니다."
 "APPROVAL_AMOUNT_CHANGED"->"승인 금액이 변경되어 대조가 필요합니다."
 "REJECTED_APPROVAL"->"승인이 거절되어 지출에 반영하지 않았습니다."
 else->"금액·환불·통화 또는 거래 식별 정보가 미확인입니다. 0원으로 계산하지 않습니다."
}
internal fun syncOutcome(status:String)=when(status){
 "IMPORTED"->"이 기간에 조회된 이용내역을 가계부에 기록했습니다."
 "PARTIAL"->"이용내역을 불러왔습니다. 확인이 필요한 내역이 있습니다."
 "FAILED"->"동기화가 실패했습니다. 저장된 결과를 확인한 뒤 다시 요청해 주세요."
 else->"동기화 처리 중입니다. 새 요청 대신 저장된 결과를 조회해 주세요."
}
