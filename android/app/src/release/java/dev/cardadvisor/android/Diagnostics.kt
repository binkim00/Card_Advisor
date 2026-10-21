package dev.cardadvisor.android

/** Release variant has no recorder, application hook, persistence, or exception handler. */
internal object Diagnostics {
 fun event(event:DiagnosticEvent) = Unit
 fun requestTrace(id:Long,route:HttpTraceRoute,phase:HttpTracePhase,elapsedMs:Long,status:Int=0) = Unit
 fun failure(site:DiagnosticSite,error:Throwable) = Unit
}
