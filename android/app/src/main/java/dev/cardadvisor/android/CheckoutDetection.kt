package dev.cardadvisor.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.os.SystemClock

internal enum class DetectionAmount { FINAL_TOTAL, CARD, POINTS, MONEY }
internal data class CheckoutObservation(val packageName:String,val appVersion:String,val checkoutTitle:Boolean,val authentication:Boolean,val amounts:Map<DetectionAmount,Set<Long>>)
internal data class DetectedCheckout(val generation:Long,val merchant:String,val appVersion:String,val amountWon:Long?,val state:String,val reason:String,val observedAt:Long)

/** Closed vocabulary + amounts only. No original node/text, address or login data enters this store. */
internal object CheckoutDetection {
 val packages=mapOf("com.navercorp.navershopping" to "NAVER_PLUS_STORE","com.musinsa.store" to "MUSINSA")
 private val state=MutableStateFlow<DetectedCheckout?>(null)
 val pending:StateFlow<DetectedCheckout?> = state
 @Volatile var sessionActive=false;private set
 private var generation=0L
 @Synchronized fun authorize(active:Boolean){if(sessionActive==active)return;sessionActive=active;clear()}
 @Synchronized fun clear(){generation++;state.value=null}
 @Synchronized fun accept(observation:CheckoutObservation){
  if(!sessionActive||observation.packageName !in packages||!observation.checkoutTitle||observation.authentication){clear();return}
  val values=observation.amounts[DetectionAmount.FINAL_TOTAL].orEmpty().filter{it in 1..10000000}.toSet()
  val amount=values.singleOrNull();val merchant=packages.getValue(observation.packageName)
  val old=state.value
  if(old!=null&&old.merchant==merchant&&old.appVersion==observation.appVersion&&old.amountWon==amount&&old.reason==(if(amount==null)"AMBIGUOUS_OR_MISSING_TOTAL" else "UNVERIFIED_ROUTE_CONFIRM_REQUIRED"))return
  state.value=DetectedCheckout(++generation,merchant,observation.appVersion,amount,if(amount==null)"HELD" else "NEEDS_CONFIRMATION",if(amount==null)"AMBIGUOUS_OR_MISSING_TOTAL" else "UNVERIFIED_ROUTE_CONFIRM_REQUIRED",SystemClock.elapsedRealtime())
 }
 fun current():DetectedCheckout?=state.value?.takeIf{SystemClock.elapsedRealtime()-it.observedAt<=120000}
}
