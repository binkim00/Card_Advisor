package dev.cardadvisor.android

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Validate the complete snapshot before publishing any of it to Compose. No zero defaults. */
internal fun validateFixedCostResponse(view:JSONObject,entries:JSONArray,budget:JSONObject?,month:String){
 fun invalid():Nothing=throw JSONException("Invalid fixed-cost response")
 fun text(o:JSONObject,k:String):String=(o.get(k) as? String)?.takeIf{it.isNotBlank()}?:invalid()
 fun integer(o:JSONObject,k:String,min:Long=0,max:Long=Long.MAX_VALUE):Long{
  val raw=o.get(k)
  val n=when(raw){is Int->raw.toLong();is Long->raw;else->invalid()}
  if(n !in min..max)invalid()
  return n
 }
 fun optionalMoney(o:JSONObject,k:String):Long?{
  if(!o.has(k))invalid()
  return if(o.isNull(k))null else integer(o,k)
 }
 fun flag(o:JSONObject,k:String){if(o.get(k) !is Boolean)invalid()}
 fun id(o:JSONObject,k:String){if(runCatching{UUID.fromString(text(o,k))}.isFailure)invalid()}
 fun date(o:JSONObject,k:String){if(runCatching{LocalDate.parse(text(o,k))}.isFailure)invalid()}

 if(text(view,"month")!=month)invalid()
 val mode=text(view,"mode");if(mode !in setOf("UNSET","MANUAL","SCHEDULED"))invalid()
 integer(view,"scheduledRemainingWon")
 val manual=optionalMoney(view,"manualRemainingWon");val applied=optionalMoney(view,"appliedRemainingWon")
 if(mode=="UNSET"){if(budget!=null||manual!=null||applied!=null)invalid()}
 else if(budget==null||manual==null||applied==null)invalid()
 val items=view.getJSONArray("items")
 for(i in 0 until items.length()){
  val item=items.getJSONObject(i)
  id(item,"id");integer(item,"version",1);text(item,"name");text(item,"category")
  integer(item,"amountWon",1,100_000_000);integer(item,"dayOfMonth",1,31);flag(item,"active");date(item,"dueDate")
  integer(item,"includedNetWon");integer(item,"remainingWon")
  val payments=item.getJSONArray("payments")
  for(j in 0 until payments.length()){
   val payment=payments.getJSONObject(j);id(payment,"transactionId");integer(payment,"netWon");flag(payment,"included")
  }
 }
 for(i in 0 until entries.length()){
  val entry=entries.getJSONObject(i);id(entry,"id");date(entry,"purchased_at");text(entry,"category")
  integer(entry,"net_won");flag(entry,"included")
 }
 budget?.let{
  integer(it,"version");integer(it,"totalWon",0,100_000_000);integer(it,"fixedRemainingWon",0,100_000_000)
  val categories=it.getJSONObject("categories")
  for(key in categories.keys())integer(categories,key,0,100_000_000)
 }
}
