package dev.cardadvisor.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger

/** Instrumentation harness only; never packaged in the release variant. */
class FixedCostLifecycleActivity:ComponentActivity(){
 companion object {
  var client:ApiClient?=null
  val callbacksAfterRemoval=AtomicInteger()
  val busyCallbacks=AtomicInteger()
 }
 var showEditor by mutableStateOf(true)
 var busy by mutableStateOf(false)
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme { Surface { Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())){
   if(showEditor)FixedCostEditor(requireNotNull(client),onBusyChanged={value->
    busyCallbacks.incrementAndGet()
    if(!showEditor||isDestroyed)callbacksAfterRemoval.incrementAndGet()
    busy=value
   },onInvalidated={},onChanged={},onOpenBudget={showEditor=false})
   else Text("검사용 다른 화면")
  }}} }
 }
}
