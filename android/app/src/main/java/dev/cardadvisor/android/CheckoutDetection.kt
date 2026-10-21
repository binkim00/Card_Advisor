package dev.cardadvisor.android

import android.os.SystemClock

internal enum class DetectionAmount { FINAL_TOTAL, ORDER, CARD, POINTS, MONEY }
internal data class CheckoutObservation(val packageName:String,val appVersion:String,val checkoutTitle:Boolean,val authentication:Boolean,val amounts:Map<DetectionAmount,Set<Long>>)
internal data class DetectedCheckout(val generation:Long,val merchant:String,val appVersion:String,val amountWon:Long?,val state:String,val reason:String,val observedAt:Long,val orderValueWon:Long?=null,val pointUsedWon:Long?=null,val prepaidMoneyUsedWon:Long?=null)

/** Memory only; the clock is injected into the store for deterministic expiry tests. */
internal object CheckoutDetection {
 val packages=mapOf("com.navercorp.navershopping" to "NAVER_PLUS_STORE","com.musinsa.store" to "MUSINSA")
 private val store=CheckoutCandidateStore(packages){SystemClock.elapsedRealtime()}
 val pending=store.pending
 val sessionActive get()=store.sessionActive
 fun authorize(active:Boolean)=store.authorize(active)
 fun clear()=store.clear()
 fun accept(observation:CheckoutObservation)=store.accept(observation)
 fun current()=store.current()
}
