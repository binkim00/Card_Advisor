package dev.cardadvisor.android

import android.content.Context
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.IdentityHashMap
import java.util.UUID

internal object Diagnostics {
 @Volatile private var store:DiagnosticStore?=null
 fun initialize(context:Context){
  runCatching{store=DiagnosticStore(File(context.noBackupFilesDir,"debug-diagnostics"));event(DiagnosticEvent.PROCESS_START)}
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler{thread,error->
   // Disk/serialization failures must never replace the original exception or prevent Android handling it.
   runCatching{store?.failure(DiagnosticSite.UNCAUGHT,error,true)}
   if(previous!=null)previous.uncaughtException(thread,error)
   else {Process.killProcess(Process.myPid());kotlin.system.exitProcess(10)}
  }
 }
 fun event(event:DiagnosticEvent){runCatching{store?.event(event)}}
 fun failure(site:DiagnosticSite,error:Throwable){runCatching{store?.failure(site,error,false)}}
 fun requestTrace(id:Long,route:HttpTraceRoute,phase:HttpTracePhase,elapsedMs:Long,status:Int=0){runCatching{store?.requestTrace(id,route,phase,elapsedMs,status)}}
 internal fun requestTraceSnapshot():String=store?.requestTraceSnapshot()?:""
 internal fun sessionDirectory():File?=store?.directory
}

internal class DiagnosticStore(root:File){
 companion object { const val MAX_EVENT_BYTES=65536L;const val MAX_SESSIONS=5;const val MAX_FATAL_SESSIONS=3 }
 val directory:File
 private var sequence=0L
 private val traceBuffer=java.util.ArrayDeque<String>()
 init{
  check(root.mkdirs()||root.isDirectory)
  directory=File(root,"session-${System.currentTimeMillis()}-${Process.myPid()}-${UUID.randomUUID()}")
  check(directory.mkdir())
  val sessions=root.listFiles()?.filter{it.isDirectory&&Regex("session-[0-9]+-[0-9]+-[a-f0-9-]{36}").matches(it.name)}
   ?.sortedByDescending{it.name}.orEmpty()
  // Successful launches must not evict the most recent terminal failures.
  val keep=(sessions.take(MAX_SESSIONS)+sessions.filter{File(it,"first-uncaught.json").exists()}.take(MAX_FATAL_SESSIONS)).toSet()
  sessions.filter{it !in keep}.forEach{old->
    // Only the small, known files owned by this recorder can be removed.
    old.listFiles()?.filter{it.isFile&&it.name in setOf("events.jsonl","events.previous.jsonl","first-exception.json","first-exception.json.bak","first-exception.json.new","first-uncaught.json","first-uncaught.json.bak","first-uncaught.json.new")}?.forEach{it.delete()}
    old.delete()
   }
 }
 private fun base(event:String)=JSONObject().put("schema",1).put("sequence",++sequence)
  .put("elapsedMs",SystemClock.elapsedRealtime()).put("pid",Process.myPid())
  .put("thread",if(Looper.myLooper()==Looper.getMainLooper())"MAIN" else "OTHER").put("event",event)
 @Synchronized fun event(event:DiagnosticEvent){append(base(event.name))}
 @Synchronized fun requestTrace(id:Long,route:HttpTraceRoute,phase:HttpTracePhase,durationMs:Long,status:Int){
  append(base("HTTP").put("request",id).put("route",route.name).put("phase",phase.name).put("durationMs",durationMs).put("status",status))
 }
 @Synchronized fun failure(site:DiagnosticSite,error:Throwable,fatal:Boolean){
  val record=base("EXCEPTION").put("site",site.name).put("fatal",fatal).put("exceptions",redact(error))
  pin("first-exception.json",record)
  if(fatal)pin("first-uncaught.json",record)
  append(record)
 }
 private fun pin(name:String,record:JSONObject){
  val file=File(directory,name)
  if(file.exists())return
  val atomic=AtomicFile(file);var stream:FileOutputStream?=null
  try{stream=atomic.startWrite();stream.write(record.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(stream)}
  catch(t:Throwable){if(stream!=null)atomic.failWrite(stream);throw t}
 }
 @Synchronized fun requestTraceSnapshot():String=traceBuffer.joinToString("\n",postfix="\n")
 private fun append(record:JSONObject){
  traceBuffer.addLast(record.toString());while(traceBuffer.size>1024)traceBuffer.removeFirst()
  // Timing probes must not serialize the UI and IO workers by syncing disk on every request phase.
  if(record.optString("event") in setOf("HTTP","APP_TASK_ENQUEUED","APP_TASK_BEGIN","APP_TASK_END","SYNC_TASK_ENQUEUED","SYNC_TASK_BEGIN","SYNC_TASK_END"))return

  val file=File(directory,"events.jsonl");val bytes=(record.toString()+"\n").toByteArray(Charsets.UTF_8)
  check(bytes.size<=MAX_EVENT_BYTES)
  if(file.length()+bytes.size>MAX_EVENT_BYTES){
   val previous=File(directory,"events.previous.jsonl")
   check(!previous.exists()||previous.delete());check(file.renameTo(previous))
  }
  FileOutputStream(file,true).use{it.write(bytes);it.fd.sync()}
 }
 private fun redact(error:Throwable):JSONArray{
  val output=JSONArray();val seen=IdentityHashMap<Throwable,Boolean>()
  fun symbol(value:String,method:Boolean=false):String{
   val pattern=if(method)"[A-Za-z0-9_$<>-]{1,96}" else "[A-Za-z0-9_.$]{1,200}"
   return if(Regex(pattern).matches(value))value else "unavailable"
  }
  fun visit(t:Throwable,relation:String){
   if(output.length()>=8||seen.put(t,true)!=null)return
   val frames=JSONArray()
   t.stackTrace.take(16).forEach{frame->
    // StackTraceElement can be forged. Only keep class/method names found in loaded code.
    val owner=runCatching{Class.forName(frame.className,false,javaClass.classLoader)}.getOrNull()
    val method=if(owner!=null&&runCatching{frame.methodName in setOf("<init>","<clinit>")||owner.declaredMethods.any{it.name==frame.methodName}}.getOrDefault(false))symbol(frame.methodName,true) else "unavailable"
    frames.put(JSONObject().put("class",if(owner==null)"unavailable" else symbol(owner.name)).put("method",method).put("line",frame.lineNumber))
   }
   // Never call Throwable.toString/message/localizedMessage, or serialize a StackTraceElement.
   output.put(JSONObject().put("relation",relation).put("type",symbol(t.javaClass.name)).put("frames",frames))
   t.cause?.let{visit(it,"cause")};t.suppressed.take(4).forEach{visit(it,"suppressed")}
  }
  visit(error,"root");return output
 }
}
