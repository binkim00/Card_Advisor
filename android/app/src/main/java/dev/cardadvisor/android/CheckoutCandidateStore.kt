package dev.cardadvisor.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Retains closed fields only. Any observed amount/route/version change needs confirmation again. */
internal class CheckoutCandidateStore(private val packages:Map<String,String>,private val clock:()->Long){
 private val state=MutableStateFlow<DetectedCheckout?>(null)
 val pending:StateFlow<DetectedCheckout?> = state
 @Volatile var sessionActive=false;private set
 private var generation=0L
 private var lastObservation:CheckoutObservation?=null
 @Synchronized fun authorize(active:Boolean){if(sessionActive==active)return;sessionActive=active;clear()}
 @Synchronized fun clear(){generation++;state.value=null;lastObservation=null}
 @Synchronized fun accept(observation:CheckoutObservation){
  if(!sessionActive||observation.packageName !in packages||!observation.checkoutTitle||observation.authentication){clear();return}
  val snapshot=observation.copy(amounts=observation.amounts.mapValues{it.value.toSet()})
  val amount=snapshot.amounts[DetectionAmount.FINAL_TOTAL].orEmpty().singleOrNull()?.takeIf{it in 1..10000000}
  val now=clock();val old=state.value
  if(old!=null&&snapshot==lastObservation&&now-old.observedAt in 0 until 120000){
   state.value=old.copy(observedAt=now);return
  }
  lastObservation=snapshot
  state.value=DetectedCheckout(++generation,packages.getValue(snapshot.packageName),snapshot.appVersion,amount,if(amount==null)"HELD" else "NEEDS_CONFIRMATION",if(amount==null)"AMBIGUOUS_OR_MISSING_TOTAL" else "UNVERIFIED_ROUTE_CONFIRM_REQUIRED",now)
 }
 @Synchronized fun current():DetectedCheckout?{
  val candidate=state.value?:return null
  if(clock()-candidate.observedAt !in 0 until 120000){clear();return null}
  return candidate
 }
}
