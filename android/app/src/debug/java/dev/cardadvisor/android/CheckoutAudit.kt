package dev.cardadvisor.android
import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
/** Debug-only closed metadata. Never stores amount values, screen text, node dumps or identities. */
internal object CheckoutAudit {
 private var last=0L
 fun record(context:Context,pkg:String,version:String,state:String,checkout:Boolean,authentication:Boolean,counts:Map<DetectionAmount,Int>,labels:Set<DetectionAmount>,visited:Int){
  val now=android.os.SystemClock.elapsedRealtime();if(now-last<1000&&!state.startsWith("STABLE_"))return;last=now
  val values=JSONObject();counts.forEach{(key,value)->values.put(key.name,value)}
  val data=JSONObject().put("package",pkg).put("appVersion",version).put("state",state).put("checkoutTitle",checkout).put("authenticationScreen",authentication).put("candidateCounts",values).put("recognizedLabels",org.json.JSONArray(labels.map{it.name})).put("visitedCount",visited).put("recordedAt",System.currentTimeMillis())
  val file=AtomicFile(java.io.File(context.noBackupFilesDir,"checkout-route-audit.json"));var output:java.io.FileOutputStream?=null
  try{output=file.startWrite();output.write(data.toString().toByteArray(Charsets.UTF_8));file.finishWrite(output)}catch(e:Exception){output?.let{file.failWrite(it)}}
 }
 fun confirmation(context:android.content.Context,visible:Boolean,valid:Boolean){
  val file=android.util.AtomicFile(java.io.File(context.noBackupFilesDir,"checkout-confirmation-ui.json"));var output:java.io.FileOutputStream?=null
  try{output=file.startWrite();output.write(org.json.JSONObject().put("confirmationFormVisible",visible).put("candidateUnexpired",valid).put("requiresUserConfirmation",true).toString().toByteArray(Charsets.UTF_8));file.finishWrite(output)}catch(e:Exception){output?.let{file.failWrite(it)}}
 }
}
