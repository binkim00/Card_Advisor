package dev.cardadvisor.android

import android.app.Activity
import android.app.Application
import android.os.Bundle

class DebugDiagnosticsApplication:Application(){
 override fun onCreate(){
  super.onCreate();Diagnostics.initialize(this)
  registerActivityLifecycleCallbacks(object:ActivityLifecycleCallbacks{
   private fun event(activity:Activity,event:DiagnosticEvent){if(activity is MainActivity)Diagnostics.event(event)}
   override fun onActivityCreated(activity:Activity,state:Bundle?)=event(activity,DiagnosticEvent.ACTIVITY_CREATED)
   override fun onActivityStarted(activity:Activity)=event(activity,DiagnosticEvent.ACTIVITY_STARTED)
   override fun onActivityResumed(activity:Activity)=event(activity,DiagnosticEvent.ACTIVITY_RESUMED)
   override fun onActivityPaused(activity:Activity)=event(activity,DiagnosticEvent.ACTIVITY_PAUSED)
   override fun onActivityStopped(activity:Activity)=event(activity,DiagnosticEvent.ACTIVITY_STOPPED)
   override fun onActivityPreDestroyed(activity:Activity)=event(activity,DiagnosticEvent.ACTIVITY_DESTROYING)
   override fun onActivityDestroyed(activity:Activity)=event(activity,DiagnosticEvent.ACTIVITY_DESTROYED)
   override fun onActivitySaveInstanceState(activity:Activity,state:Bundle)=Unit
  })
 }
}
